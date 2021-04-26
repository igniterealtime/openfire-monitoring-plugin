package com.reucon.openfire.plugin.archive.impl;

import com.reucon.openfire.plugin.archive.PersistenceManager;
import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import com.reucon.openfire.plugin.archive.model.Conversation;
import com.reucon.openfire.plugin.archive.util.StanzaIDUtil;
import com.reucon.openfire.plugin.archive.xep0059.XmppResultSet;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * A persistence provider that facilitates the implementation of Message Archive Management (XEP-0313) for MUC rooms.
 *
 * Created by dwd on 25/07/16.
 */
public class MucMamPersistenceManager implements PersistenceManager {
    private final static Logger Log = LoggerFactory.getLogger( MucMamPersistenceManager.class );
    private static SystemProperty<Boolean> USE_OPENFIRE_TABLES = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("conversation.database.use-openfire-tables")
        .setDefaultValue(false)
        .setDynamic(true)
        .setPlugin(MonitoringConstants.PLUGIN_NAME)
        .build();
    private static final int DEFAULT_MAX = 100;

    @Override
    public Collection<Conversation> findConversations(Date startDate, Date endDate, JID owner, JID with, XmppResultSet xmppResultSet) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public Collection<ArchivedMessage> findMessages(Date startDate, Date endDate, JID archiveOwner, JID messageOwner, JID with, String query, XmppResultSet xmppResultSet, boolean useStableID ) throws NotFoundException, DataRetrievalException {
        Log.debug( "Finding messages in archive '{}' for user '{}' with start date '{}', end date '{}' with '{}', query: '{}' and resultset '{}', useStableId '{}'.", archiveOwner, messageOwner, startDate, endDate, with, query, xmppResultSet, useStableID );
        final MultiUserChatManager manager = XMPPServer.getInstance().getMultiUserChatManager();
        final MultiUserChatService service = manager.getMultiUserChatService(archiveOwner);
        final MUCRoom room = service.getChatRoom(archiveOwner.getNode());

        if (!room.isLogEnabled()) {
            Log.debug( "Request for message archive of room '{}' that currently has message logging disabled. Returning an empty list.", room.getJID() );
            return Collections.emptyList();
        }

        if (startDate == null) {
            Log.debug( "Request for message archive of room '{}' did not specify a start date. Using EPOCH.", room.getJID() );
            startDate = new Date(0L);
        }
        if (endDate == null) {
            Log.debug( "Request for message archive of room '{}' did not specify an end date. Using the current timestamp.", room.getJID() );
            endDate = new Date();
        }

        final Long after = parseAndValidate( xmppResultSet.getAfter(), room, useStableID, "after" );
        final Long before = parseAndValidate( xmppResultSet.getBefore(), room, useStableID, "before" );
        final int maxResults = xmppResultSet.getMax() != null ? xmppResultSet.getMax() : DEFAULT_MAX;
        final boolean isPagingBackwards = xmppResultSet.isPagingBackwards();

        final List<ArchivedMessage> msgs;
        final int totalCount;
        if ( query != null && !query.isEmpty() ) {
            // When there's a 'query' element, the search needs to go through a Lucene index (which takes care of text-search).
            if (USE_OPENFIRE_TABLES.getValue()) {
                Log.debug("Using Openfire tables");
                final PaginatedMucMessageFromOpenfireLuceneQuery paginatedMucMessageLuceneQuery = new PaginatedMucMessageFromOpenfireLuceneQuery(startDate, endDate, room, with, query);
                Log.debug("Request for message archive of room '{}' resulted in the following query data: {}", room.getJID(), paginatedMucMessageLuceneQuery);
                totalCount = paginatedMucMessageLuceneQuery.getTotalCount();
                if (totalCount == 0) {
                    msgs = Collections.emptyList();
                } else {
                    msgs = paginatedMucMessageLuceneQuery.getPage(after, before, maxResults, isPagingBackwards);
                }
            } else {
                Log.debug("Using Monitoring plugin tables");
                final PaginatedMucMessageLuceneQuery paginatedMucMessageLuceneQuery = new PaginatedMucMessageLuceneQuery(startDate, endDate, room, messageOwner, with, query);
                Log.debug("Request for message archive of room '{}' resulted in the following query data: {}", room.getJID(), paginatedMucMessageLuceneQuery);
                totalCount = paginatedMucMessageLuceneQuery.getTotalCount();
                if (totalCount == 0) {
                    msgs = Collections.emptyList();
                } else {
                    msgs = paginatedMucMessageLuceneQuery.getPage(after, before, maxResults, isPagingBackwards);
                }
            }
        } else {
            // If the search does not include a text query, the results can be looked up in the database directly.
            if (USE_OPENFIRE_TABLES.getValue()) {
                Log.debug("Using Openfire tables");
                final PaginatedMucMessageFromOpenfireDatabaseQuery paginatedMucMessageFromOpenfireDatabaseQuery = new PaginatedMucMessageFromOpenfireDatabaseQuery(startDate, endDate, room, with);
                Log.debug("Request for message archive of room '{}' resulted in the following query data: {}", room.getJID(), paginatedMucMessageFromOpenfireDatabaseQuery);
                totalCount = paginatedMucMessageFromOpenfireDatabaseQuery.getTotalCount();
                if (totalCount == 0) {
                    msgs = Collections.emptyList();
                } else {
                    msgs = paginatedMucMessageFromOpenfireDatabaseQuery.getPage(after, before, maxResults, isPagingBackwards);
                }
            } else {
                Log.debug("Using Monitoring plugin tables");
                final PaginatedMucMessageDatabaseQuery paginatedMessageDatabaseQuery = new PaginatedMucMessageDatabaseQuery(startDate, endDate, room.getJID(), messageOwner, with);
                Log.debug("Request for message archive of room '{}' resulted in the following query data: {}", room.getJID(), paginatedMessageDatabaseQuery);
                totalCount = paginatedMessageDatabaseQuery.getTotalCount();
                if (totalCount == 0) {
                    msgs = Collections.emptyList();
                } else {
                    msgs = paginatedMessageDatabaseQuery.getPage(after, before, maxResults, isPagingBackwards);
                }
            }
        }

        Log.debug( "Request for message archive of room '{}' found a total of {} applicable messages. Of these, {} were actually retrieved from the database.", room.getJID(), totalCount, msgs.size() );

        xmppResultSet.setCount(totalCount);

        if ( !msgs.isEmpty() ) {
            final ArchivedMessage firstMessage = msgs.get(0);
            final ArchivedMessage lastMessage = msgs.get(msgs.size()-1);
            final String first;
            final String last;
            if ( useStableID ) {
                final String firstSid = firstMessage.getStableId(archiveOwner);
                if ( firstSid != null && !firstSid.isEmpty() ) {
                    first = firstSid;
                } else {
                    // Issue #98: Fall-back to using the database-identifier. Although not a stable-id, it at least gives the client the option to paginate.
                    first = firstMessage.getId().toString();
                }
                final String lastSid = lastMessage.getStableId(archiveOwner);
                if ( lastSid != null && !lastSid.isEmpty()) {
                    last = lastSid;
                } else {
                    last = lastMessage.getId().toString();
                }
            } else {
                first = String.valueOf(firstMessage.getId() );
                last = String.valueOf(lastMessage.getId() );
            }
            xmppResultSet.setFirst(first);
            xmppResultSet.setLast(last);

            // Check to see if there are more pages, by simulating a request for the next page.
            // When paging backwards, we need to find out if there are results 'before' the first result.
            // When paging forward, we need to find out if there are results 'after' the last result.
            final Long afterForNextPage = isPagingBackwards ? null : lastMessage.getId();
            final Long beforeForNextPage = isPagingBackwards ? firstMessage.getId() : null;
            final List<ArchivedMessage> nextPage;
            if ( query != null && !query.isEmpty() )
            {
                final PaginatedMucMessageFromOpenfireLuceneQuery paginatedMucMessageLuceneQuery = new PaginatedMucMessageFromOpenfireLuceneQuery(startDate, endDate, room, with, query);
                nextPage = paginatedMucMessageLuceneQuery.getPage(afterForNextPage, beforeForNextPage, 1, isPagingBackwards);
            }
            else
            {
                if (USE_OPENFIRE_TABLES.getValue()) {
                    final PaginatedMucMessageFromOpenfireDatabaseQuery paginatedMucMessageFromOpenfireDatabaseQuery = new PaginatedMucMessageFromOpenfireDatabaseQuery(startDate, endDate, room, with);
                    nextPage = paginatedMucMessageFromOpenfireDatabaseQuery.getPage(afterForNextPage, beforeForNextPage, 1, isPagingBackwards);
                } else {
                    final PaginatedMucMessageDatabaseQuery paginatedMessageDatabaseQuery = new PaginatedMucMessageDatabaseQuery(startDate, endDate, room.getJID(), messageOwner, with);
                    nextPage = paginatedMessageDatabaseQuery.getPage(afterForNextPage, beforeForNextPage, 1, isPagingBackwards);
                }
            }
            Log.debug("Found results for 'next page': {} (based on after: {} before: {} isPagingBackwards: {})", !nextPage.isEmpty(), afterForNextPage, beforeForNextPage, isPagingBackwards);
            xmppResultSet.setComplete(nextPage.isEmpty());
        } else {
            // Issue #112: When there are no results, then the request is definitely 'complete'.
            xmppResultSet.setComplete(true);
        }
        return msgs;
    }

