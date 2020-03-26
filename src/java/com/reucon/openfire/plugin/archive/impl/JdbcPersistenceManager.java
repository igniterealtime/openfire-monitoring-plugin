package com.reucon.openfire.plugin.archive.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.archive.ConversationManager;
import org.jivesoftware.openfire.stanzaid.StanzaIDUtil;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import com.reucon.openfire.plugin.archive.ArchivedMessageConsumer;
import com.reucon.openfire.plugin.archive.PersistenceManager;
import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import com.reucon.openfire.plugin.archive.model.ArchivedMessage.Direction;
import com.reucon.openfire.plugin.archive.model.Conversation;
import com.reucon.openfire.plugin.archive.model.Participant;
import com.reucon.openfire.plugin.archive.xep0059.XmppResultSet;
import org.xmpp.packet.Message;

/**
 * Manages database persistence.
 */
public class JdbcPersistenceManager implements PersistenceManager {
    private static final Logger Log = LoggerFactory.getLogger( JdbcPersistenceManager.class );
    public static final int DEFAULT_MAX = 1000;

    public static final String SELECT_MESSAGES_BY_CONVERSATION = "SELECT DISTINCT " + "ofConversation.conversationID, " + "ofConversation.room, "
            + "ofConversation.isExternal, " + "ofConversation.startDate, " + "ofConversation.lastActivity, " + "ofConversation.messageCount, "
            + "ofConParticipant.joinedDate, " + "ofConParticipant.leftDate, " + "ofConParticipant.bareJID, " + "ofConParticipant.jidResource, "
            + "ofConParticipant.nickname, " + "ofMessageArchive.fromJID, " + "ofMessageArchive.toJID, " + "ofMessageArchive.sentDate, "
            + "ofMessageArchive.body, ofMessageArchive.stanza, ofMessageArchive.messageID " + "FROM ofConversation "
            + "INNER JOIN ofConParticipant ON ofConversation.conversationID = ofConParticipant.conversationID "
            + "INNER JOIN ofMessageArchive ON ofConParticipant.conversationID = ofMessageArchive.conversationID "
            + "WHERE ofConversation.conversationID = ? AND ofConParticipant.bareJID = ? ORDER BY ofMessageArchive.sentDate";

		public static final String SELECT_CONVERSATIONS = "SELECT DISTINCT "
            + "ofConversation.conversationID, " + " ofConversation.room, " + "ofConversation.isExternal, "+ "ofConversation.lastActivity, "
            + "ofConversation.messageCount, " + "ofConversation.startDate, " + "ofConParticipant.bareJID, " + "ofConParticipant.jidResource,"
            + "ofConParticipant.nickname, " + "ofConParticipant.bareJID as fromJID, " + "ofMessageArchive.toJID, "
            + "ofConParticipant.joinedDate, ofConParticipant.leftDate  "
            + "FROM ofConversation "
						+ "INNER JOIN ( "
							+ "SELECT ofConParticipant.conversationID, ofConParticipant.bareJID, ofConParticipant.jidResource, ofConParticipant.nickname, "
							+ "min(ofConParticipant.joinedDate) AS joinedDate, max(ofConParticipant.leftDate) as leftDate "
							+ "FROM ofConParticipant "
							+ "GROUP BY ofConParticipant.conversationID, ofConParticipant.bareJID, ofConParticipant.jidResource, ofConParticipant.nickname "
						+ ") ofConParticipant ON ofConversation.conversationID = ofConParticipant.conversationID "
            + "INNER JOIN ( "
							+ "SELECT ofMessageArchive.conversationID, ofMessageArchive.toJID "
							+ "FROM ofMessageArchive "
							+ "union all "
							+ "SELECT ofMessageArchive.conversationID, ofMessageArchive.fromJID as toJID "
							+ "FROM ofMessageArchive "
						+ ") ofMessageArchive ON ofConversation.conversationID = ofMessageArchive.conversationID ";

