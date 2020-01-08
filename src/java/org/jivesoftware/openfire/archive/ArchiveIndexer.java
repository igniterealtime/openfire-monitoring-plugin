/*
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.archive;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.dom4j.DocumentFactory;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.reporting.util.TaskEngine;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.XMLProperties;
import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Indexes archived conversations. If conversation archiving is not enabled,
 * this class does nothing. The search index is maintained in the <tt>monitoring/search</tt>
 * directory of the Openfire home directory. It's automatically updated with the latest
 * conversation content as long as conversation archiving is enabled. The index update
 * interval is controllec by the Jive property "conversation.search.updateInterval" and
 * the default value is 15 minutes.
 *
 * @see ArchiveSearcher
 * @author Matt Tucker
 */
public class ArchiveIndexer implements Startable {

    private static final Logger Log = LoggerFactory.getLogger(ArchiveIndexer.class);

    private static final String ALL_CONVERSATIONS =
            "SELECT conversationID, isExternal FROM ofConversation";
    private static final String NEW_CONVERSATIONS =
            "SELECT DISTINCT conversationID FROM ofMessageArchive WHERE sentDate > ?";
    private static final String CONVERSATION_METADATA =
            "SELECT isExternal FROM ofConversation WHERE conversationID=?";
    private static final String CONVERSATION_MESSAGES =
            "SELECT conversationID, sentDate, fromJID, toJID, body FROM ofMessageArchive " +
            "WHERE conversationID IN ? ORDER BY conversationID";

    private File searchDir;
    private TaskEngine taskEngine;
    private ConversationManager conversationManager;
    private XMLProperties indexProperties;
    private Directory directory;
    private IndexSearcher searcher;
    private boolean stopped = false;

    private boolean rebuildInProgress = false;
    private RebuildFuture rebuildFuture;

    private long lastModified = 0;

    private TimerTask indexUpdater;

    /**
     * Constructs a new archive indexer.
     *
     * @param conversationManager a ConversationManager instance.
     * @param taskEngine a task engine instance.
     */
    public ArchiveIndexer(ConversationManager conversationManager, TaskEngine taskEngine) {
        this.conversationManager = conversationManager;
        this.taskEngine = taskEngine;
    }

    public void start() {
        Log.debug( "Starting..." );
        searchDir = new File(JiveGlobals.getHomeDirectory() +
                    File.separator + MonitoringConstants.NAME + File.separator + "search");
        if (!searchDir.exists()) {
            if (!searchDir.mkdirs()) {
                Log.warn( "Lucene index directory '{}' does not exist, but cannot be created!", searchDir);
            }
        }
        boolean indexCreated = false;
        try {
            loadPropertiesFile(searchDir);
            directory = FSDirectory.open(searchDir.toPath());
            if (!DirectoryReader.indexExists(directory)) {
                // Create a new index.
                indexCreated = true;
            } else {
                // See if we can read the format.
                try {
                    // TODO make this optional through configuration.
                    Log.debug( "Checking Lucene index...");
                    boolean isClean;
                    try ( final CheckIndex check = new CheckIndex(directory) )
                    {
                        check.setChecksumsOnly(true);
                        check.setDoSlowChecks(false);
                        check.setFailFast(true);
                        isClean = check.checkIndex().clean;
                        Log.info( "Lucene index {} clean.", isClean ? "is" : "is not");
                    }
                    if ( !isClean ) {
                        Log.info( "Lucene index is not clean. Removing and rebuilding: {}", isClean);
                        directory.close();
                        FileUtils.deleteDirectory( searchDir );
                        if (!searchDir.mkdirs()) {
                            Log.warn( "Lucene index directory '{}' cannot be recreated!", searchDir);
                        }
                        directory = FSDirectory.open(searchDir.toPath());
                        indexCreated = true;
                    }
                } catch ( IndexFormatTooOldException ex ) {
                    Log.info( "Format of Lucene index is to old. Removing and rebuilding.", ex);
                    directory.close();
                    FileUtils.deleteDirectory( searchDir );
                    if (!searchDir.mkdirs()) {
                        Log.warn( "Lucene index directory '{}' cannot be recreated!", searchDir);
                    }
                    directory = FSDirectory.open(searchDir.toPath());
                    indexCreated = true;
                }
            }
        }
        catch (IOException ioe) {
            Log.error(ioe.getMessage(), ioe);
        }

        String modified = indexProperties.getProperty("lastModified");
        if (modified != null) {
            try {
                lastModified = Long.parseLong(modified);
            }
            catch (NumberFormatException nfe) {
                // Ignore.
            }
        }
        // If the index has never been updated, build it from scratch.
        if (lastModified == 0 || indexCreated) {
            taskEngine.submit(this::rebuildIndex);
        }

        indexUpdater = new TimerTask() {
            @Override
            public void run() {
                updateIndex();
            }
        };
        int updateInterval = JiveGlobals.getIntProperty("conversation.search.updateInterval", 15);
        taskEngine.scheduleAtFixedRate(indexUpdater, JiveConstants.MINUTE * 5, JiveConstants.MINUTE * updateInterval);
    }