    /**
     * Takes a message reference (as used in 'before' and 'after' elements of RSM) and returns the corresponding
     * database identifier for the message. This method validates that the value refers to an existing message in the
     * archive.
     *
     * When a 'null' value is passed in the value argument, a 'null' result is returned.
     *
     * @param value The reference to process (can be null).
     * @param room The room that provides the context of the message archive search (cannot be null).
     * @param useStableID Indicator if 'value' is a direct database identifier, or a stable and unique identifiers, as specified in XEP-0359.
     * @param fieldName Name of the field in which the value was transmitted (cannot be null).
     * @return A database identifier (possibly null)
     * @throws NotFoundException When a non-null value does not refer to an existing message for the supplied room.
     */
    private Long parseAndValidate( final String value, final MUCRoom room, final boolean useStableID, final String fieldName ) throws NotFoundException
    {
        if ( value == null || value.isEmpty() ) {
            return null;
        }

        final Long result;
        try {
            result = parseIdentifier(value, room, useStableID);
        } catch ( IllegalArgumentException e ) {
            // When a 'before' or 'after' element is present, but is not present in the archive, XEP-0313 specifies that a item-not-found error must be returned.
            throw new NotFoundException( "The reference '"+value+"' used in the '"+fieldName+"' RSM element is not recognized.");
        }
        // When a 'before' or 'after' element is present, but is not present in the archive, XEP-0313 specifies that a item-not-found error must be returned.
        if ( result == null || getArchivedMessage( result, room ) == null ) {
            throw new NotFoundException( "The reference '"+value+"' used in the '"+fieldName+"' RSM element is not recognized.");
        }

        return result;
    }

