/*
 * Copyright (C) 2021 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.user.UserNameManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A database access object for instances of {@link Conversation}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ConversationDAO {

    private static final String INSERT_CONVERSATION = "INSERT INTO ofConversation(conversationID, room, isExternal, startDate, "
        + "lastActivity, messageCount) VALUES (?,?,?,?,?,0)";
    private static final String INSERT_PARTICIPANT = "INSERT INTO ofConParticipant(conversationID, joinedDate, bareJID, jidResource, nickname) "
        + "VALUES (?,?,?,?,?)";
    private static final String LOAD_CONVERSATION = "SELECT room, isExternal, startDate, lastActivity, messageCount "
        + "FROM ofConversation WHERE conversationID=?";
    private static final String LOAD_PARTICIPANTS = "SELECT bareJID, jidResource, nickname, joinedDate, leftDate FROM ofConParticipant "
        + "WHERE conversationID=? ORDER BY joinedDate";
    private static final String LOAD_MESSAGES = "SELECT fromJID, fromJIDResource, toJID, toJIDResource, sentDate, body, stanza, isPMforJID FROM ofMessageArchive WHERE conversationID=? "
        + "ORDER BY sentDate";

    private static final Logger Log = LoggerFactory.getLogger(ConversationDAO.class);

    /**
     * Constructs a new one-to-one conversation.
     *
     * @param conversationManager
     *            the ConversationManager.
     * @param users
     *            the two participants in the conversation.
     * @param external
     *            true if the conversation includes a user on another server.
     * @param startDate
     *            the starting date of the conversation.
     */
    public static Conversation createConversation(ConversationManager conversationManager, Collection<JID> users, boolean external, Date startDate) {
        if (users.size() != 2) {
            throw new IllegalArgumentException("Illegal number of participants: " + users.size());
        }
        final Map<String, UserParticipations> participants = new HashMap<>(2);
        // Ensure that we're use the full JID of each participant.
        for (final JID user : users) {
            final UserParticipations userParticipations = new UserParticipations(false);
            userParticipations.addParticipation(new ConversationParticipation(startDate));
            participants.put(user.toString(), userParticipations);
        }

        final Conversation conversation = new Conversation(participants, external, startDate);

        // If archiving is enabled, insert the conversation into the database.
        if (conversationManager.isMetadataArchivingEnabled()) {
            try {
                insertIntoDb(conversation);
            } catch (Exception e) {
                Log.error("Unable to persist a conversation that was just created: {}", conversation, e);
            }
        }
        return conversation;
    }

    /**
     * Constructs a new group chat conversation that is taking place in a room.
     *
     * @param conversationManager
     *            the ConversationManager.
     * @param room
     *            the JID of the room where the conversation is taking place.
     * @param external
     *            true if the conversation includes a user on another server.
     * @param startDate
     *            the starting date of the conversation.
     */
    public static Conversation createConversation(ConversationManager conversationManager, JID room, boolean external, Date startDate) {
        final Map<String, UserParticipations> participants = new ConcurrentHashMap<>();
        // Add list of existing room occupants as participants of this conversation
        MUCRoom mucRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(room).getChatRoom(room.getNode());
        if (mucRoom != null) {
            for (MUCRole role : mucRoom.getOccupants()) {
                UserParticipations userParticipations = new UserParticipations(true);
                userParticipations.addParticipation(new ConversationParticipation(startDate, role.getNickname()));
                participants.put(role.getUserAddress().toString(), userParticipations);
            }
        }

        final Conversation conversation = new Conversation(room, participants, external, startDate);

        // If archiving is enabled, insert the conversation into the database.
        if (conversationManager.isMetadataArchivingEnabled()) {
            try {
                insertIntoDb(conversation);
            } catch (Exception e) {
                Log.error("Unable to persist a conversation that was just created: {}", conversation, e);
            }
        }

        return conversation;
    }

    /**
     * Loads a conversation from the database.
     *

     * @param conversationID
     *            the ID of the conversation.
     * @throws NotFoundException
     *             if the conversation can't be loaded.
     */
    public static Conversation loadConversation(long conversationID) throws NotFoundException {
        return loadFromDb(conversationID);
    }

    /**
     * Returns the archived messages in the conversation. If message archiving is not enabled, this method will always return an empty collection.
     * This method will only return messages that have already been batch-archived to the database; in other words, it does not provide a real-time
     * view of new messages.
     *
     * @return the archived messages in the conversation.
     */
    public static List<ArchivedMessage> getMessages(@Nonnull final Conversation conversation, @Nonnull ConversationManager conversationManager) {
        if (conversation.getRoom() == null && !conversationManager.isMessageArchivingEnabled()) {
            return Collections.emptyList();
        } else if (conversation.getRoom() != null && !conversationManager.isRoomArchivingEnabled()) {
            return Collections.emptyList();
        }

        List<ArchivedMessage> messages = new ArrayList<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_MESSAGES);
            pstmt.setLong(1, conversation.getConversationID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                JID fromJID = new JID(rs.getString(1));
                String fromJIDResource = rs.getString(2);
                if (fromJIDResource != null && !"".equals(fromJIDResource)) {
                    fromJID = new JID(rs.getString(1) + "/" + fromJIDResource);
                }
                JID toJID = new JID(rs.getString(3));
                String toJIDResource = rs.getString(4);
                if (toJIDResource != null && !"".equals(toJIDResource)) {
                    toJID = new JID(rs.getString(3) + "/" + toJIDResource);
                }
                Date date = new Date(rs.getLong(5));
                String body = DbConnectionManager.getLargeTextField(rs, 6);

                String stanza = DbConnectionManager.getLargeTextField(rs, 7);

                final String isPMforJIDValue = rs.getString(8);
                final JID isPMforJID = isPMforJIDValue == null ? null : new JID(isPMforJIDValue);

                messages.add(new ArchivedMessage(conversation.getConversationID(), fromJID, toJID, date, body, stanza,false, isPMforJID));
            }
        } catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        // Add messages of users joining or leaving the group chat conversation
        if (conversation.getRoom() != null) {
            for (JID user : conversation.getParticipants()) {
                boolean anonymous = false;
                String name;
                try {
                    name = UserNameManager.getUserName(user);
                } catch (UserNotFoundException e) {
                    name = user.toBareJID();
                    anonymous = true;
                }
                for (ConversationParticipation participation : conversation.getParticipations(user)) {
                    if (participation.getJoined() == null) {
                        Log.warn("Found muc participant with no join date in conversation: " + conversation.getConversationID());
                        continue;
                    }
                    JID jid = new JID(conversation.getRoom() + "/" + participation.getNickname());
                    String joinBody;
                    String leftBody;
                    if (anonymous) {
                        joinBody = LocaleUtils.getLocalizedString("muc.conversation.joined.anonymous", MonitoringConstants.NAME,
                            Collections.singletonList(participation.getNickname()));
                        leftBody = LocaleUtils.getLocalizedString("muc.conversation.left.anonymous", MonitoringConstants.NAME,
                            Collections.singletonList(participation.getNickname()));
                    } else {
                        joinBody = LocaleUtils.getLocalizedString("muc.conversation.joined", MonitoringConstants.NAME,
                            Arrays.asList(participation.getNickname(), name));
                        leftBody = LocaleUtils.getLocalizedString("muc.conversation.left", MonitoringConstants.NAME,
                            Arrays.asList(participation.getNickname(), name));
                    }
                    messages.add(new ArchivedMessage(conversation.getConversationID(), user, jid, participation.getJoined(), joinBody, true, null));
                    if (participation.getLeft() != null) {
                        messages.add(new ArchivedMessage(conversation.getConversationID(), user, jid, participation.getLeft(), leftBody, true, null));
                    }
                }
            }
            // Sort messages by sent date
            messages.sort(Comparator.comparing(ArchivedMessage::getSentDate));
        }
        return messages;
    }

    private static Conversation loadFromDb(final long conversationID) throws NotFoundException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_CONVERSATION);
            pstmt.setLong(1, conversationID);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new NotFoundException("Conversation not found: " + conversationID);
            }
            final JID room = rs.getString(1) == null ? null : new JID(rs.getString(1));
            final boolean external = rs.getInt(2) == 1;
            final Date startDate = new Date(rs.getLong(3));
            final Date lastActivity = new Date(rs.getLong(4));
            final int messageCount = rs.getInt(5);
            rs.close();
            pstmt.close();

            final Map<String, UserParticipations> participants = new ConcurrentHashMap<>();
            pstmt = con.prepareStatement(LOAD_PARTICIPANTS);
            pstmt.setLong(1, conversationID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                // Rebuild full JID of participant
                String baredJID = rs.getString(1);
                String resource = rs.getString(2);
                JID fullJID = new JID("".equals(resource) ? baredJID : baredJID + "/" + resource);
                // Rebuild joined and left time
                ConversationParticipation participation = new ConversationParticipation(new Date(rs.getLong(4)), rs.getString(3));
                if (rs.getLong(5) > 0) {
                    participation.participationEnded(new Date(rs.getLong(5)));
                }
                // Store participation data
                UserParticipations userParticipations = participants.get(fullJID.toString());
                if (userParticipations == null) {
                    userParticipations = new UserParticipations(room != null);
                    participants.put(fullJID.toString(), userParticipations);
                }
                userParticipations.addParticipation(participation);
            }

            final Conversation result = new Conversation(room, external, startDate, lastActivity, messageCount, participants);
            result.setConversationID(conversationID);
            return result;
        } catch (SQLException sqle) {
            Log.error("An exception occurred while trying to load conversation {} form database.", conversationID, sqle);
            return null;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    /**
     * Inserts a new conversation into the database.
     *
     * @throws SQLException
     *             if an error occurs inserting the conversation.
     */
    static void insertIntoDb(@Nonnull final Conversation conversation) throws SQLException {
        conversation.setConversationID(SequenceManager.nextID(conversation));
        Connection con = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            PreparedStatement pstmt = con.prepareStatement(INSERT_CONVERSATION);
            pstmt.setLong(1, conversation.getConversationID());
            pstmt.setString(2, conversation.getRoom() == null ? null : conversation.getRoom().toString());
            pstmt.setInt(3, (conversation.isExternal() ? 1 : 0));
            pstmt.setLong(4, conversation.getStartDate().getTime());
            pstmt.setLong(5, conversation.getLastActivity().getTime());
            pstmt.executeUpdate();
            pstmt.close();

            pstmt = con.prepareStatement(INSERT_PARTICIPANT);
            for (JID user : conversation.getParticipants()) {
                for (ConversationParticipation participation : conversation.getParticipations(user)) {
                    pstmt.setLong(1, conversation.getConversationID());
                    pstmt.setLong(2, participation.getJoined().getTime());
                    pstmt.setString(3, user.toBareJID());
                    pstmt.setString(4, user.getResource() == null ? "" : user.getResource());
                    pstmt.setString(5, participation.getNickname());
                    pstmt.executeUpdate();
                }
            }
            pstmt.close();
        } catch (SQLException sqle) {
            abortTransaction = true;
            throw sqle;
        } finally {
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    /**
     * Adds a new conversation participant into the database.
     *
     * @param participant
     *            the full JID of the participant.
     * @param nickname
     *            nickname of the user in the room.
     * @param joined
     *            timestamp when user joined the conversation.
     * @throws SQLException
     *             if an error occurs inserting the conversation.
     */
    static void insertIntoDb(long conversationID, JID participant, String nickname, long joined) throws SQLException {
        Connection con = null;
        try {
            con = DbConnectionManager.getConnection();
            PreparedStatement pstmt = con.prepareStatement(INSERT_PARTICIPANT);
            pstmt.setLong(1, conversationID);
            pstmt.setLong(2, joined);
            pstmt.setString(3, participant.toBareJID());
            pstmt.setString(4, participant.getResource() == null ? "" : participant.getResource());
            pstmt.setString(5, nickname);
            pstmt.executeUpdate();
            pstmt.close();
        } finally {
            DbConnectionManager.closeConnection(con);
        }
    }
}