    public static final String COUNT_CONVERSATIONS = "SELECT COUNT(ofConversation.conversationID) FROM ofConversation ";
		
		public static final String WHERE_CONVERSATION_OWNER_JID = "EXISTS (SELECT * FROM ofConParticipant t1 WHERE t1.conversationID = ofConversation.conversationID AND t1.bareJID = ? ) ";
		
		public static final String WHERE_CONVERSATION_WITH_JID = "EXISTS (SELECT * FROM ofMessageArchive t1 WHERE t1.conversationID = ofConversation.conversationID AND (t1.toJID = ? OR t1.fromJID = ? )) ";

    public static final String CONVERSATION_ID = "ofConversation.conversationID";

    public static final String CONVERSATION_START_TIME = "ofConversation.startDate";

    public static final String CONVERSATION_END_TIME = "ofConversation.lastActivity";

    public static final String CONVERSATION_OWNER_JID = "ofConParticipant.bareJID";

    public static final String MESSAGE_ID = "ofMessageArchive.messageID";

    public static final String MESSAGE_SENT_DATE = "ofMessageArchive.sentDate";

    public static final String MESSAGE_TO_JID = "ofMessageArchive.toJID";

    public static final String MESSAGE_FROM_JID = "ofMessageArchive.fromJID";

    public static final String SELECT_ACTIVE_CONVERSATIONS = "SELECT DISTINCT " + "ofConversation.conversationID, " + "ofConversation.room, "
            + "ofConversation.isExternal, " + "ofConversation.startDate, " + "ofConversation.lastActivity, " + "ofConversation.messageCount, "
            + "ofConParticipant.joinedDate, " + "ofConParticipant.leftDate, " + "ofConParticipant.bareJID, " + "ofConParticipant.jidResource, "
            + "ofConParticipant.nickname, " + "ofMessageArchive.fromJID, " + "ofMessageArchive.toJID, " + "ofMessageArchive.sentDate, "
            + "ofMessageArchive.body " + "FROM ofConversation "
            + "INNER JOIN ofConParticipant ON ofConversation.conversationID = ofConParticipant.conversationID "
            + "INNER JOIN ofMessageArchive ON ofConParticipant.conversationID = ofMessageArchive.conversationID "
            + "WHERE ofConversation.lastActivity > ?";

    public static final String SELECT_ACTIVE_CONVERSATIONS_ORACLE = "select SUBSET.conversationID,"
                + "SUBSET.room,"
                + "SUBSET.isExternal,"
                + "SUBSET.startDate,"
                + "SUBSET.lastActivity,"
                + "SUBSET.messageCount,"
                + "SUBSET.joinedDate,"
                + "SUBSET.leftDate,"
                + "SUBSET.bareJID,"
                + "SUBSET.jidResource,"
                + "SUBSET.nickname,"
                + "SUBSET.fromJID,"
                + "SUBSET.toJID,"
                + "SUBSET.sentDate,"
                + "MAR.body from ("
                + "SELECT DISTINCT ofConversation.conversationID as conversationID,"
                + "ofConversation.room as room,"
                + "ofConversation.isExternal as isExternal,"
                + "ofConversation.startDate as startDate,"
                + "ofConversation.lastActivity as lastActivity,"
                + "ofConversation.messageCount as messageCount,"
                + "ofConParticipant.joinedDate as joinedDate,"
                + "ofConParticipant.leftDate as leftDate,"
                + "ofConParticipant.bareJID as bareJID,"
                + "ofConParticipant.jidResource as jidResource,"
                + "ofConParticipant.nickname as nickname,"
                + "ofMessageArchive.fromJID as fromJID,"
                + "ofMessageArchive.toJID as toJID,"
                + "ofMessageArchive.sentDate as sentDate,"
                + "ofMessageArchive.MESSAGEID as msgId "
                + "FROM ofConversation "
                + "INNER JOIN ofConParticipant ON ofConversation.conversationID = ofConParticipant.conversationID "
                + "INNER JOIN ofMessageArchive ON ofConParticipant.conversationID = ofMessageArchive.conversationID "
                + "where ofConversation.lastActivity > ? ) SUBSET "
                + "INNER JOIN ofMessageArchive MAR ON MAR.conversationID = SUBSET.conversationID "
                + "where MAR.MESSAGEID = SUBSET.msgId "
                + "and MAR.sentDate = SUBSET.sentDate "
                + "and MAR.fromJID = SUBSET.fromJID "
                + "and MAR.toJID = SUBSET.toJID";
    // public static final String SELECT_ACTIVE_CONVERSATIONS =
    // "SELECT c.conversationId,c.startTime,c.endTime,c.ownerJid,c.ownerResource,withJid,c.withResource,"
    // + " c.subject,c.thread "
    // + "FROM archiveConversations AS c WHERE c.endTime > ?";

