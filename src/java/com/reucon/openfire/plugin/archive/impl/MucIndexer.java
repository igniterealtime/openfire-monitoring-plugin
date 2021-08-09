package com.reucon.openfire.plugin.archive.impl;

import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.archive.ConversationManager;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.index.LuceneIndexer;
import org.jivesoftware.openfire.reporting.util.TaskEngine;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.JID;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Creates and maintains a Lucene index for messages exchanged in multi-user chat.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class MucIndexer extends LuceneIndexer
{
    /**
     * The version of the structure that is stored in the Lucene index. When this value differs from the value that is
     * stored in a file with the index, then upon restart, an automatic re-indexation will occur.
     */
    public static final int SCHEMA_VERSION = 1;

    public static final String ALL_MUC_MESSAGES = "SELECT roomID, sender, logTime, body, messageID FROM ofMucConversationLog WHERE messageID IS NOT NULL";
    public static final String NEW_MUC_MESSAGES = "SELECT roomID, sender, logTime, body, messageID FROM ofMucConversationLog WHERE messageID IS NOT NULL AND logTime > ?";

    private ConversationManager conversationManager;

    public MucIndexer( final TaskEngine taskEngine, final ConversationManager conversationManager )
    {
        super(taskEngine, new File(JiveGlobals.getHomeDirectory() + File.separator + MonitoringConstants.NAME + File.separator + "mucsearch"), "MUCSEARCH", SCHEMA_VERSION);
        this.conversationManager = conversationManager;
    }

    @Override
    protected Instant doUpdateIndex( final IndexWriter writer, final Instant lastModified ) throws IOException
    {
        // Do nothing if room archiving is disabled.
        if ( !conversationManager.isRoomArchivingEnabled() ) {
            return lastModified;
        }

        if ( lastModified.equals(Instant.EPOCH)) {
            Log.warn( "Updating (not creating) an index since 'EPOCH'. This is suspicious, as it suggests that an existing, but empty index is being operated on. If the index is non-empty, index duplication might occur." );
        }

        // Index MUC messages that arrived since the provided date.
        Log.debug("... started to index MUC messages since {} to update the Lucene index.", lastModified);
        final Instant newestDate = indexMUCMessages(writer, lastModified);
        Log.debug("... finished indexing MUC messages to update the Lucene index. Last indexed message date: {}", newestDate);
        return newestDate;
    }

    @Override
    public Instant doRebuildIndex( final IndexWriter writer ) throws IOException
    {
        // Do nothing if room archiving is disabled.
        if (!conversationManager.isRoomArchivingEnabled()) {
            return Instant.EPOCH;
        }

        // Index all MUC messages.
        Log.debug("... started to index MUC messages to rebuild the Lucene index.");
        final Instant newestDate = indexMUCMessages(writer, Instant.EPOCH);
        Log.debug("... finished indexing MUC messages to update the Lucene index. Lasted indexed message date {}", newestDate);
        return newestDate;
    }

    /**
     * Returns all identifiers of MUC messages in the system.
     *
     * @return A set of message identifiers. Possibly empty, never null.
     */
    private Instant indexMUCMessages( IndexWriter writer, Instant since )
    {
        Instant latest = since;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            // This retrieves _all_ table content, and operates on the entire result set. For large data sets, one would
            // expect issues caused by the entire data set being loaded into memory, before being operated on.
            // However, with 'fetch size' hint as coded below, this didn't appear to cause any issues on
            // our Igniterealtime.org domain. This domain had over 300,000 messages at the time, had a Java heap that
            // was not larger than 1GB, and uses PostgreSQL 11.5. At the time of the test, it was running Openfire 4.5.0.
            // The entire process took under 8 seconds.
            // Preventing the driver to collect all results at once depends on auto-commit from being disabled, at
            // least for postgres. Getting a 'transaction' connection will ensure this (if supported).
            con = DbConnectionManager.getTransactionConnection();

            if ( since.equals( Instant.EPOCH ) ) {
                pstmt = con.prepareStatement(ALL_MUC_MESSAGES);
            } else {
                pstmt = con.prepareStatement(NEW_MUC_MESSAGES);
                pstmt.setString(1, StringUtils.dateToMillis(Date.from(since))); // This mimics org.jivesoftware.openfire.muc.spi.MUCPersistenceManager.saveConversationLogBatch
            }

            pstmt.setFetchSize(250);
            rs = pstmt.executeQuery();

            long progress = 0;
            Instant lastProgressReport = Instant.now();
            while (rs.next()) {
                final long roomID = rs.getLong("roomID");
                final long messageID = rs.getLong("messageID");
                final JID sender;
                try {
                    sender = new JID(rs.getString("sender"));
                } catch (IllegalArgumentException ex) {
                    Log.debug("Invalid JID value for roomID {}, messageID {}.", roomID, messageID, ex);
                    continue;
                }
                final Instant logTime = Instant.ofEpochMilli( Long.parseLong( rs.getString("logTime") ));
                final String body = DbConnectionManager.getLargeTextField(rs, 4);

                // This shouldn't happen, but I've seen a very small percentage of rows have a null body.
                if ( body == null ) {
                    continue;
                }

                // Index message.
                final Document document = createDocument(roomID, messageID, sender, logTime, body );
                writer.addDocument(document);

                if (logTime.isAfter(latest)) {
                    latest = logTime;
                }

                // When there are _many_ messages to be processed, log an occasional progress indicator, to let admins know that things are still churning.
                ++progress;
                if ( lastProgressReport.isBefore( Instant.now().minus(10, ChronoUnit.SECONDS)) ) {
                    Log.debug( "... processed {} messages so far.", progress);
                    lastProgressReport = Instant.now();
                }
            }
            Log.debug( "... finished the entire result set. Processed {} messages in total.", progress );
        }
        catch (SQLException sqle) {
            Log.error("An exception occurred while trying to fetch all MUC messages from the database to rebuild the Lucene index.", sqle);
        }
        catch (IOException ex) {
            Log.error("An exception occurred while trying to write the Lucene index.", ex);
        }
        finally {
            DbConnectionManager.closeResultSet(rs);
            DbConnectionManager.closeTransactionConnection(pstmt, con, false); // Only read queries are performed. No need to roll back, even on exceptions.
        }
        return latest;
    }

    /**
     * Creates a index document for one particular chat message.
     *
     * @param roomID ID of the MUC room in which the message was exchanged.
     * @param messageID ID of the message that was exchanged.
     * @param sender Bare or full JID of the author of the message (cannot be null).
     * @param logTime Timestamp of the message (cannot be null).
     * @param body Message text (cannot be null).
     */
    protected static Document createDocument( long roomID, long messageID, JID sender, Instant logTime, String body)
    {
        final Document document = new Document();
        document.add(new LongPoint("roomID", roomID ) );
        document.add(new StoredField("messageID", messageID ) );
        document.add(new NumericDocValuesField("messageIDRange", messageID));
        document.add(new StringField("senderBare", sender.toBareJID(), Field.Store.NO));
        if ( sender.getResource() != null ) {
            document.add(new StringField("senderResource", sender.getResource(), Field.Store.NO));
        }
        document.add(new NumericDocValuesField("logTime", logTime.toEpochMilli()));
        document.add(new TextField("body", body, Field.Store.NO));
        return document;
    }
}
