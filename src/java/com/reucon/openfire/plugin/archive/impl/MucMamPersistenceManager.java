package com.reucon.openfire.plugin.archive.impl;

import com.reucon.openfire.plugin.archive.ArchivedMessageConsumer;
import com.reucon.openfire.plugin.archive.PersistenceManager;
import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import com.reucon.openfire.plugin.archive.model.Conversation;
import com.reucon.openfire.plugin.archive.model.Participant;
import com.reucon.openfire.plugin.archive.xep0059.XmppResultSet;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.stanzaid.StanzaIDUtil;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
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
    private static final String LOAD_HISTORY =
            "SELECT sender, nickname, logTime, subject, body, stanza, messageId FROM ofMucConversationLog " +
                    "WHERE messageId IS NOT NULL AND logTime>? AND logTime <= ? AND roomID=? AND (nickname IS NOT NULL OR subject IS NOT NULL) ";
    private static final String WHERE_SENDER = " AND sender = ? ";
    private static final String WHERE_AFTER = " AND messageId > ? ";
    private static final String WHERE_BEFORE = " AND messageId < ? ";
    private static final String ORDER_BY = " ORDER BY logTime";
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
    public Collection<Conversation> findConversations(Date startDate, Date endDate, String owner, String with, XmppResultSet xmppResultSet) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public Collection<ArchivedMessage> findMessages(Date startDate, Date endDate, String owner, String with, XmppResultSet xmppResultSet, boolean useStableID ) {
        Log.debug( "Finding messages of owner '{}' with start date '{}', end date '{}' with '{}' and resultset '{}', useStableId '{}'.", owner, startDate, endDate, with, xmppResultSet, useStableID );
        JID mucRoom = new JID(owner);
        MultiUserChatManager manager = XMPPServer.getInstance().getMultiUserChatManager();
        MultiUserChatService service =  manager.getMultiUserChatService(mucRoom);
        MUCRoom room = service.getChatRoom(mucRoom.getNode());
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        List<ArchivedMessage>msgs = new LinkedList<>();
        // If logging isn't enabled, do nothing.
        if (!room.isLogEnabled()) return msgs;
        if (startDate == null) {
            startDate = new Date(0L);
        }
        if (endDate == null) {
            endDate = new Date();
        }
        int max = xmppResultSet.getMax() != null ? xmppResultSet.getMax() : DEFAULT_MAX;
        with = null; // TODO: Suppress this, since we don't yet have requestor information for access control.
        try {
            connection = DbConnectionManager.getConnection();
            StringBuilder sql = new StringBuilder(LOAD_HISTORY);
            if (with != null) {
                sql.append(WHERE_SENDER);
            }
            if (xmppResultSet.getAfter() != null) {
                sql.append(WHERE_AFTER);
            }
            if (xmppResultSet.getBefore() != null) {
                sql.append(WHERE_BEFORE);
            }
            sql.append(ORDER_BY);
            pstmt = connection.prepareStatement(sql.toString());
            pstmt.setString(1, StringUtils.dateToMillis(startDate));
            pstmt.setString(2, StringUtils.dateToMillis(endDate));
            pstmt.setLong(3, room.getID());
            int pos = 3;
            if (with != null) {
                pstmt.setString(++pos, with);
            }
            if (xmppResultSet.getAfter() != null) {
                final Long needle;
                if ( useStableID ) {
                    needle = getMessageIdForStableId( room, xmppResultSet.getAfter() );
                } else {
                    needle = Long.parseLong( xmppResultSet.getAfter() );
                }
                pstmt.setLong(++pos, needle );
            }
            if (xmppResultSet.getBefore() != null) {
                final Long needle;
                if ( useStableID ) {
                    needle = getMessageIdForStableId( room, xmppResultSet.getBefore() );
                } else {
                    needle = Long.parseLong( xmppResultSet.getBefore() );
                }
                pstmt.setLong(++pos, needle );
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String senderJID = rs.getString(1);
                String nickname = rs.getString(2);
                Date sentDate = new Date(Long.parseLong(rs.getString(3).trim()));
                String subject = rs.getString(4);
                String body = rs.getString(5);
                String stanza = rs.getString(6);
                long id = rs.getLong(7);
                if (stanza == null) {
                    Message message = new Message();
                    message.setType(Message.Type.groupchat);
                    message.setSubject(subject);
                    message.setBody(body);
                    // Set the sender of the message
                    if (nickname != null && nickname.trim().length() > 0) {
                        JID roomJID = room.getRole().getRoleAddress();
                        // Recreate the sender address based on the nickname and room's JID
                        message.setFrom(new JID(roomJID.getNode(), roomJID.getDomain(), nickname, true));
                    }
                    else {
                        // Set the room as the sender of the message
                        message.setFrom(room.getRole().getRoleAddress());
                    }
                    stanza = message.toString();
                }

                UUID sid;
                try
                {
                    final Document doc = DocumentHelper.parseText( stanza );
                    final Message message = new Message( doc.getRootElement() );
                    sid = StanzaIDUtil.parseUniqueAndStableStanzaID( message, room.getJID().toBareJID() );
                } catch ( Exception e ) {
                    Log.warn( "An exception occurred while parsing message with ID {}", id, e );
                    sid = null;
                }

                final ArchivedMessage archivedMessage = new ArchivedMessage(sentDate, ArchivedMessage.Direction.from, null, null, sid);
                archivedMessage.setStanza(stanza);
                archivedMessage.setId(id);

                msgs.add(archivedMessage);
            }
        } catch (SQLException e) {
            Log.error("SQL failure during MAM-MUC: ", e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }
        // TODO - Not great, really should be done by suitable LIMIT stuff.
        // Would need to reverse ordering in some cases and then reverse results.
        boolean pagingBackwards = xmppResultSet.isPagingBackwards();
        if ( pagingBackwards ) {
            Collections.reverse(msgs);
        }
        boolean complete = true;
        xmppResultSet.setCount(msgs.size());
        while (msgs.size() > max) {
            msgs.remove(msgs.size() - 1);
            complete = false;
        }
        if ( pagingBackwards ) {
            Collections.reverse(msgs);
        }
        xmppResultSet.setComplete(complete);
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
    public Conversation getConversation(String ownerJid, String withJid, Date start) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }

    @Override
    public Conversation getConversation(Long conversationId) {
        throw new UnsupportedOperationException("MAM-MUC cannot perform this operation");
    }
}