    public static final String SELECT_PARTICIPANTS_BY_CONVERSATION = "SELECT DISTINCT " + "ofConversation.conversationID, "
            + "ofConversation.startDate, " + "ofConversation.lastActivity, " + "ofConParticipant.bareJID " + "FROM ofConversation "
            + "INNER JOIN ofConParticipant ON ofConversation.conversationID = ofConParticipant.conversationID "
            + "INNER JOIN ofMessageArchive ON ofConParticipant.conversationID = ofMessageArchive.conversationID "
            + "WHERE ofConversation.conversationID = ? ORDER BY ofConversation.startDate";

    // public static final String SELECT_PARTICIPANTS_BY_CONVERSATION =
    // "SELECT participantId,startTime,endTime,jid FROM archiveParticipants WHERE conversationId =? ORDER BY startTime";
     public static final String SELECT_MESSAGES = "SELECT DISTINCT " + "ofMessageArchive.fromJID, "
            + "ofMessageArchive.toJID, " + "ofMessageArchive.sentDate, " + "ofMessageArchive.stanza, "
            + "ofMessageArchive.messageID, " + "ofConParticipant.bareJID "
            + "FROM ofMessageArchive "
            + "INNER JOIN ofConParticipant ON ofMessageArchive.conversationID = ofConParticipant.conversationID "
            + "WHERE (ofMessageArchive.stanza IS NOT NULL OR ofMessageArchive.body IS NOT NULL) ";

    public static final String SELECT_MESSAGE_ORACLE = "SELECT "
            + "ofMessageArchive.fromJID, "
            + "ofMessageArchive.toJID, "
            + "ofMessageArchive.sentDate, "
            + "ofMessageArchive.stanza, "
            + "ofMessageArchive.messageID "
            + "FROM ofMessageArchive WHERE 1 = 1";
    public static final String SELECT_CONVERSATIONS_BY_OWNER = "SELECT DISTINCT ofConParticipant.conversationID FROM ofConParticipant WHERE "
            + CONVERSATION_OWNER_JID
            + " = ?";

     public static final String COUNT_MESSAGES = "SELECT COUNT(DISTINCT ofMessageArchive.messageID) "
            + "FROM ofMessageArchive "
            + "INNER JOIN ofConParticipant ON ofMessageArchive.conversationID = ofConParticipant.conversationID "
            + "WHERE (ofMessageArchive.stanza IS NOT NULL OR ofMessageArchive.body IS NOT NULL) ";

    @Override
    public boolean createMessage(ArchivedMessage message) {
        /* read only */
        return false;
    }

    @Override
    public int processAllMessages(ArchivedMessageConsumer callback) {
        return 0;
    }

    @Override
    public boolean createConversation(Conversation conversation) {
        /* read only */
        return false;
    }

    @Override
    public boolean updateConversationEnd(Conversation conversation) {
        /* read only */
        return false;
    }

    @Override
    public boolean createParticipant(Participant participant, Long conversationId) {
        return false;
    }

