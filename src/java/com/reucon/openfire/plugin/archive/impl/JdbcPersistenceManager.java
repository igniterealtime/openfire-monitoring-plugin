package com.reucon.openfire.plugin.archive.impl;

import com.reucon.openfire.plugin.archive.PersistenceManager;
import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import com.reucon.openfire.plugin.archive.model.ArchivedMessage.Direction;
import com.reucon.openfire.plugin.archive.model.Conversation;
import com.reucon.openfire.plugin.archive.model.Participant;
import com.reucon.openfire.plugin.archive.xep0059.XmppResultSet;
import org.dom4j.*;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.archive.ConversationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;

/**
 * Manages database interactions to work with 'personal archives' for messages.
 */
public class JdbcPersistenceManager implements PersistenceManager {
    private static final Logger Log = LoggerFactory.getLogger( JdbcPersistenceManager.class );
    public static final int DEFAULT_MAX = 1000;

    public static final String SELECT_MESSAGES_BY_CONVERSATION = "SELECT DISTINCT ofConversation.conversationID, ofConversation.room, "
            + "ofConversation.isExternal, ofConversation.startDate, ofConversation.lastActivity, ofConversation.messageCount, "
            + "ofConParticipant.joinedDate, ofConParticipant.leftDate, ofConParticipant.bareJID, ofConParticipant.jidResource, "
            + "ofConParticipant.nickname, ofMessageArchive.fromJID, ofMessageArchive.fromJIDResource, ofMessageArchive.toJID, "
            + "ofMessageArchive.toJIDResource, ofMessageArchive.sentDate, ofMessageArchive.body, ofMessageArchive.stanza, ofMessageArchive.messageID FROM ofConversation "
            + "INNER JOIN ofConParticipant ON ofConversation.conversationID = ofConParticipant.conversationID "
            + "INNER JOIN ofMessageArchive ON ofConParticipant.conversationID = ofMessageArchive.conversationID "
            + "WHERE ofConversation.conversationID = ? AND ofConParticipant.bareJID = ? ORDER BY ofMessageArchive.sentDate";

    public static final String SELECT_CONVERSATIONS = "SELECT "
            + "ofConversation.conversationID, ofConversation.room, ofConversation.isExternal, ofConversation.lastActivity, "
            + "ofConversation.messageCount, ofConversation.startDate, ofConParticipant.bareJID, ofConParticipant.jidResource, "
            + "ofConParticipant.nickname, ofConParticipant.bareJID AS fromJID, ofConParticipant.jidResource AS fromJIDResource, ofMessageArchive.toJID, ofMessageArchive.toJIDResource, "
            + "min(ofConParticipant.joinedDate) AS joinedDate, max(ofConParticipant.leftDate) as leftDate "
            + "FROM ofConversation "
            + "INNER JOIN ofConParticipant ON ofConversation.conversationID = ofConParticipant.conversationID "
            + "INNER JOIN (SELECT conversationID, toJID, toJIDResource FROM ofMessageArchive union all SELECT conversationID, fromJID as toJID, fromJIDResource as toJIDResource FROM ofMessageArchive) ofMessageArchive ON ofConParticipant.conversationID = ofMessageArchive.conversationID ";

    public static final String SELECT_CONVERSATIONS_GROUP_BY = " GROUP BY ofConversation.conversationID, ofConversation.room, ofConversation.isExternal, ofConversation.lastActivity, ofConversation.messageCount, ofConversation.startDate, ofConParticipant.bareJID, ofConParticipant.jidResource, ofConParticipant.nickname, ofConParticipant.bareJID, ofMessageArchive.toJID, ofMessageArchive.toJIDResource";

    public static final String COUNT_CONVERSATIONS = "SELECT COUNT(DISTINCT ofConversation.conversationID) FROM ofConversation "
            + "INNER JOIN ofConParticipant ON ofConversation.conversationID = ofConParticipant.conversationID "
            + "INNER JOIN (SELECT conversationID, toJID, toJIDResource FROM ofMessageArchive "
            + "union all "
            + "SELECT conversationID, fromJID as toJID, fromJIDResource as toJIDResource FROM ofMessageArchive) ofMessageArchive ON ofConParticipant.conversationID = ofMessageArchive.conversationID";