    /**
     * Retrieve a specific message from the database.
     *
     * @param messageId The database ID of the message.
     * @param room The room in which the message was exchanged (cannot be null).
     * @return The message, or null if no message was found.
     */
    public static ArchivedMessage getArchivedMessage( long messageId, MUCRoom room )
    {
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            connection = DbConnectionManager.getConnection();
            if (USE_OPENFIRE_TABLES.getValue()) {
                pstmt = connection.prepareStatement("SELECT sender, nickname, logTime, subject, body, stanza, messageId FROM ofMucConversationLog WHERE messageID = ? and roomID = ?");
                pstmt.setLong( 1, messageId );
                pstmt.setLong( 2, room.getID());
            } else {
                pstmt = connection.prepareStatement("SELECT fromJid, toJidResource, sentdate, fromJidResource, body, stanza, messageId FROM ofMessageArchive WHERE messageID = ? and toJid=?");
                pstmt.setLong( 1, messageId );
                pstmt.setString( 2, room.getJID().toBareJID());
            }
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                Log.warn("Database does not contain a message with ID {} from the archive of MUC room {}.", messageId, room.getJID());
                return null;
            }

            String senderJID = rs.getString(1);
            String nickname = rs.getString(2);
            Date sentDate = new Date(Long.parseLong(rs.getString(3).trim()));
            String subject = rs.getString(4);
            String body = rs.getString(5);
            String stanza = rs.getString(6);
            long id = rs.getLong(7);