    @Override
    public List<Conversation> findConversations(String[] participants, Date startDate, Date endDate) {
        final List<Conversation> conversations = new ArrayList<Conversation>();
        return conversations;
    }

    public Date getAuditedStartDate(Date startDate) {
        long maxRetrievable = JiveGlobals.getIntProperty("conversation.maxRetrievable", ConversationManager.DEFAULT_MAX_RETRIEVABLE)
                * JiveConstants.DAY;
        Date result = startDate;
        if (maxRetrievable > 0) {
            Date now = new Date();
            Date maxRetrievableDate = new Date(now.getTime() - maxRetrievable);
            if (startDate == null) {
                result = maxRetrievableDate;
            } else if (startDate.before(maxRetrievableDate)) {
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
						appendWhere(whereSB, WHERE_CONVERSATION_OWNER_JID);
        }
        if (with != null) {
						appendWhere(whereSB, WHERE_CONVERSATION_WITH_JID);
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
            } else if (xmppResultSet.isPagingBackwards()){
                int messagesCount = xmppResultSet.getCount();
                firstIndex = messagesCount;
                firstIndex -= max;

                if (max > messagesCount) max = messagesCount;
                if (firstIndex < 0) firstIndex = 0;
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
						
						if (owner != null) {
								appendWhere(querySB, CONVERSATION_OWNER_JID, " = ?");
						}
						if (with != null) {
								appendWhere(querySB, MESSAGE_TO_JID, " = ?");
						}
        }
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
						int parameterIndex;
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(querySB.toString());
            parameterIndex = bindConversationParameters(startDate, endDate, owner, with, pstmt);
						if (owner != null) {
								pstmt.setString(parameterIndex++, owner.toString());
						}
						if (with != null) {
								pstmt.setString(parameterIndex++, with.toString());
						}
            rs = pstmt.executeQuery();
            //Log.debug("findConversations: SELECT_CONVERSATIONS: " + pstmt.toString());
						Log.debug("findConversations: SELECT_CONVERSATIONS: " + rs.getStatement().toString());
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
						Log.debug("countConversations: COUNT_CONVERSATIONS: " + rs.getStatement().toString());
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
						Log.debug("countConversationsBefore: COUNT_CONVERSATIONS: " + rs.getStatement().toString());
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
						// Add twice due to OR operator
            pstmt.setString(parameterIndex++, with.toString());
						pstmt.setString(parameterIndex++, with.toString());
        }
        return parameterIndex;
    }