    public static final String CONVERSATION_ID = "ofConversation.conversationID";

    public static final String CONVERSATION_START_TIME = "ofConversation.startDate";

    public static final String CONVERSATION_END_TIME = "ofConversation.lastActivity";

    public static final String CONVERSATION_OWNER_JID = "ofConParticipant.bareJID";

    public static final String CONVERSATION_WITH_JID = "ofMessageArchive.toJID";

    public static final String SELECT_PARTICIPANTS_BY_CONVERSATION = "SELECT DISTINCT ofConversation.conversationID, "
            + "ofConversation.startDate, ofConversation.lastActivity, ofConParticipant.bareJID FROM ofConversation "
            + "INNER JOIN ofConParticipant ON ofConversation.conversationID = ofConParticipant.conversationID "
            + "INNER JOIN ofMessageArchive ON ofConParticipant.conversationID = ofMessageArchive.conversationID "
            + "WHERE ofConversation.conversationID = ? ORDER BY ofConversation.startDate";

    public Date getAuditedStartDate(Date startDate) {
        Duration maxRetrievable = ConversationManager.MAX_RETRIEVABLE.getValue();
        Date result = startDate;
        if (maxRetrievable.toDays() > 0) {
            Date now = new Date();
            Date maxRetrievableDate = new Date(now.getTime() - maxRetrievable.toMillis());
            if (startDate == null || startDate.before(maxRetrievableDate)) {
                result = maxRetrievableDate;
            }
        }
        return result;
    }

    @Override
    public Collection<Conversation> findConversations(Date startDate, Date endDate, JID owner, JID with, XmppResultSet xmppResultSet) {
        final TreeMap<Long, Conversation> conversations;
        final StringBuilder querySB;
        final StringBuilder whereSB;
        final StringBuilder limitSB;

        conversations = new TreeMap<>();

        querySB = new StringBuilder(SELECT_CONVERSATIONS);
        whereSB = new StringBuilder();
        limitSB = new StringBuilder();

        startDate = getAuditedStartDate(startDate);
        if (startDate != null) {
            appendWhere(whereSB, CONVERSATION_START_TIME, " >= ?");
        }
        if (endDate != null) {
            appendWhere(whereSB, CONVERSATION_END_TIME, " <= ?");
        }
        if (owner != null) {
            appendWhere(whereSB, CONVERSATION_OWNER_JID, " = ?");
        }
        if (with != null) {
            appendWhere(whereSB, CONVERSATION_WITH_JID, " = ?");
        }

        if (xmppResultSet != null) {
            Integer firstIndex = null;
            int max = xmppResultSet.getMax() != null ? xmppResultSet.getMax() : DEFAULT_MAX;

            xmppResultSet.setCount(countConversations(startDate, endDate, owner, with, whereSB.toString()));
            if (xmppResultSet.getIndex() != null) {
                firstIndex = xmppResultSet.getIndex();
            } else if (xmppResultSet.getAfter() != null) {
                firstIndex = countConversationsBefore(startDate, endDate, owner, with, Long.parseLong( xmppResultSet.getAfter() ), whereSB.toString());
                firstIndex += 1;
            } else if (xmppResultSet.getBefore() != null) {
                firstIndex = countConversationsBefore(startDate, endDate, owner, with, Long.parseLong( xmppResultSet.getBefore() ), whereSB.toString());
                firstIndex -= max;
                if (firstIndex < 0) {
                    firstIndex = 0;
                }
            }
            firstIndex = firstIndex != null ? firstIndex : 0;

            if (DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.sqlserver) {
                limitSB.append(" BETWEEN ").append(firstIndex+1);
                limitSB.append(" AND ").append(firstIndex+max);
            }
            else {
                limitSB.append(" LIMIT ").append(max);
                limitSB.append(" OFFSET ").append(firstIndex);
            }
            xmppResultSet.setFirstIndex(firstIndex);
        }

        if (whereSB.length() != 0) {
            querySB.append(" WHERE ").append(whereSB);
        }
        querySB.append(SELECT_CONVERSATIONS_GROUP_BY);
        if (DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.sqlserver) {
            querySB.insert(0,"SELECT * FROM (SELECT *, ROW_NUMBER() OVER (ORDER BY "+CONVERSATION_ID+") AS RowNum FROM ( ");
            querySB.append(") ofConversation ) t2 WHERE RowNum");
        }
        else {
            querySB.append(" ORDER BY ").append(CONVERSATION_ID);
        }
        querySB.append(limitSB);

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(querySB.toString());
            bindConversationParameters(startDate, endDate, owner, with, pstmt);
            rs = pstmt.executeQuery();
            Log.debug("findConversations: SELECT_CONVERSATIONS: " + pstmt.toString());
            while (rs.next()) {
                Conversation conv = extractConversation(rs);
                conversations.put(conv.getId(), conv);
            }
        } catch (SQLException sqle) {
            Log.error("Error selecting conversations", sqle);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        if (xmppResultSet != null && conversations.size() > 0) {
            xmppResultSet.setFirst( String.valueOf( conversations.firstKey() ));
            xmppResultSet.setLast( String.valueOf( conversations.lastKey() ));
        }
        return conversations.values();
    }