    public void stop() {
        Log.debug( "Stopping..." );
        stopped = true;
        indexUpdater.cancel();
        if (searcher != null) {
            try {
                searcher.getIndexReader().close();
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
            searcher = null;
        }
        try {
            directory.close();
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        directory = null;
        indexProperties = null;
        conversationManager = null;
        searchDir = null;
        rebuildFuture = null;
    }

    /**
     * Returns the total size of the search index (in bytes).
     *
     * @return the total size of the search index (in bytes).
     */
    public long getIndexSize() {
        File [] files = searchDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                // Ignore the index properties file since it's not part of the index.
                return !name.equals("indexprops.xml");
            }
        });
        if (files == null) {
            // Search folder does not exist so size of index is 0
            return 0;
        }
        long size = 0;
        for (File file : files) {
            size += file.length();
        }
        return size;
    }

    /**
     * Updates the search index with all new conversation data since the last index update.
     */
    public void updateIndex() {
        // Immediately return if the service has been stopped.
        if (stopped) {
            return;
        }
        // Do nothing if archiving is disabled.
        if (!conversationManager.isArchivingEnabled()) {
            return;
        }
        // If we're currently rebuilding the index, return.
        if (rebuildInProgress) {
            return;
        }
        Log.debug("Updating the Lucene index...");
        final Instant start = Instant.now();

        List<Long> conversationIDs = new ArrayList<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(NEW_CONVERSATIONS);
            pstmt.setLong(1, lastModified);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                conversationIDs.add(rs.getLong(1));
            }
        }
        catch (SQLException sqle) {
            Log.error("An exception occurred while trying to fetch new/updated conversations from the database to update the Lucene index.", sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        Log.debug("... identified {} conversations.", conversationIDs.size());
        if (!conversationIDs.isEmpty()) {
            final Analyzer analyzer = new StandardAnalyzer();
            final IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

            // Load meta-data for each conversation.
            Log.debug("... loading meta-data for all to-be-updated conversations.");
            Map<Long, Boolean> externalMetaData = new HashMap<Long, Boolean>();
            for (long conversationID : conversationIDs) {
                try {
                    con = DbConnectionManager.getConnection();
                    pstmt = con.prepareStatement(CONVERSATION_METADATA);
                    pstmt.setLong(1, conversationID);
                    rs = pstmt.executeQuery();
                    while (rs.next()) {
                        externalMetaData.put(conversationID, rs.getInt(1) == 1);
                    }
                }
                catch (SQLException sqle) {
                    Log.error("An exception occurred while trying to load metadata for conversations to be updated in the Lucene index.", sqle);
                }
                finally {
                    DbConnectionManager.closeConnection(rs, pstmt, con);
                }
            }

            try ( final IndexWriter writer = new IndexWriter(directory, iwc) )
            {
                // Delete any conversations found -- they may have already been indexed, but updated since then.
                Log.debug("... deleting all to-be-updated conversations from the index.");
                for (long conversationID : conversationIDs) {
                    writer.deleteDocuments(new Term("conversationID", Long.toString(conversationID)));
                }

                // Now index all the new conversations.
                Log.debug("... started to index conversations to update the Lucene index.");
                long newestDate = indexConversations(conversationIDs, externalMetaData, writer, false);

                // Done indexing so store a last modified date.
                if (newestDate != -1) {
                    lastModified = newestDate;
                    indexProperties.setProperty("lastModified", Long.toString(lastModified));
                }
                final Duration duration = Duration.between( start, Instant.now() );
                Log.debug("Finished updating the Lucene index. Duration: " + duration);
            }
            catch (IOException ioe) {
                Log.error("An exception occurred while updating the Lucene index.", ioe);
            }
        }
    }

    /**
     * Rebuilds the search index with all archived conversation data. This method returns
     * a Future that represents the status of the index rebuild process (also available
     * via {@link #getIndexRebuildProgress()}). The integer value
     * (values 0 through 100) represents the percentage of work done. If message archiving
     * is disabled, this method will return <tt>null</tt>.
     *
     * @return a Future to indicate the status of rebuilding the index or <tt>null</tt> if
     *      rebuilding the index is not possible.
     */
    public synchronized Future<Integer> rebuildIndex() {
        // Immediately return if the service has been stopped.
        if (stopped) {
            return null;
        }

        // Do nothing if archiving is disabled.
        if (!conversationManager.isArchivingEnabled()) {
            return null;
        }

        // If a rebuild is already happening, return.
        if (rebuildInProgress) {
            return null;
        }

        rebuildInProgress = true;

        // Create a future to track the index rebuild progress.
        rebuildFuture = new RebuildFuture();

        // Create a runnable that will perform the actual rebuild work.
        Runnable rebuildTask = () -> {
            Log.debug("Rebuilding the Lucene index...");
            final Instant start = Instant.now();
            List<Long> conversationIDs = new ArrayList<>();
            Map<Long, Boolean> externalMetaData = new HashMap<>();
            Connection con = null;
            PreparedStatement pstmt = null;
            ResultSet rs = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(ALL_CONVERSATIONS);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    long conversationID = rs.getLong(1);
                    conversationIDs.add(conversationID);
                    externalMetaData.put(conversationID, rs.getInt(2) == 1);
                }
            }
            catch (SQLException sqle) {
                Log.error("An exception occurred while trying to fetch all conversations from the database to rebuild the Lucene index.", sqle);
            }
            finally {
                DbConnectionManager.closeConnection(rs, pstmt, con);
            }

            Log.debug("... identified {} conversations.", conversationIDs.size());
            if (!conversationIDs.isEmpty()) {
                // Index the conversations.
                final Analyzer analyzer = new StandardAnalyzer();
                final IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE); // force re-create (as opposed to CREATE_OR_APPEND)

                try ( final IndexWriter writer = new IndexWriter(directory, iwc) )
                {
                    Log.debug("... started to index conversations to rebuild the Lucene index.");
                    final long newestDate = indexConversations(conversationIDs, externalMetaData, writer, true);

                    // Done indexing so store a last modified date.
                    if (newestDate != -1) {
                        lastModified = newestDate;
                        indexProperties.setProperty("lastModified", Long.toString(lastModified));
                    }
                    Log.debug("... finished indexing conversations to rebuild the Lucene index..");
                }
                catch (IOException ioe) {
                    Log.error("An exception occurred while rebuilding the Lucene index.", ioe);
                }
            }
            // Done rebuilding the index, so reset state.
            rebuildFuture = null;
            rebuildInProgress = false;
            final Duration duration = Duration.between( start, Instant.now() );
            Log.debug("Finished rebuilding the Lucene index. Duration: {}", duration );
        };
        taskEngine.submit(rebuildTask);

        return rebuildFuture;
    }

    /**
     * Returns a Future representing the status of an index rebuild operation. This is the
     * same Future returned by the {@link #rebuildIndex()} method; access is provided via
     * this method as a convenience. If the index is not currently being rebuilt, this method
     * will return <tt>null</tt>.
     *
     * @return a Future that represents the index rebuild status or <tt>null</tt> if the
     *      index is not being rebuilt.
     */
    public Future<Integer> getIndexRebuildProgress() {
        return rebuildFuture;
    }

    /**
     * Indexes a set of conversations. Each conversation is stored as a single Lucene document
     * by appending message bodies together. The date of the newest message indexed is
     * returned, or -1 if no conversations are indexed.
     *
     * @param conversationIDs the ID's of the conversations to index.
     * @param externalMetaData meta-data about whether each conversation involves a participant on
     *      an external server.
     * @param writer an IndexWriter to add the documents to.
     * @param indexRebuild true if this is an index rebuild operation.
     * @return the date of the newest message archived.
     */
    private long indexConversations(List<Long> conversationIDs, Map<Long, Boolean> externalMetaData,
            IndexWriter writer, boolean indexRebuild) throws IOException
    {
        if (conversationIDs.isEmpty()) {
            return -1;
        }

        // Keep track of how many conversations we index for index rebuild stats.
        int indexedConversations = 0;

        long newestDate = -1;
        // Index 250 items at a time.
        final int OP_SIZE = 250;
        int n = ((conversationIDs.size() - 1) / OP_SIZE);
        if (n == 0) {
            n = 1;
        }
        for (int i = 0; i < n; i++) {
            StringBuilder inSQL = new StringBuilder();
            inSQL.append(" (");
            int start = i * OP_SIZE;
            int end = (start + OP_SIZE > conversationIDs.size()) ? conversationIDs.size() : start + OP_SIZE;
            if (end > conversationIDs.size()) {
                end = conversationIDs.size();
            }
            inSQL.append(conversationIDs.get(start));
            for (int j = start + 1; j < end; j++) {
                inSQL.append(", ").append(conversationIDs.get(j));
            }
            inSQL.append(")");
            // Get the messages.
            Connection con = null;
            PreparedStatement pstmt = null;
            ResultSet rs = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(CONVERSATION_MESSAGES.replaceAll("\\?", inSQL.toString()));
                rs = pstmt.executeQuery();
                long conversationID = -1;
                long date = -1;
                Set<JID> jids = null;
                StringBuilder text = null;
                // Loop through each message. Each conversation is a single document. So, as
                // we find each conversation we save off the last chunk of content as a document.
                while (rs.next()) {
                    long id = rs.getLong(1);
                    if (id != conversationID) {
                        if (conversationID != -1) {
                            // Index the previously defined doc.
                            boolean external = externalMetaData.get(conversationID);
                            indexDocument(writer, conversationID, external, date, jids, text.toString());
                        }
                        // Reset the variables to index the next conversation.
                        conversationID = id;
                        date = rs.getLong(2);
                        jids = new TreeSet<JID>();
                        // Get the JID's. Each JID may be stored in full format. We convert
                        // to bare JID for indexing so that searching is possible.
                        jids.add(new JID(rs.getString(3)).asBareJID());
                        jids.add(new JID(rs.getString(4)).asBareJID());
                        text = new StringBuilder();
                    }
                    // Make sure that we record the earliest date of the conversation start
                    // for consistency.
                    long msgDate = rs.getLong(2);
                    if (msgDate < date) {
                        date = msgDate;
                    }
                    // See if this is the newest message found so far.
                    if (msgDate > newestDate) {
                        newestDate = msgDate;
                    }
                    // Add the body of the current message to the buffer.
                    text.append(DbConnectionManager.getLargeTextField(rs, 5)).append("\n");
                }
                // Finally, index the last document found.
                if (conversationID != -1) {
                    // Index the previously defined doc.
                    boolean external = externalMetaData.get(conversationID);
                    indexDocument(writer, conversationID, external, date, jids, text.toString());
                }
                // If this is an index rebuild, we need to track the percentage done.
                if (indexRebuild) {
                    indexedConversations++;
                    rebuildFuture.setPercentageDone(indexedConversations/conversationIDs.size());
                }
            }
            catch (SQLException sqle) {
                Log.error(sqle.getMessage(), sqle);
            }
            finally {
                DbConnectionManager.closeConnection(rs, pstmt, con);
            }
        }
        return newestDate;
    }

    /**
     * Indexes a single conversation.
     *
     * @param writer the index modifier.
     * @param conversationID the ID of the conversation to index.
     * @param external true if the conversation has a participant from an external server.
     * @param date the date the conversation was started.
     * @param jids the JIDs of the users in the conversation.
     * @param text the full text of the conversation.
     * @throws IOException if an IOException occurs.
     */
    private void indexDocument(IndexWriter writer, long conversationID, boolean external,
            long date, Set<JID> jids, String text) throws IOException
    {
        Document document = new Document();
        document.add(new StoredField("conversationID", conversationID ) );
        document.add(new StringField("external", String.valueOf(external), Field.Store.NO));
        document.add(new SortedDocValuesField("date", new BytesRef(DateTools.timeToString(date, DateTools.Resolution.DAY))));
        for (JID jid : jids) {
            document.add(new StringField("jid", jid.toString(), Field.Store.NO));
        }
        document.add(new TextField("text", text, Field.Store.NO));
        writer.addDocument(document);
    }

    /**
     * Returns an IndexSearcher to search the archive index.
     *
     * @return an IndexSearcher.
     * @throws IOException if an IOException occurs.
     */
    synchronized IndexSearcher getSearcher() throws IOException {
        // If the searcher hasn't been instantiated, create it.
        if (searcher == null) {
            DirectoryReader reader = DirectoryReader.open(directory);
            searcher = new IndexSearcher(reader);
        }
        // See if the searcher needs to be closed due to the index being updated.
        final DirectoryReader replacement = DirectoryReader.openIfChanged((DirectoryReader) searcher.getIndexReader());
        if ( replacement != null ) {
            Log.debug( "Returning new Index Searcher (as index was updated)");
            searcher.getIndexReader().close();
            searcher = new IndexSearcher(replacement);
        }
        return searcher;
    }

    /**
     * Loads a property manager for search properties if it isn't already
     * loaded. If an XML file for the search properties isn't already
     * created, it will attempt to make a file with default values.
     */
    private void loadPropertiesFile(File searchDir) throws IOException {
        File indexPropertiesFile = new File(searchDir, "indexprops.xml");

        // Make sure the file actually exists. If it doesn't, a new file
        // will be created.
        // If it doesn't exists we have to create it.
        if (!indexPropertiesFile.exists()) {
            org.dom4j.Document doc = DocumentFactory.getInstance().createDocument(
                    DocumentFactory.getInstance().createElement("search"));
            // Now, write out to the file.
            try ( Writer out = new FileWriter(indexPropertiesFile) )
            {
                // Use JDOM's XMLOutputter to do the writing and formatting.
                XMLWriter outputter = new XMLWriter(out, OutputFormat.createPrettyPrint());
                outputter.write(doc);
                outputter.flush();
            }
            catch ( Exception e )
            {
                Log.error(e.getMessage(), e);
            }
            // Ignore.
        }
        indexProperties = new XMLProperties(indexPropertiesFile);
    }

    /**
     * A Future class to track the status of index rebuilding.
     */
    private class RebuildFuture implements Future<Integer> {

        private int percentageDone = 0;

        public boolean cancel(boolean mayInterruptIfRunning) {
            // Don't allow cancels.
            return false;
        }

        public boolean isCancelled() {
            return false;
        }

        public boolean isDone() {
            return percentageDone == 100;
        }

        public Integer get() throws InterruptedException, ExecutionException {
            return percentageDone;
        }

        public Integer get(long timeout, TimeUnit unit) throws InterruptedException,
                ExecutionException, TimeoutException
        {
            return percentageDone;
        }

        /**
         * Sets the percentage done.
         *
         * @param percentageDone the percentage done.
         */
        public void setPercentageDone(int percentageDone) {
            if (percentageDone < 0 || percentageDone > 100) {
                throw new IllegalArgumentException("Invalid value: " + percentageDone);
            }
            this.percentageDone = percentageDone;
        }
    }
}