    @Override
    public Collection<ArchivedMessage> findMessages(Date startDate, Date endDate, JID owner, JID with, String query, XmppResultSet xmppResultSet, boolean useStableID) {

        Log.debug( "Finding messages of owner '{}' with start date '{}', end date '{}' with '{}' and resultset '{}', useStableId '{}'.", owner, startDate, endDate, with, xmppResultSet, useStableID );
        if ( query != null ) {
            Log.warn( "Query provided in search request, but not supported by implementation. Query is ignored!" );
        }
        final boolean isOracleDB = isOracleDB();

        final StringBuilder querySB;
        final StringBuilder whereSB;
        final StringBuilder limitSB;

        final TreeMap<Long, ArchivedMessage> archivedMessages = new TreeMap<Long, ArchivedMessage>();

        querySB = new StringBuilder( isOracleDB ? SELECT_MESSAGE_ORACLE : SELECT_MESSAGES );
        whereSB = new StringBuilder();
        limitSB = new StringBuilder();

        // Ignore legacy messages
        appendWhere(whereSB, MESSAGE_ID, " IS NOT NULL ");

        startDate = getAuditedStartDate(startDate);
        if (startDate != null) {
            appendWhere(whereSB, MESSAGE_SENT_DATE, " >= ?");
        }
        if (endDate != null) {
            appendWhere(whereSB, MESSAGE_SENT_DATE, " <= ?");
        }
        if (owner != null) {
            if( isOracleDB ) {
                appendWhere( whereSB, "ofMessageArchive.conversationID in ( ", SELECT_CONVERSATIONS_BY_OWNER, " )" );
            }
            else {
                appendWhere(whereSB, CONVERSATION_OWNER_JID, " = ?");
            }
        }
        if(with != null) {
            appendWhere(whereSB, "( ", MESSAGE_TO_JID, " = ? OR ", MESSAGE_FROM_JID, " = ? )");
        }
        if (whereSB.length() != 0) {
            querySB.append(" AND ").append(whereSB);
        }

        if (DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.sqlserver) {
            querySB.insert(0,"SELECT * FROM (SELECT *, ROW_NUMBER() OVER (ORDER BY "+MESSAGE_SENT_DATE+") AS RowNum FROM ( ");
            querySB.append(") ofMessageArchive ) t2 WHERE RowNum");
        }
        else {
            querySB.append(" ORDER BY ").append(MESSAGE_SENT_DATE);
        }

        if (xmppResultSet != null) {
            Integer firstIndex = null;
            int max = xmppResultSet.getMax() != null ? xmppResultSet.getMax() : DEFAULT_MAX;
            int count = countMessages(startDate, endDate, owner, with, whereSB.toString());
            boolean reverse = false;

            xmppResultSet.setCount(count);
            if (xmppResultSet.getIndex() != null) {
                firstIndex = xmppResultSet.getIndex();
            } else if (xmppResultSet.getAfter() != null) {
                final Long needle;
                if ( useStableID ) {
                    needle = ConversationManager.getMessageIdForStableId( owner, xmppResultSet.getAfter() );
                } else {
                    needle = Long.parseLong( xmppResultSet.getAfter() );
                }
                firstIndex = countMessagesBefore(startDate, endDate, owner, with, needle, whereSB.toString());
                firstIndex += 1;
            } else if (xmppResultSet.getBefore() != null) {
                final Long needle;
                if ( useStableID ) {
                    needle = ConversationManager.getMessageIdForStableId( owner, xmppResultSet.getBefore() );
                } else {
                    needle = Long.parseLong( xmppResultSet.getBefore() );
                }

                int messagesBeforeCount = countMessagesBefore(startDate, endDate, owner, with, needle, whereSB.toString());
                firstIndex = messagesBeforeCount;
                firstIndex -= max;

                // Reduce result limit to number of results before (if less than a page remaining)
                if(messagesBeforeCount < max) {
                    max = messagesBeforeCount;
                }

                reverse = true;
                if (firstIndex < 0) {
                    firstIndex = 0;
                }
            }
            else if (xmppResultSet.isPagingBackwards()){
                int messagesCount = countMessages(startDate, endDate, owner, with, whereSB.toString());
                firstIndex = messagesCount;
                firstIndex -= max;

                if (max > messagesCount) max = messagesCount;
                if (firstIndex < 0) firstIndex = 0;

                reverse = true;
            }
            firstIndex = firstIndex != null ? firstIndex : 0;

            if (DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.sqlserver) {
                limitSB.append(" BETWEEN ").append(firstIndex+1);
                limitSB.append(" AND ").append(firstIndex+max);
            }
            else if( isOracleDB() ) {
                try {
                    final Statement statement = DbConnectionManager.getConnection().createStatement();
                    final ResultSet resultSet = statement.executeQuery( "select VERSION from PRODUCT_COMPONENT_VERSION P where P.PRODUCT like 'Oracle Database%'" );
                    resultSet.next();
                    final String versionString = resultSet.getString( "VERSION" );
                    final String[] versionParts = versionString.split( "\\." );
                    final int majorVersion = Integer.parseInt( versionParts[ 0 ] );
                    final int minorVersion = Integer.parseInt( versionParts[ 1 ] );

                    if( ( majorVersion == 12 && minorVersion >= 1 ) || majorVersion > 12 ) {
                        limitSB.append(" LIMIT ").append(max);
                        limitSB.append(" OFFSET ").append(firstIndex);
                    }
                    else {
                        querySB.insert( 0, "SELECT * FROM ( " );
                        limitSB.append( " ) WHERE rownum BETWEEN " )
                        .append( firstIndex + 1 )
                        .append( " AND " )
                        .append( firstIndex + max );
                    }
                } catch( SQLException e ) {
                    Log.warn( "Unable to determine oracle database version using fallback", e );
                    querySB.insert( 0, "SELECT * FROM ( " );
                    limitSB.append( " ) WHERE rownum BETWEEN " )
                    .append( firstIndex + 1 )
                    .append( " AND " )
                    .append( firstIndex + max );
                }
            }
            else {
                limitSB.append(" LIMIT ").append(max);
                limitSB.append(" OFFSET ").append(firstIndex);
            }
            xmppResultSet.setFirstIndex(firstIndex);

            if(isLastPage(firstIndex, count, max, reverse)) {
                xmppResultSet.setComplete(true);
            }
        }

        querySB.append(limitSB);

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(querySB.toString());
            bindMessageParameters(startDate, endDate, owner, with, pstmt);

            rs = pstmt.executeQuery();
            Log.debug("findMessages: SELECT_MESSAGES: " + rs.getStatement().toString());
            while(rs.next()) {
                // TODO Can we replace this with #extractMessage?
                final String stanza = rs.getString( "stanza" );
                final long id = rs.getLong( "messageID" );
                final Date time = millisToDate(rs.getLong("sentDate"));

                UUID sid = null;
                if ( stanza != null && !stanza.isEmpty() ) {
                    try {
                        final Document doc = DocumentHelper.parseText( stanza );
                        final Message message = new Message( doc.getRootElement() );
                        sid = StanzaIDUtil.parseUniqueAndStableStanzaID( message, owner.toBareJID() );
                    } catch ( Exception e ) {
                        Log.warn( "An exception occurred while parsing message with ID {}", id, e );
                        sid = null;
                    }
                }

                ArchivedMessage archivedMessage = new ArchivedMessage(time, null, null, null, sid);
                archivedMessage.setId(id);
                archivedMessage.setStanza(stanza);

                archivedMessages.put(archivedMessage.getId(), archivedMessage);
            }
        } catch(SQLException sqle) {
            Log.error("Error selecting conversations", sqle);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        if (xmppResultSet != null && archivedMessages.size() > 0) {
            xmppResultSet.setFirst(String.valueOf( archivedMessages.firstKey() ));
            xmppResultSet.setLast(String.valueOf( archivedMessages.lastKey() ));
        }

        return archivedMessages.values();
    }