            if ( rs.next() ) {
                Log.warn("Database contains more than one message with ID {} from the archive of MUC room {}.", messageId, room.getJID());
            }
            return asArchivedMessage(room.getJID(), senderJID, nickname, sentDate, subject, body, stanza, id);
        } catch (SQLException ex) {
            Log.warn("SQL failure while trying to get message with ID {} from the archive of MUC room {}.", messageId, room.getJID(), ex);
            return null;
        } catch (DocumentException ex) {
            Log.warn("Failure to parse 'stanza' value as XMPP for the message with ID {} from the archive of MUC room {}.", messageId, room.getJID(), ex);
            return null;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }
    }

    static protected ArchivedMessage asArchivedMessage(JID roomJID, String senderJID, String nickname, Date sentDate, String subject, String body, String stanza, long id) throws DocumentException {
        final JID with;
        if (nickname != null && nickname.trim().length() > 0) {
            // Recreate the sender address based on the nickname and room's JID
            with = new JID(roomJID.getNode(), roomJID.getDomain(), nickname, true);
        }
        else {
            with = roomJID;
        }

        return new ArchivedMessage(id, sentDate, ArchivedMessage.Direction.from, with, body, stanza);
    }

    static Long parseIdentifier( String value, MUCRoom room, boolean useStableID )
    {
        if ( value == null ) {
            return null;
        }

        // If this implementation can be expected to use XEP-0359 identifiers, try evaluating the value as a SSID first.
        if ( useStableID ) {
            final Long result = getMessageIdForStableId( room, value );
            if ( result != null ) {
                return result;
            }
        }

        // When not using XEP-0359 (eg: pre-MAM2) or if that didn't yield a result, see if we're processing old-style database identifiers.
        final Long result = getMessageIdForLegacyDatabaseIdentifierFormat( value );
        if ( result != null )
        {
            Log.debug( "Fallback mechanism: parse value as old database identifier: '{}'", value );
            return result;
        }

        // XEP-0359 nor old-style database ID format. Giving up.
        throw new IllegalArgumentException( "Unable to parse value '" + value + "' as a database identifier." );
    }

    public static Long getMessageIdForLegacyDatabaseIdentifierFormat( final String value ) {
        try {
            // TODO getMessageIdForStableId (the alternative to this method) ensures that a matching row is present. Do we need this method to be able to give the same guarantee (at the expense of an additional database query).
            return Long.parseLong( value );
        } catch ( NumberFormatException e ) {
            return null;
        }
    }

    static Long getMessageIdForStableId( final MUCRoom room, final String value )
    {
        Log.debug( "Looking for ID of the message with stable/unique stanza ID {}", value );

        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try
        {
            connection = DbConnectionManager.getConnection();
            if (USE_OPENFIRE_TABLES.getValue()) {
                pstmt = connection.prepareStatement( "SELECT messageId, stanza FROM ofMucConversationLog WHERE messageId IS NOT NULL AND roomID=? AND stanza LIKE ? AND stanza LIKE ?" );
                pstmt.setLong( 1, room.getID() );
            } else {
                pstmt = connection.prepareStatement("SELECT messageId, stanza FROM ofMessageArchive WHERE messageID IS NOT NULL AND toJid=? AND stanza LIKE ? AND stanza LIKE ?");
                pstmt.setString( 1, room.getJID().toBareJID());
            }

            pstmt.setString( 2, "%"+value+"%" );
            pstmt.setString( 3, "%urn:xmpp:sid:%" ); // only match stanzas if some kind of XEP-0359 namespace is used.

            rs = pstmt.executeQuery();
            while ( rs.next() ) {
                final Long messageId = rs.getLong( "messageId" );
                final String stanza = rs.getString( "stanza" );
                Log.trace( "Iterating over message with ID {}.", messageId );
                try
                {
                    final Document doc = DocumentHelper.parseText( stanza );
                    final Message message = new Message( doc.getRootElement() );
                    final String sid = StanzaIDUtil.findFirstUniqueAndStableStanzaID( message, room.getJID().toBareJID() );
                    if ( sid != null && sid.equals(value)) {
                        Log.debug( "Found stable/unique stanza ID {} in message with ID {}.", value, messageId );
                        return messageId;
                    }
                }
                catch ( DocumentException e )
                {
                    Log.warn( "An exception occurred while trying to parse stable/unique stanza ID from message with database id {}.", value );
                }
            }
        }
        catch ( SQLException e )
        {
            Log.warn( "An exception occurred while trying to determine the message ID for stanza ID '{}'.", value, e );
        }
        finally
        {
            DbConnectionManager.closeConnection( rs, pstmt, connection );
        }

        Log.debug( "Unable to find ID of the message with stable/unique stanza ID {}", value );
        return null;
    }

    @Override
    public Conversation getConversation(JID owner, JID with, Date start) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    public static Instant getDateOfFirstLog( MUCRoom room )
    {
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            connection = DbConnectionManager.getConnection();
            if (USE_OPENFIRE_TABLES.getValue()) {
                pstmt = connection.prepareStatement("SELECT MIN(logTime) FROM ofMucConversationLog WHERE roomid = ?");
                pstmt.setLong(1, room.getID());
            } else {
                pstmt = connection.prepareStatement("SELECT MIN(sentDate) FROM ofMessageArchive WHERE toJid=?");
                pstmt.setString(1, room.getJID().toBareJID());
            }
            rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Date(Long.parseLong(rs.getString(1).trim())).toInstant();
            }
        } catch (SQLException e) {
            Log.error("SQL failure while trying to find the timestamp of the earliest message for room {} in MAM-MUC: ", room.getJID(), e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }
        return null;
    }

    public static Instant getDateOfLastLog( MUCRoom room )
    {
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            connection = DbConnectionManager.getConnection();
            if (USE_OPENFIRE_TABLES.getValue()) {
                pstmt = connection.prepareStatement("SELECT MAX(logTime) FROM ofMucConversationLog WHERE roomid = ?");
                pstmt.setLong(1, room.getID());
            } else {
                pstmt = connection.prepareStatement("SELECT MAX(sentDate) FROM ofMessageArchive WHERE toJid=?");
                pstmt.setString(1, room.getJID().toBareJID());
            }
            rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Date(Long.parseLong(rs.getString(1).trim())).toInstant();
            }
        } catch (SQLException e) {
            Log.error("SQL failure while trying to find the timestamp of the latest message for room {} in MAM-MUC: ", room.getJID(), e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }
        return null;
    }
}
