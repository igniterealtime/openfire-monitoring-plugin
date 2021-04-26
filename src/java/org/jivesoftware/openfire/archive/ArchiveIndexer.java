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

import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.reporting.util.TaskEngine;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.JID;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/**
 * Indexes archived conversations. If conversation archiving is not enabled,
 * this class does nothing. The search index is maintained in the <tt>monitoring/search</tt>
 * directory of the Openfire home directory. It's automatically updated with the latest
 * conversation content as long as conversation archiving is enabled. The index update
 * interval is controlled by the System Property "conversation.search.updateInterval" and
 * the default value is 15 minutes.
 *
 * @see ArchiveSearcher
 * @author Matt Tucker
 */
public class ArchiveIndexer extends org.jivesoftware.openfire.index.LuceneIndexer
{
    private static final String ALL_CONVERSATIONS = "SELECT conversationID, isExternal FROM ofConversation";
    private static final String NEW_CONVERSATIONS = "SELECT DISTINCT conversationID FROM ofMessageArchive WHERE sentDate > ?";
    private static final String CONVERSATION_METADATA = "SELECT isExternal FROM ofConversation WHERE conversationID=?";
    private static final String CONVERSATION_MESSAGES = "SELECT conversationID, sentDate, fromJID, toJID, body FROM ofMessageArchive WHERE conversationID IN ? ORDER BY conversationID";

    private ConversationManager conversationManager;

    /**
     * Constructs a new archive indexer.
     *
     * @param conversationManager a ConversationManager instance.
     * @param taskEngine a task engine instance.
     */
    public ArchiveIndexer(ConversationManager conversationManager, TaskEngine taskEngine) {
        super(taskEngine, new File(JiveGlobals.getHomeDirectory() + File.separator + MonitoringConstants.NAME + File.separator + "search"), "CONVERSATION" );
        this.conversationManager = conversationManager;
    }

    @Override
    public void stop()
    {
        super.stop();
        conversationManager = null;
    }

    /**
     * Updates the index with all new conversation data since the last index update.
     *
     * @param writer The instance used to modify the index data (cannot be null).
     * @param lastModified The date up until the index has been updated (cannot be null)
     * @return the date of the up until the index has been updated after processing (never null).
     */
    @Override
    protected Instant doUpdateIndex( final IndexWriter writer, Instant lastModified) throws IOException
    {
        // Do nothing if archiving is disabled.
        if (!conversationManager.isArchivingEnabled()) {
            return lastModified;
        }

        // Find all conversations that have changed since the last index run.
        final List<Long> conversationIDs = findModifiedConversations(lastModified);

        if (conversationIDs.isEmpty()) {
            return lastModified;
        }

        // Load meta-data for each conversation that needs updating.
        final SortedMap<Long, Boolean> externalMetaData = extractMetaData(conversationIDs);

        // Delete any conversations found -- they may have already been indexed, but updated since then.
        Log.debug("... deleting all to-be-updated conversations from the index.");
        for (long conversationID : conversationIDs) {
            writer.deleteDocuments(new Term("conversationID", Long.toString(conversationID)));
        }

        // Now index all the new conversations.
        Log.debug("... started to index conversations to update the Lucene index.");
        final Instant newestDate = indexConversations(externalMetaData, writer, false);

        // Done indexing so store a last modified date.
        if (newestDate.isAfter(lastModified)) {
            lastModified = newestDate;
        }

        return lastModified;
    }

    /**
     * Updates the index with all conversations that are available. This effectively rebuilds the index.
     *
     * @param writer The instance used to modify the index data (cannot be null).
     * @return the date of the up until the index has been updated after processing (never null).
     */
    @Override
    public synchronized Instant doRebuildIndex( final IndexWriter writer ) throws IOException
    {
        // Do nothing if archiving is disabled.
        if (!conversationManager.isArchivingEnabled()) {
            return Instant.EPOCH;
        }

        final SortedMap<Long, Boolean> conversationMetadata = findAllConversations();
        Log.debug("... identified {} conversations.", conversationMetadata.size());
        if (conversationMetadata.isEmpty()) {
            return Instant.EPOCH;
        }

        // Index the conversations.
        Log.debug("... started to index conversations to rebuild the Lucene index.");
        final Instant newestDate = indexConversations(conversationMetadata, writer, true);
        Log.debug("... finished indexing conversations to rebuild the Lucene index..");
        return newestDate;
    }