    private boolean isOracleDB()
    {
        return DbConnectionManager.getDatabaseType() == DbConnectionManager.DatabaseType.oracle;
    }

    private Integer countMessages(Date startDate, Date endDate,
            JID owner, JID with, String whereClause) {

        StringBuilder querySB;

        querySB = new StringBuilder(COUNT_MESSAGES);
        if (whereClause != null && whereClause.length() != 0) {
            querySB.append(" AND ").append(whereClause);
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(querySB.toString());
            bindMessageParameters(startDate, endDate, owner, with, pstmt);
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

    private Integer countMessagesBefore(Date startDate, Date endDate,
            JID owner, JID with, Long before, String whereClause) {

        StringBuilder querySB;

        querySB = new StringBuilder(COUNT_MESSAGES);
        querySB.append(" AND ");
        if (whereClause != null && whereClause.length() != 0) {
            querySB.append(whereClause);
            querySB.append(" AND ");
        }
        querySB.append(MESSAGE_ID).append(" < ?");

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            int parameterIndex;
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(querySB.toString());
            parameterIndex = bindMessageParameters(startDate, endDate, owner, with, pstmt);
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

    private int bindMessageParameters(Date startDate, Date endDate,
            JID owner, JID with, PreparedStatement pstmt) throws SQLException {
        int parameterIndex = 1;

        if (startDate != null) {
            pstmt.setLong(parameterIndex++, dateToMillis(startDate));
        }
        if (endDate != null) {
            pstmt.setLong(parameterIndex++, dateToMillis(endDate));
        }
        if (owner != null) {
            pstmt.setString(parameterIndex++, owner.toBareJID());
        }
        if (with != null) {
            // Add twice due to OR operator
            pstmt.setString(parameterIndex++, with.toString());
            pstmt.setString(parameterIndex++, with.toString());
        }
        return parameterIndex;
    }

    @Override
    public Collection<Conversation> getActiveConversations(int conversationTimeout) {
        final Collection<Conversation> conversations;
        final long now = System.currentTimeMillis();

        conversations = new ArrayList<>();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement( isOracleDB() ? SELECT_ACTIVE_CONVERSATIONS_ORACLE : SELECT_ACTIVE_CONVERSATIONS );

            pstmt.setLong(1, now - conversationTimeout * 60L * 1000L);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                conversations.add(extractConversation(rs));
            }
        } catch (SQLException sqle) {
            Log.error("Error selecting conversations", sqle);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return conversations;
    }

    @Override
    public List<Conversation> getConversations(Collection<Long> conversationIds) {
        final List<Conversation> conversations;
        final StringBuilder querySB;

        conversations = new ArrayList<>();
        if (conversationIds.isEmpty()) {
            return conversations;
        }

        querySB = new StringBuilder(SELECT_CONVERSATIONS);
        querySB.append(" WHERE ");
        querySB.append(CONVERSATION_ID);
        querySB.append(" IN ( ");
        for (int i = 0; i < conversationIds.size(); i++) {
            if (i == 0) {
                querySB.append("?");
            } else {
                querySB.append(",?");
            }
        }
        querySB.append(" )");
        querySB.append(" ORDER BY ").append(CONVERSATION_END_TIME);

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(querySB.toString());

            int i = 0;
            for (Long id : conversationIds) {
                pstmt.setLong(++i, id);
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                conversations.add(extractConversation(rs));
            }
        } catch (SQLException sqle) {
            Log.error("Error selecting conversations", sqle);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return conversations;
    }

    @Override
    public Conversation getConversation(JID owner, JID with, Date start) {
        return getConversation(null, owner, with, start);
    }

    @Override
    public Conversation getConversation(Long conversationId) {
        return getConversation(conversationId, null, null, null);
    }

    private Conversation getConversation(Long conversationId, JID owner, JID with, Date start) {
        Conversation conversation = null;
        StringBuilder querySB;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        querySB = new StringBuilder(SELECT_CONVERSATIONS);
        querySB.append(" WHERE ");
        if (conversationId != null) {
            querySB.append(CONVERSATION_ID).append(" = ? ");
        } else {
						querySB.append(WHERE_CONVERSATION_OWNER_JID);
            appendWhere(querySB, CONVERSATION_OWNER_JID, " = ?");
						if (with != null) {
                querySB.append(" AND ");
								querySB.append(WHERE_CONVERSATION_WITH_JID);
                querySB.append(" AND ");
								querySB.append(MESSAGE_TO_JID).append(" = ? ");
            }
            if (start != null) {
                querySB.append(" AND ");
                querySB.append(CONVERSATION_START_TIME).append(" = ? ");
            }
						
        }

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(querySB.toString());
            int i = 1;

            if (conversationId != null) {
                pstmt.setLong(1, conversationId);
            } else {
                pstmt.setString(i++, owner.toString());
								pstmt.setString(i++, owner.toString());
                if (with != null) {
										// Add twice due to OR operator
                    pstmt.setString(i++, with.toString());
										pstmt.setString(i++, with.toString());
										// MESSAGE_TO_JID
										pstmt.setString(i++, with.toString());
                }
                if (start != null) {
                    pstmt.setLong(i++, dateToMillis(start));
                }
            }
            rs = pstmt.executeQuery();
            Log.debug("getConversation: SELECT_CONVERSATIONS: " + rs.getStatement().toString());
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
            Log.debug("getConversation: SELECT_PARTICIPANTS_BY_CONVERSATION: " + rs.getStatement().toString());

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
            Log.debug("getConversation: SELECT_MESSAGES_BY_CONVERSATION: " + rs.getStatement().toString());

            while (rs.next()) {
								ArchivedMessage message;

                message = extractMessage(rs);
                message.setConversation(conversation);
                conversation.addMessage(message);
            }
        } catch (SQLException sqle) {
            Log.error("Error selecting conversation", sqle);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return conversation;
    }

    private JID getWithJidConversations(ResultSet rs) throws SQLException {
        String bareJid = rs.getString("bareJID");
        String fromJid = rs.getString("fromJID");
        String toJid = rs.getString("toJID");
        String room = rs.getString("room");
        String result = null;
        if (bareJid != null && fromJid != null && toJid != null) {
            if (room != null && !room.equals("")) {
                result = room;
            } else if (fromJid.contains(bareJid)) {
                result = toJid;
            } else {
                result = fromJid;
            }
        }
        return result == null ? null : new JID(result);
    }

    private Direction getDirection(ResultSet rs) throws SQLException {
        Direction direction = null;
        String bareJid = rs.getString("bareJID");
        String fromJid = rs.getString("fromJID");
        String toJid = rs.getString("toJID");
        if (bareJid != null && fromJid != null && toJid != null) {
            if (bareJid.equals(fromJid)) {
                /*
                 * if message from me to 'with' then it is to the 'with' participant
                 */
                direction = Direction.to;
            } else {
                /*
                 * if message to me from 'with' then it is from the 'with' participant
                 */
                direction = Direction.from;
            }
        }
        return direction;
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

    private ArchivedMessage extractMessage(ResultSet rs) throws SQLException {
				final ArchivedMessage message;
        Date time = millisToDate(rs.getLong("sentDate"));
        Direction direction = getDirection(rs);
        String type = null;
        String subject = null;
        String body = rs.getString("body");
        String stanza = rs.getString("stanza");
        String bareJid = rs.getString("bareJID");
        Long id = rs.getLong( "messageID" );
        JID with = null;

        if (Direction.from == direction) {
            with = new JID(rs.getString("fromJID"));
        }

        UUID sid = null;
        if ( stanza != null && !stanza.isEmpty() ) {
            try {
                final Document doc = DocumentHelper.parseText( stanza );
                final Message m = new Message( doc.getRootElement() );
                sid = StanzaIDUtil.parseUniqueAndStableStanzaID( m, new JID( bareJid ).toBareJID() );
            } catch ( Exception e ) {
                Log.warn( "An exception occurred while parsing message with ID {}", id, e );
                sid = null;
            }
        }

        message = new ArchivedMessage(time, direction, null, with, sid);
        // message.setId(id);
        // message.setSubject(subject);
        message.setBody(body);
        return message;
    }

    private Long dateToMillis(Date date) {
        return date == null ? null : date.getTime();
    }

    private Date millisToDate(Long millis) {
        return millis == null ? null : new Date(millis);
    }

    /**
     * Determines whether a result page is the last of a set.
     *
     * @param firstItemIndex index (in whole set) of first item in page.
     * @param resultCount total number of results in set.
     * @param pageSize number of results in a page.
     * @param reverse whether paging is being performed in reverse (back to front)
     * @return whether results are from last page.
     */
    private boolean isLastPage(int firstItemIndex, int resultCount, int pageSize, boolean reverse) {

        if(reverse) {
            // Index of first item in last page always 0 when reverse
            if(firstItemIndex == 0) {
                return true;
            }
        } else {
            if((firstItemIndex + pageSize) >= resultCount) {
                return true;
            }
        }

        return false;
    }
}