    private void appendWhere(StringBuilder sb, String... fragments) {
        if (sb.length() != 0) {
            sb.append(" AND ");
        }

        for (String fragment : fragments) {
            sb.append(fragment);
        }
    }

    private int countConversations(Date startDate, Date endDate, JID owner, JID with, String whereClause) {
        StringBuilder querySB;

        querySB = new StringBuilder(COUNT_CONVERSATIONS);
        if (whereClause != null && whereClause.length() != 0) {
            querySB.append(" WHERE ").append(whereClause);
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(querySB.toString());
            bindConversationParameters(startDate, endDate, owner, with, pstmt);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                return 0;
            }
        } catch (SQLException sqle) {
            Log.error("Error counting conversations", sqle);
            return 0;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    private int countConversationsBefore(Date startDate, Date endDate, JID owner, JID with, Long before, String whereClause) {
        StringBuilder querySB;

        querySB = new StringBuilder(COUNT_CONVERSATIONS);
        querySB.append(" WHERE ");
        if (whereClause != null && whereClause.length() != 0) {
            querySB.append(whereClause);
            querySB.append(" AND ");
        }
        querySB.append(CONVERSATION_ID).append(" < ?");

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            int parameterIndex;
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(querySB.toString());
            parameterIndex = bindConversationParameters(startDate, endDate, owner, with, pstmt);
            pstmt.setLong(parameterIndex, before);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                return 0;
            }
        } catch (SQLException sqle) {
            Log.error("Error counting conversations", sqle);
            return 0;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    private int bindConversationParameters(Date startDate, Date endDate, JID owner, JID with, PreparedStatement pstmt)
            throws SQLException {
        int parameterIndex = 1;

        if (startDate != null) {
            pstmt.setLong(parameterIndex++, dateToMillis(startDate));
        }
        if (endDate != null) {
            pstmt.setLong(parameterIndex++, dateToMillis(endDate));
        }
        if (owner != null) {
            pstmt.setString(parameterIndex++, owner.toString());
        }
        if (with != null) {
            pstmt.setString(parameterIndex++, with.toString());
        }
        return parameterIndex;
    }

    @Override
    public Collection<ArchivedMessage> findMessages(Date startDate, Date endDate, JID owner, JID messageOwner, JID with, String query, XmppResultSet xmppResultSet, boolean useStableID) throws DataRetrievalException {
        if ( !owner.equals(messageOwner)) {
            throw new IllegalArgumentException("A personal archive can't be queried for messages of a different owner than the archive. Supplied archive owner: " + owner + ", supplied message owner: " + messageOwner);
        }
        Log.debug( "Finding messages of owner '{}' with start date '{}', end date '{}' with '{}' and resultset '{}', useStableId '{}'.", owner, startDate, endDate, with, xmppResultSet, useStableID );

        if (startDate == null) {
            Log.debug( "Request for message archive of user '{}' did not specify a start date. Using EPOCH.", owner );
            startDate = new Date(0L);
        }
        if (endDate == null) {
            Log.debug( "Request for message archive of user '{}' did not specify an end date. Using the current timestamp.", owner );
            endDate = new Date();
        }

        // Limit history, if so configured.
        startDate = getAuditedStartDate(startDate);

        final Long after;
        final Long before;
        // TODO re-enable search-by-index.
        /* if (xmppResultSet.getIndex() != null) {
            firstIndex = xmppResultSet.getIndex();
        } */
        if (xmppResultSet.getAfter() != null) {
            if ( useStableID ) {
                after = ConversationManager.getMessageIdForStableId( owner, xmppResultSet.getAfter() );
            } else {
                after = Long.parseLong( xmppResultSet.getAfter() );
            }
        } else {
            after = null;
        }
        if (xmppResultSet.getBefore() != null) {
            if ( useStableID ) {
                before = ConversationManager.getMessageIdForStableId( owner, xmppResultSet.getBefore() );
            } else {
                before = Long.parseLong( xmppResultSet.getBefore() );
            }
        } else {
            before = null;
        }

        final int maxResults = xmppResultSet.getMax() != null ? xmppResultSet.getMax() : DEFAULT_MAX;
        final boolean isPagingBackwards = xmppResultSet.isPagingBackwards();

        final List<ArchivedMessage> msgs;
        final int totalCount;
        if ( query != null && !query.isEmpty() ) {
            final PaginatedMessageLuceneQuery paginatedMessageLuceneQuery = new PaginatedMessageLuceneQuery( startDate, endDate, owner, with, query );
            Log.debug("Request for message archive of user '{}' resulted in the following query data: {}", owner, paginatedMessageLuceneQuery);
            totalCount = paginatedMessageLuceneQuery.getTotalCount();
            if ( totalCount == 0 ) {
                msgs = Collections.emptyList();
            } else {
                msgs = paginatedMessageLuceneQuery.getPage(after, before, maxResults, isPagingBackwards);
            }
        } else {
            final PaginatedMessageDatabaseQuery paginatedMessageDatabaseQuery = new PaginatedMessageDatabaseQuery(startDate, endDate, owner, with );
            Log.debug("Request for message archive of user '{}' resulted in the following query data: {}", owner, paginatedMessageDatabaseQuery);
            totalCount = paginatedMessageDatabaseQuery.getTotalCount();
            if ( totalCount == 0 ) {
                msgs = Collections.emptyList();
            } else {
                msgs = paginatedMessageDatabaseQuery.getPage(after, before, maxResults, isPagingBackwards);
            }
        }

        Log.debug( "Request for message archive of owner '{}' found a total of {} applicable messages. Of these, {} were actually retrieved from the database.", owner, totalCount, msgs.size() );

        xmppResultSet.setCount(totalCount);

        if ( !msgs.isEmpty() ) {
            final ArchivedMessage firstMessage = msgs.get(0);
            final ArchivedMessage lastMessage = msgs.get(msgs.size()-1);
            final String first;
            final String last;
            if ( useStableID ) {
                final String firstSid = firstMessage.getStableId(owner);
                if ( firstSid != null && !firstSid.isEmpty() ) {
                    first = firstSid;
                } else {
                    // Issue #98: Fall-back to using the database-identifier. Although not a stable-id, it at least gives the client the option to paginate.
                    first = firstMessage.getId().toString();
                }
                final String lastSid = lastMessage.getStableId(owner);
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
                final PaginatedMessageLuceneQuery paginatedMessageLuceneQuery = new PaginatedMessageLuceneQuery(startDate, endDate, owner, with, query);
                nextPage = paginatedMessageLuceneQuery.getPage(afterForNextPage, beforeForNextPage, 1, isPagingBackwards);
            }
            else
            {
                final PaginatedMessageDatabaseQuery paginatedMessageDatabaseQuery = new PaginatedMessageDatabaseQuery(startDate, endDate, owner, with );
                nextPage = paginatedMessageDatabaseQuery.getPage(afterForNextPage, beforeForNextPage, 1, isPagingBackwards);
            }
            Log.debug("Found results for 'next page': {} (based on after: {} before: {} isPagingBackwards: {})", !nextPage.isEmpty(), afterForNextPage, beforeForNextPage, isPagingBackwards);
            xmppResultSet.setComplete(nextPage.isEmpty());
        } else {
            // Issue #112: When there are no results, then the request is definitely 'complete'.
            xmppResultSet.setComplete(true);
        }
        return msgs;
    }

    @Override
    public Conversation getConversation(JID owner, JID with, Date start) {
        Conversation conversation = null;
        StringBuilder querySB;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        querySB = new StringBuilder(SELECT_CONVERSATIONS);
        querySB.append(" WHERE ");
        querySB.append(CONVERSATION_OWNER_JID).append(" = ?");
        if (with != null) {
            querySB.append(" AND ");
            querySB.append(CONVERSATION_WITH_JID).append(" = ? ");
        }
        if (start != null) {
            querySB.append(" AND ");
            querySB.append(CONVERSATION_START_TIME).append(" = ? ");
        }
        querySB.append(SELECT_CONVERSATIONS_GROUP_BY);

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(querySB.toString());
            int i = 1;
            pstmt.setString(i++, owner.toString());
            if (with != null) {
                pstmt.setString(i++, with.toString());
            }
            if (start != null) {
                pstmt.setLong(i++, dateToMillis(start));
            }
            rs = pstmt.executeQuery();
            Log.debug("getConversation: SELECT_CONVERSATIONS: " + pstmt.toString());
            if (rs.next()) {
                conversation = extractConversation(rs);
            } else {
                return null;
            }

            rs.close();
            pstmt.close();

            pstmt = con.prepareStatement(SELECT_PARTICIPANTS_BY_CONVERSATION);
            pstmt.setLong(1, conversation.getId());

            rs = pstmt.executeQuery();
            Log.debug("getConversation: SELECT_PARTICIPANTS_BY_CONVERSATION: " + pstmt.toString());

            while (rs.next()) {
                for (Participant participant : extractParticipant(rs)) {
                    conversation.addParticipant(participant);
                }
            }

            rs.close();
            pstmt.close();

            pstmt = con.prepareStatement(SELECT_MESSAGES_BY_CONVERSATION);
            pstmt.setLong(1, conversation.getId());
            pstmt.setString(2, conversation.getOwnerBareJid().toString());

            rs = pstmt.executeQuery();
            Log.debug("getConversation: SELECT_MESSAGES_BY_CONVERSATION: " + pstmt.toString());

            while (rs.next()) {
                ArchivedMessage message;

                message = extractMessage(owner, rs);
                conversation.addMessage(message);
            }
        } catch (SQLException | DocumentException sqle) {
            Log.error("Error selecting conversation", sqle);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return conversation;
    }

    private JID getWithJidConversations(ResultSet rs) throws SQLException {
        String bareJid = rs.getString("bareJID");
        String fromJid = rs.getString("fromJID");
        String fromJIDResource = rs.getString("fromJIDResource");
        String toJid = rs.getString("toJID");
        String toJIDResource = rs.getString("toJIDResource");
        String room = rs.getString("room");
        String result = null;
        if (bareJid != null && fromJid != null && toJid != null) {
            if (room != null && !room.equals("")) {
                result = room;
            } else if (fromJid.contains(bareJid)) { // older versions of the database put the full jid in 'fromJID'. Using 'contains' (instead of 'equals') will also match those.
                result = toJid + ( toJIDResource == null || toJIDResource.isEmpty() ? "" : "/" + toJIDResource );
            } else {
                result = fromJid + ( fromJIDResource == null || fromJIDResource.isEmpty() ? "" : "/" + fromJIDResource );
            }
        }
        return result == null ? null : new JID(result);
    }

    private Conversation extractConversation(ResultSet rs) throws SQLException {
        final Conversation conversation;

        long id = rs.getLong("conversationID");
        Date startDate = millisToDate(rs.getLong("startDate"));
        JID owner = new JID(rs.getString("bareJID"));
        JID with = getWithJidConversations(rs);
        String subject = null;
        String thread = String.valueOf(id);

        conversation = new Conversation(startDate, owner, with, subject, thread);
        conversation.setId(id);
        return conversation;
    }

    private Collection<Participant> extractParticipant(ResultSet rs) throws SQLException {
        Collection<Participant> participants = new HashSet<>();

        Date startDate = millisToDate(rs.getLong("startDate"));
        String participantJid = rs.getString("bareJID");

        Date endDate = millisToDate(rs.getLong("lastActivity"));

        if (participantJid != null) {
            Participant participant = new Participant(startDate, new JID(participantJid));
            participant.setEnd(endDate);
            participants.add(participant);
        }

        // String withJid = getWithJid(rs);
        // if (withJid != null) {
        // Participant participant = new Participant(startDate, participantJid);
        // participant.setEnd(endDate);
        // participants.add(participant);
        // }

        return participants;
    }

    static ArchivedMessage extractMessage(final JID owner, ResultSet rs) throws SQLException, DocumentException {
        Date time = millisToDate(rs.getLong("sentDate"));
        String body = rs.getString("body");
        String stanza = rs.getString("stanza");
        String fromJid = rs.getString("fromJID");
        String fromJIDResource = rs.getString("fromJIDResource");
        String toJid = rs.getString("toJID");
        String toJIDResource = rs.getString("toJIDResource");
        Long id = rs.getLong( "messageID" );

        return asArchivedMessage( owner, fromJid, fromJIDResource, toJid, toJIDResource, time, body, stanza, id );
    }

    /**
     * Retrieve a specific message from the database.
     *
     * @param messageId The database ID of the message.
     * @param owner The owner of the archive in which the message was stored (cannot be null).
     * @return The message, or null if no message was found.
     */
    public static ArchivedMessage getArchivedMessage( long messageId, JID owner )
    {
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            connection = DbConnectionManager.getConnection();
            final String query = "SELECT ofMessageArchive.fromJID, ofMessageArchive.fromJIDResource, ofMessageArchive.toJID, ofMessageArchive.toJIDResource, ofMessageArchive.sentDate, ofMessageArchive.body, ofMessageArchive.stanza, ofMessageArchive.messageID "
                + "FROM ofMessageArchive "
                + "INNER JOIN ofConParticipant ON ofMessageArchive.conversationID = ofConParticipant.conversationID "
                + "WHERE (ofMessageArchive.stanza IS NOT NULL OR ofMessageArchive.body IS NOT NULL) "
                + "AND ofMessageArchive.messageID = ? AND ofConParticipant.bareJID = ?";

            pstmt = connection.prepareStatement( query );
            pstmt.setLong( 1, messageId );
            pstmt.setString( 2, owner.toBareJID() );
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                return null;
            }

            String fromJID = rs.getString(1);
            String fromJIDResource = rs.getString(2);
            String toJID = rs.getString(3);
            String toJIDResource = rs.getString(4);
            Date sentDate = new Date(rs.getLong(5));
            String body = rs.getString(6);
            String stanza = rs.getString(7);
            if ( stanza != null && stanza.isEmpty()) {
                stanza = null;
            }
            long id = rs.getLong(8);

            if ( rs.next() ) {
                Log.warn("Database contains more than one message with ID {} from the archive of {}.", messageId, owner);
            }

            return asArchivedMessage(owner, fromJID, fromJIDResource, toJID, toJIDResource, sentDate, body, stanza, id);
        } catch (SQLException ex) {
            Log.warn("SQL failure while trying to get message with ID {} from the archive of {}.", messageId, owner, ex);
            return null;
        } catch (DocumentException ex) {
            Log.warn("Failure to parse 'stanza' value as XMPP for the message with ID {} from the archive of {}.", messageId, owner, ex);
            return null;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }
    }

    static protected ArchivedMessage asArchivedMessage(JID owner, String fromJID, String fromJIDResource, String toJID, String toJIDResource, Date sentDate, String body, String stanza, Long id) throws DocumentException {
        final JID from = new JID(fromJID + ( fromJIDResource == null || fromJIDResource.isEmpty() ? "" : "/" + fromJIDResource ));
        final JID to = new JID(toJID + ( toJIDResource == null || toJIDResource.isEmpty() ? "" : "/" + toJIDResource ));

        final ArchivedMessage.Direction direction = ArchivedMessage.Direction.getDirection(owner, to);
        final JID with = direction == Direction.from ? from : to;
        return new ArchivedMessage(id, sentDate, direction, with, body, stanza);
    }

    private static Long dateToMillis(Date date) {
        return date == null ? null : date.getTime();
    }

    private static Date millisToDate(Long millis) {
        return millis == null ? null : new Date(millis);
    }
}