    /**
     * Returns all identifiers of conversations in the system.
     *
     * The returned collection maps the conversation identifier to a boolean, that indicates if the conversation is
     * 'external'
     *
     * @return a map that contains conversation identifiers and metadata. Possibly empty, never null.
     */
    private SortedMap<Long, Boolean> findAllConversations()
    {
        SortedMap<Long, Boolean> externalMetaData = new TreeMap<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_CONVERSATIONS);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                long conversationID = rs.getLong(1);
                externalMetaData.put(conversationID, rs.getInt(2) == 1);
            }
        }
        catch (SQLException sqle) {
            Log.error("An exception occurred while trying to fetch all conversations from the database to rebuild the Lucene index.", sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return externalMetaData;
    }

    /**
     * Finds converstations that are modified after the specified date.
     *
     * @param lastModified The date that marks the beginning of the period for which to return conversations. Cannot be null.
     * @return A list of conversation identifiers (never null, possibly empty).
     */
    private List<Long> findModifiedConversations( final Instant lastModified )
    {
        Log.debug("... finding all conversations modified since: {}", lastModified);
        final List<Long> results = new ArrayList<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(NEW_CONVERSATIONS);
            pstmt.setLong(1, lastModified.toEpochMilli());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                results.add(rs.getLong(1));
            }
        }
        catch ( SQLException sqle) {
            Log.error("An exception occurred while trying to fetch new/updated conversations from the database to update the Lucene index.", sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        Log.debug("... identified {} conversations.", results.size());
        return results;
    }

    /**
     * Returns metadata for a specific list of conversations.
     *
     * The returned collection maps the conversation identifier to a boolean, that indicates if the conversation is
     * 'external'
     *
     * @param conversationIDs A list of identifiers for conversations to be included in the result cannot be null.
     * @return a map that contains conversation identifiers and metadata. Possibly empty, never null.
     */
    private SortedMap<Long, Boolean> extractMetaData( final List<Long> conversationIDs )
    {
        Log.debug("... loading meta-data for all to-be-updated conversations.");
        final SortedMap<Long, Boolean> results = new TreeMap<>();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        for (long conversationID : conversationIDs) {
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(CONVERSATION_METADATA);
                pstmt.setLong(1, conversationID);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    results.put(conversationID, rs.getInt(1) == 1);
                }
            }
            catch ( SQLException sqle) {
                Log.error("An exception occurred while trying to load metadata for conversations to be updated in the Lucene index.", sqle);
            }
            finally {
                DbConnectionManager.closeConnection(rs, pstmt, con);
            }
        }
        return results;
    }

    /**
     * Indexes a set of conversations. Each conversation is stored as a single Lucene document
     * by appending message bodies together. The date of the newest message indexed is
     * returned, or -1 if no conversations are indexed.
     *
     * @param conversations meta-data about whether each conversation involves a participant on
     *      an external server.
     * @param writer an IndexWriter to add the documents to.
     * @param indexRebuild true if this is an index rebuild operation.
     * @return the date of the newest message archived, or null if no messages were archived.
     */
    private Instant indexConversations(SortedMap<Long, Boolean> conversations, IndexWriter writer, boolean indexRebuild) throws IOException
    {
        if (conversations.isEmpty()) {
            return Instant.EPOCH;
        }

        final List<Long> conversationIDs = new ArrayList<>(conversations.keySet());

        // Keep track of how many conversations we index for index rebuild stats.
        int indexedConversations = 0;

        Instant newestDate = Instant.EPOCH;
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
            int end = Math.min(start + OP_SIZE, conversationIDs.size());
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
                Instant date = Instant.EPOCH;
                Set<JID> jids = null;
                StringBuilder text = null;
                // Loop through each message. Each conversation is a single document. So, as
                // we find each conversation we save off the last chunk of content as a document.
                while (rs.next()) {
                    long id = rs.getLong(1);
                    if (id != conversationID) {
                        if (conversationID != -1) {
                            // Index the previously defined doc.
                            boolean external = conversations.get(conversationID);
                            indexDocument(writer, conversationID, external, date, jids, text.toString());
                        }
                        // Reset the variables to index the next conversation.
                        conversationID = id;
                        date = Instant.ofEpochMilli(rs.getLong(2));
                        jids = new TreeSet<>();
                        // Get the JID's. Each JID may be stored in full format. We convert to bare JID for indexing so that searching is possible.
                        jids.add(new JID(rs.getString(3)).asBareJID());
                        jids.add(new JID(rs.getString(4)).asBareJID());
                        text = new StringBuilder();
                    }
                    // Make sure that we record the earliest date of the conversation start for consistency.
                    final Instant msgDate = Instant.ofEpochMilli(rs.getLong(2));
                    if (msgDate.isBefore(date)) {
                        date = msgDate;
                    }
                    // See if this is the newest message found so far.
                    if (msgDate.isAfter(newestDate)) {
                        newestDate = msgDate;
                    }
                    // Add the body of the current message to the buffer.
                    text.append(DbConnectionManager.getLargeTextField(rs, 5)).append("\n");
                }
                // Finally, index the last document found.
                if (conversationID != -1) {
                    // Index the previously defined doc.
                    boolean external = conversations.get(conversationID);
                    indexDocument(writer, conversationID, external, date, jids, text.toString());
                }
                // If this is an index rebuild, we need to track the percentage done.
                if (indexRebuild) {
                    indexedConversations++;
                    rebuildFuture.setPercentageDone(indexedConversations/conversationIDs.size());
                }
            }
            catch (SQLException sqle) {
                Log.error("An exception occurred while indexing conversations.", sqle);
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
    private void indexDocument(IndexWriter writer, long conversationID, boolean external, Instant date, Set<JID> jids, String text) throws IOException
    {
        final Document document = new Document();
        document.add(new StoredField("conversationID", conversationID ) );
        document.add(new StringField("external", String.valueOf(external), Field.Store.NO));
        document.add(new SortedDocValuesField("date", new BytesRef(DateTools.timeToString(date.toEpochMilli(), DateTools.Resolution.DAY))));
        for (JID jid : jids) {
            document.add(new StringField("jid", jid.toString(), Field.Store.NO));
        }
        document.add(new TextField("text", text, Field.Store.NO));
        writer.addDocument(document);
    }
}
