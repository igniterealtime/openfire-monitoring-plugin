package com.reucon.openfire.plugin.archive.impl;

import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.ConversationManager;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.index.LuceneIndexer;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.reporting.util.TaskEngine;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.print.Doc;
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
 * Creates and maintains a Lucene index for archived messages.
 *
 * This implementation differs from {@link MucIndexer} which is specific to chatrooms, as well as from
 * {@link org.jivesoftware.openfire.archive.ArchiveIndexer} which archives entire conversations (as opposed to single
 * messages).
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class MessageIndexer extends LuceneIndexer
{
    /**
     * The version of the structure that is stored in the Lucene index. When this value differs from the value that is
     * stored in a file with the index, then upon restart, an automatic re-indexation will occur.
     */
    public static final int SCHEMA_VERSION = 1;

    public static final String ALL_MESSAGES = "SELECT fromJID, fromJIDResource, toJID, toJIDResource, sentDate, body, messageID, isPMforJID "
                                            + "FROM ofMessageArchive "
                                            + "WHERE body IS NOT NULL "
                                            + "AND messageID IS NOT NULL";

    public static final String NEW_MESSAGES = ALL_MESSAGES + " AND sentDate > ?";

    private ConversationManager conversationManager;

    public MessageIndexer( final TaskEngine taskEngine, final ConversationManager conversationManager )
    {
        super(taskEngine, new File(JiveGlobals.getHomeDirectory() + File.separator + MonitoringConstants.NAME + File.separator + "msgsearch"), "MESSAGE", SCHEMA_VERSION);
        this.conversationManager = conversationManager;
    }

    @Override
    protected Instant doUpdateIndex( final IndexWriter writer, final Instant lastModified ) throws IOException
    {
        // Do nothing if message archiving is disabled.
        if ( !conversationManager.isMessageArchivingEnabled() ) {
            return lastModified;
        }

        if ( lastModified.equals(Instant.EPOCH)) {
            Log.warn( "Updating (not creating) an index since 'EPOCH'. This is suspicious, as it suggests that an existing, but empty index is being operated on. If the index is non-empty, index duplication might occur." );
        }

        // Index messages that arrived since the provided date.
        Log.debug("... started to index messages since {} to update the Lucene index.", lastModified);
        final Instant newestDate = indexMessages(writer, lastModified);
        Log.debug("... finished indexing messages to update the Lucene index. Last indexed message date: {}", newestDate);
        return newestDate;
    }

    @Override
    public Instant doRebuildIndex( final IndexWriter writer ) throws IOException
    {
        // Do nothing if message archiving is disabled.
        if (!conversationManager.isMessageArchivingEnabled()) {
            return Instant.EPOCH;
        }

        // Index all messages.
        Log.debug("... started to index messages to rebuild the Lucene index.");
        final Instant newestDate = indexMessages(writer, Instant.EPOCH);
        Log.debug("... finished indexing messages to update the Lucene index. Lasted indexed message date {}", newestDate);
        return newestDate;
    }

    /**
     * Returns all identifiers of messages in the system.
     *
     * @return A set of message identifiers. Possibly empty, never null.
     */
    private Instant indexMessages( IndexWriter writer, Instant since )
    {
        Instant latest = since;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            // This retrieves _all_ table content, and operates on the entire result set. For large data sets, one would
            // expect issues caused by the entire data set being loaded into memory, before being operated on.
            // However, with 'fetch size' hint as coded below, this didn't appear to cause any issues on
            // our Igniterealtime.org domain. This domain had over 10,500,000 messages at the time, had a Java heap that
            // was not larger than 1GB, and uses PostgreSQL 11.5. At the time of the test, it was running Openfire 4.5.0.
            // The entire process took under 7 minutes.
            // Preventing the driver to collect all results at once depends on auto-commit from being disabled, at
            // least for postgres. Getting a 'transaction' connection will ensure this (if supported).
            con = DbConnectionManager.getTransactionConnection();

            boolean wasAutoCommit = con.getAutoCommit();
            if ( wasAutoCommit ) {
                con.setAutoCommit(false);
            }

            if ( since.equals( Instant.EPOCH ) ) {
                pstmt = con.prepareStatement(ALL_MESSAGES);
            } else {
                pstmt = con.prepareStatement(NEW_MESSAGES);
                pstmt.setLong(1, Date.from(since).getTime());
            }

            pstmt.setFetchSize(250);
            rs = pstmt.executeQuery();

            long progress = 0;
            Instant lastProgressReport = Instant.now();
            while (rs.next()) {
                final long messageID = rs.getLong("messageID");

                final String fromJIDBare = rs.getString("fromJID");
                final String fromJIDResource = rs.getString("fromJIDResource");
                final JID fromJID;
                try {
                    fromJID = new JID(fromJIDResource == null || fromJIDResource.isEmpty() ? fromJIDBare : fromJIDBare + "/" + fromJIDResource);
                } catch (IllegalArgumentException ex) {
                    Log.debug("Invalid fromJID value for messageID {}", messageID, ex);
                    continue;
                }

                final String toJIDBare = rs.getString("toJID");
                final String toJIDResource = rs.getString("toJIDResource");
                final JID toJID;
                try {
                    toJID = new JID(toJIDResource == null || toJIDResource.isEmpty() ? toJIDBare : toJIDBare + "/" + toJIDResource);
                } catch (IllegalArgumentException ex) {
                    Log.debug("Invalid toJID value for messageID {}", messageID, ex);
                    continue;
                }

                final String isPMforJIDValue = rs.getString("isPMforJID");
                final JID isPMforJID;
                if ( isPMforJIDValue == null ) {
                    isPMforJID = null;
                } else {
                    try {
                        isPMforJID = new JID(isPMforJIDValue);
                    } catch (IllegalArgumentException ex) {
                        Log.debug("Invalid isPMforJID value for messageID{}", messageID, ex);
                        continue;
                    }
                }
                final Instant sentDate = Instant.ofEpochMilli( Long.parseLong( rs.getString("sentDate") ));

                final String body = DbConnectionManager.getLargeTextField(rs, 6);

                // This shouldn't happen, but I've seen a very small percentage of rows have a null body.
                if ( body == null ) {
                    continue;
                }

                if ( XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(toJID) != null) {
                    // Store in the archive of the chat room.
                    final JID room = toJID.asBareJID();
                    final JID pmFromJID = isPMforJID != null ? fromJID.asBareJID() : null; // only set if the message was a PM.
                    final Document document = createMUCDocument(room, messageID, pmFromJID, isPMforJID, sentDate, body);
                    writer.addDocument(document);

                    if (sentDate.isAfter(latest)) {
                        latest = sentDate;
                    }

                    // Store in the personal archive of the sender.
                    if ( XMPPServer.getInstance().isLocal( fromJID ) ) {
                        final Document personalDocument = createPersonalDocument(fromJID.asBareJID(), messageID, fromJID, toJID, sentDate, body);
                        writer.addDocument(personalDocument);

                        if (sentDate.isAfter(latest)) {
                            latest = sentDate;
                        }
                    }

                } else {
                    // Not a chat room
                    if ( XMPPServer.getInstance().isLocal( fromJID ) ) {
                        // Store in the personal archive of the sender.
                        final Document document = createPersonalDocument(fromJID.asBareJID(), messageID, fromJID, toJID, sentDate, body);
                        writer.addDocument(document);

                        if (sentDate.isAfter(latest)) {
                            latest = sentDate;
                        }
                    }

                    if ( XMPPServer.getInstance().isLocal( toJID ) ) {
                        // Store in the personal archive of the recipient.
                        final Document document = createPersonalDocument(toJID.asBareJID(), messageID, fromJID, toJID, sentDate, body);
                        writer.addDocument(document);

                        if (sentDate.isAfter(latest)) {
                            latest = sentDate;
                        }
                    }
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
            Log.error("An exception occurred while trying to fetch all messages from the database to rebuild the Lucene index.", sqle);
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
     * Creates a index document for one particular chat message for a particular users's personal archive.
     *
     * @param owner the (bare) JID of the owner of the archive.
     * @param messageID ID of the message that was exchanged.
     * @param fromJID Bare or full JID of the author of the message.
     * @param toJID Bare or full JID of the addressee of the message.
     * @param sentDate Timestamp of the message.
     * @param body Message text.
     */
    protected static Document createPersonalDocument( @Nonnull final JID owner,
                                              final long messageID,
                                              @Nonnull final JID fromJID,
                                              @Nonnull final JID toJID,
                                              @Nonnull final Instant sentDate,
                                              @Nonnull final String body)
    {
        // 'with' should be the entity that the owner of the archive is exchanging messages with.
        final JID with;
        if ( owner.asBareJID().equals( fromJID.asBareJID() ) ) {
            with = toJID;
        } else {
            with = fromJID;
        }

        final Document document = new Document();
        document.add(new StoredField("messageID", messageID ) );
        document.add(new NumericDocValuesField("messageIDRange", messageID));
        document.add(new StringField("owner", owner.toBareJID(), Field.Store.NO));
        document.add(new StringField("withBare", with.toBareJID(), Field.Store.NO));
        if ( with.getResource() != null ) {
            document.add(new StringField("withResource", with.getResource(), Field.Store.NO));
        }
        document.add(new NumericDocValuesField("sentDate", sentDate.toEpochMilli()));
        document.add(new TextField("body", body, Field.Store.NO));
        return document;
    }

    /**
     * Creates a index document for one particular chat message exchanged in a MUC room.
     *
     * @param owner the (bare) JID of the owner of the archive.
     * @param messageID ID of the message that was exchanged.
     * @param pmFromJID Bare or full JID of the author of the message, if it is a PM
     * @param pmToJID Bare or full JID of the addressee of the message, if it is a PM
     * @param sentDate Timestamp of the message.
     * @param body Message text.
     */
    protected static Document createMUCDocument( @Nonnull final JID owner,
                                                  final long messageID,
                                                  @Nullable final JID pmFromJID,
                                                  @Nullable final JID pmToJID,
                                                  @Nonnull final Instant sentDate,
                                                  @Nonnull final String body)
    {
        final Document document = new Document();
        document.add(new StoredField("messageID", messageID ) );
        document.add(new NumericDocValuesField("messageIDRange", messageID));
        document.add(new StringField("room", owner.toBareJID(), Field.Store.NO));
        document.add(new StringField("isPrivateMessage", pmFromJID != null || pmToJID != null ? "true" : "false", Field.Store.NO));
        if ( pmFromJID != null ) {
            document.add(new StringField("pmFromJID", pmFromJID.toBareJID(), Field.Store.NO));
        }

        if (pmToJID != null) {
            document.add(new StringField("pmToJID", pmToJID.toBareJID(), Field.Store.NO));
        }
        document.add(new NumericDocValuesField("sentDate", sentDate.toEpochMilli()));
        document.add(new TextField("body", body, Field.Store.NO));
        return document;
    }
}
