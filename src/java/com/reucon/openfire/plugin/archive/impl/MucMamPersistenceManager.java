package com.reucon.openfire.plugin.archive.impl;

import com.reucon.openfire.plugin.archive.ArchivedMessageConsumer;
import com.reucon.openfire.plugin.archive.PersistenceManager;
import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import com.reucon.openfire.plugin.archive.model.Conversation;
import com.reucon.openfire.plugin.archive.model.Participant;
import com.reucon.openfire.plugin.archive.xep0059.XmppResultSet;
import org.dom4j.*;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.stanzaid.StanzaIDUtil;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by dwd on 25/07/16.
 */
public class MucMamPersistenceManager implements PersistenceManager {
    private final static Logger Log = LoggerFactory.getLogger( MucMamPersistenceManager.class );
    protected static final DocumentFactory docFactory = DocumentFactory.getInstance();
    private static final int DEFAULT_MAX = 100;

    @Override
    public boolean createMessage(ArchivedMessage message) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public int processAllMessages(ArchivedMessageConsumer callback) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public boolean createConversation(Conversation conversation) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public boolean updateConversationEnd(Conversation conversation) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public boolean createParticipant(Participant participant, Long conversationId) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public List<Conversation> findConversations(String[] participants, Date startDate, Date endDate) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public Collection<Conversation> findConversations(Date startDate, Date endDate, JID owner, JID with, XmppResultSet xmppResultSet) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public Collection<ArchivedMessage> findMessages(Date startDate, Date endDate, JID owner, JID with, String query, XmppResultSet xmppResultSet, boolean useStableID ) {
        Log.debug( "Finding messages of owner '{}' with start date '{}', end date '{}' with '{}', query: '{}' and resultset '{}', useStableId '{}'.", owner, startDate, endDate, with, query, xmppResultSet, useStableID );
        final MultiUserChatManager manager = XMPPServer.getInstance().getMultiUserChatManager();
        final MultiUserChatService service = manager.getMultiUserChatService(owner);
        final MUCRoom room = service.getChatRoom(owner.getNode());

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

        final Long after = parseIdentifier( xmppResultSet.getAfter(), room, useStableID );
        final Long before = parseIdentifier( xmppResultSet.getBefore(), room, useStableID );
        final int maxResults = xmppResultSet.getMax() != null ? xmppResultSet.getMax() : DEFAULT_MAX;
        final boolean isPagingBackwards = xmppResultSet.isPagingBackwards();

        final List<ArchivedMessage> msgs;
        final int totalCount;
        if ( query != null && !query.isEmpty() ) {
            final PaginatedMucMessageLuceneQuery paginatedMucMessageLuceneQuery = new PaginatedMucMessageLuceneQuery( startDate, endDate, room, with, query, after, before, maxResults, isPagingBackwards );
            Log.debug("Request for message archive of room '{}' resulted in the following query data: {}", room.getJID(), paginatedMucMessageLuceneQuery);
            msgs = paginatedMucMessageLuceneQuery.getArchivedMessages();
            totalCount = paginatedMucMessageLuceneQuery.getTotalCountOfLastQuery();
        } else {
            final PaginatedMucMessageDatabaseQuery paginatedMucMessageDatabaseQuery = new PaginatedMucMessageDatabaseQuery(startDate, endDate, room, with, after, before, maxResults, isPagingBackwards );
            Log.debug("Request for message archive of room '{}' resulted in the following query data: {}", room.getJID(), paginatedMucMessageDatabaseQuery);
            msgs = getArchivedMessages(paginatedMucMessageDatabaseQuery, room.getJID() );
            totalCount = getTotalCount(paginatedMucMessageDatabaseQuery);
        }

        Log.debug( "Request for message archive of room '{}' found a total of {} applicable messages. Of these, {} were actually retrieved from the database.", room.getJID(), totalCount, msgs.size() );

        xmppResultSet.setCount(totalCount);
        xmppResultSet.setComplete( msgs.size() <= maxResults );

        if (msgs.size() > 0) {
            String first = null;
            String last = null;
            if ( useStableID ) {
                final UUID firstSid = msgs.get( 0 ).getStableId();
                if ( firstSid != null ) {
                    first = firstSid.toString();
                }
                final UUID lastSid = msgs.get(msgs.size()-1).getStableId();
                if ( lastSid != null ) {
                    last = lastSid.toString();
                }
            } else {
                first = String.valueOf( msgs.get(0) );
                last = String.valueOf( msgs.get(msgs.size()-1));
            }
            xmppResultSet.setFirst(first);
            if (msgs.size() > 1) {
                xmppResultSet.setLast(last);
            }
        }
        return msgs;
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
            pstmt = connection.prepareStatement( "SELECT sender, nickname, logTime, subject, body, stanza, messageId FROM ofMucConversationLog WHERE messageID = ? and roomID = ?");
            pstmt.setLong( 1, messageId );
            pstmt.setLong( 2, room.getID());
            rs = pstmt.executeQuery();
            if (!rs.next()) {
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
                Log.warn("Database contains more than one message with ID {} from the archive of MUC room {}.", messageId, room);
            }
            return asArchivedMessage(room.getJID(), senderJID, nickname, sentDate, subject, body, stanza, id);
        } catch (SQLException ex) {
            Log.warn("SQL failure while trying to get message with ID {} from the archive of MUC room {}.", messageId, room, ex);
            return null;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }
    }

    protected List<ArchivedMessage> getArchivedMessages( PaginatedMucMessageDatabaseQuery paginatedMucMessageDatabaseQuery, JID roomJID )
    {
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        List<ArchivedMessage> msgs = new LinkedList<>();
        try {
            connection = DbConnectionManager.getConnection();
            pstmt = paginatedMucMessageDatabaseQuery.prepareStatement(connection, false );
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String senderJID = rs.getString(1);
                String nickname = rs.getString(2);
                Date sentDate = new Date(Long.parseLong(rs.getString(3).trim()));
                String subject = rs.getString(4);
                String body = rs.getString(5);
                String stanza = rs.getString(6);
                long id = rs.getLong(7);

                msgs.add( asArchivedMessage(roomJID, senderJID, nickname, sentDate, subject, body, stanza, id) );
            }
        } catch (SQLException e) {
            Log.error("SQL failure during MAM-MUC: ", e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }

        return msgs;
    }

    static protected ArchivedMessage asArchivedMessage(JID roomJID, String senderJID, String nickname, Date sentDate, String subject, String body, String stanza, long id)
    {
        if (stanza == null) {
            Message message = new Message();
            message.setType(Message.Type.groupchat);
            message.setSubject(subject);
            message.setBody(body);
            // Set the sender of the message
            if (nickname != null && nickname.trim().length() > 0) {
                // Recreate the sender address based on the nickname and room's JID
                message.setFrom(new JID(roomJID.getNode(), roomJID.getDomain(), nickname, true));
            }
            else {
                // Set the room as the sender of the message
                message.setFrom(roomJID);
            }
            stanza = message.toString();
        }

        UUID sid;
        try
        {
            if ( !JiveGlobals.getBooleanProperty( "conversation.OF-1804.disable", false ) )
            {
                // Prior to OF-1804 (Openfire 4.4.0), the stanza was logged with a formatter applied.
                // This causes message formatting to be modified (notably, new lines could be altered).
                // This workaround restores the original body text, that was stored in a different column.
                final int pos1 = stanza.indexOf( "<body>" );
                final int pos2 = stanza.indexOf( "</body>" );

                if ( pos1 > -1 && pos2 > -1 )
                {
                    // Add the body value to a proper XML element, so that the strings get XML encoded (eg: ampersand is escaped).
                    final Element bodyEl = docFactory.createDocument().addElement("body");
                    bodyEl.setText(body);
                    stanza = stanza.substring( 0, pos1 ) + bodyEl.asXML() + stanza.substring( pos2 + 7 );
                }
            }
            final Document doc = DocumentHelper.parseText( stanza );
            final Message message = new Message( doc.getRootElement() );
            sid = StanzaIDUtil.parseUniqueAndStableStanzaID( message, roomJID.toBareJID() );
        } catch ( Exception e ) {
            Log.warn( "An exception occurred while parsing message with ID {}", id, e );
            sid = null;
        }

        final ArchivedMessage archivedMessage = new ArchivedMessage(sentDate, ArchivedMessage.Direction.from, null, null, sid);
        archivedMessage.setStanza(stanza);
        archivedMessage.setId(id);
        return archivedMessage;
    }
    protected int getTotalCount( PaginatedMucMessageDatabaseQuery paginatedMucMessageDatabaseQuery )
    {
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        int totalCount = 0;
        try {
            connection = DbConnectionManager.getConnection();
            pstmt = paginatedMucMessageDatabaseQuery.prepareStatement(connection, true );
            rs = pstmt.executeQuery();
            if (rs.next()) {
                totalCount = rs.getInt(1);
            }
        } catch (SQLException e) {
            Log.error("SQL failure while counting messages in MAM-MUC: ", e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }
        return totalCount;
    }

    static Long parseIdentifier( String value, MUCRoom room, boolean useStableID )
    {
        if ( value == null ) {
            return null;
        }
        if ( useStableID ) {
            return getMessageIdForStableId( room, value );
        }

        return Long.parseLong( value );
    }

    static Long getMessageIdForStableId( final MUCRoom room, final String value )
    {
        Log.debug( "Looking for ID of the message with stable/unique stanza ID {}", value );

        final UUID uuid;
        try {
            uuid = UUID.fromString( value );
        } catch ( IllegalArgumentException e ) {
            Log.debug( "Client presented a value that's not a UUID: '{}'", value );

            try {
                Log.debug( "Fallback mechanism: parse value as old database identifier: '{}'", value );
                return Long.parseLong( value );
            } catch ( NumberFormatException e1 ) {
                Log.debug( "Fallback failed: value cannot be parsed as the old database identifier." );
                throw e; // throwing the original exception, as we'd originally expected an UUID here.
            }
        }
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try
        {

            connection = DbConnectionManager.getConnection();
            pstmt = connection.prepareStatement( "SELECT messageId, stanza FROM ofMucConversationLog WHERE messageId IS NOT NULL AND roomID=? AND stanza LIKE ?" );
            pstmt.setLong( 1, room.getID() );
            pstmt.setString( 2, "%"+uuid.toString()+"%" );

            rs = pstmt.executeQuery();
            while ( rs.next() ) {
                final Long messageId = rs.getLong( "messageId" );
                final String stanza = rs.getString( "stanza" );
                Log.trace( "Iterating over message with ID {}.", messageId );
                try
                {
                    final Document doc = DocumentHelper.parseText( stanza );
                    final Message message = new Message( doc.getRootElement() );
                    final UUID sid = StanzaIDUtil.parseUniqueAndStableStanzaID( message, room.getJID().toBareJID() );
                    if ( sid != null ) {
                        Log.debug( "Found stable/unique stanza ID {} in message with ID {}.", uuid, messageId );
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
    public Collection<Conversation> getActiveConversations(int conversationTimeout) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public List<Conversation> getConversations(Collection<Long> conversationIds) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public Conversation getConversation(JID owner, JID with, Date start) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public Conversation getConversation(Long conversationId) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }
}
