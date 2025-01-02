/*
 * Copyright (C) 2008 Jive Software, 2025 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.database.JiveID;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.IOException;
import java.util.*;

/**
 * Represents an IM conversation between people. A conversation encompasses a series of messages sent back and forth. It may cover a single topic
 * or several. The start of a conversation occurs when the first message between users is sent. It ends when either:
 * <ul>
 * <li>No messages are sent between the users for a certain period of time (default of 10 minutes). The default value can be overridden by setting the
 * Openfire property <tt>conversation.idleTime</tt>.</li>
 * <li>The total conversation time reaches a maximum value (default of 60 minutes). The default value can be overridden by setting the Openfire
 * property <tt>conversation.maxTime</tt>. When the max time has been reached and additional messages are sent between the users, a new conversation
 * will simply be started.</li>
 * </ul>
 * <p/>
 * Each conversation has a start time, date of the last message, and count of the messages in the conversation. Conversations are specially marked if
 * one of the participants is on an external server. If archiving is enabled, the actual messages in the conversation can be retrieved.
 * 
 * @author Matt Tucker
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JiveID(50)
public class Conversation {

    private static final Logger Log = LoggerFactory.getLogger(Conversation.class);

    @XmlElement
    private long conversationID = -1;
    @XmlElementWrapper
    private Map<String, UserParticipations> participants;
    @XmlElement
    private boolean external;
    @XmlElement
    private Date startDate;
    @XmlElement
    private Date lastActivity;
    @XmlElement
    private int messageCount;

    /**
     * Unique identifier of the room where the group conversion is taking place. For one-to-one chats there is no room
     * so this variable will be null.
     *
     * This identifier differs from the JID provided in {@link #room}. When a room gets destroyed, and is recreated, it
     * <em>can</em> be recreated using the same JID. The numeric identifier however, will be unique.
     */
    @XmlElement
    private Long roomID = null;

    /**
     * Room where the group conversion is taking place. For one-to-one chats there is no room so this variable will be null.
     *
     * This identifier differs from the ID provided in {@link #roomID}. When a room gets destroyed, and is recreated, it
     * <em>can</em> be recreated using the same JID. The numeric identifier however, will be unique.
     */
    @XmlElement
    @XmlJavaTypeAdapter(XmlSerializer.JidAdapter.class)
    private JID room;

    /**
     * Do not use this constructor. It only exists for serialization purposes.
     */
    public Conversation() {
    }

    public Conversation(Map<String, UserParticipations> participants, boolean external, Date startDate) {
        this.participants = participants;
        this.external = external;
        this.startDate = startDate;
        this.lastActivity = startDate;
    }

    public Conversation(Long roomID, JID room, Map<String, UserParticipations> participants, boolean external, Date startDate) {
        this.roomID = roomID;
        this.room = room;
        this.participants = participants;
        this.external = external;
        this.startDate = startDate;
        this.lastActivity = startDate;
    }

    public Conversation(Long roomID, JID room, boolean external, Date startDate, Date lastActivity, int messageCount, Map<String, UserParticipations> participants) {
        this.roomID = roomID;
        this.room = room;
        this.external = external;
        this.startDate = startDate;
        this.lastActivity = lastActivity;
        this.messageCount = messageCount;
        this.participants = participants;
    }

    /**
     * Sets the ID of the room where the group conversation is taking place. One-to-one chats should not have a room ID
     * (or have a value that is <tt>null</tt>).
     *
     * This identifier differs from the JID provided in {@link #room}. When a room gets destroyed, and is recreated, it
     * <em>can</em> be recreated using the same JID. The numeric identifier however, will be unique.
     *
     * @param roomID the room ID, or -1 if this is a one-to-one chat.
     */
    public void setRoomID(Long roomID) {
        this.roomID = roomID;
    }

    /**
     * Returns the ID of the room where the group conversation is taking place. For one-to-one chats, this method will
     * return null, as there is no room.
     *
     * This identifier differs from the JID provided in {@link #room}. When a room gets destroyed, and is recreated, it
     * <em>can</em> be recreated using the same JID. The numeric identifier however, will be unique.
     *
     * @return the room ID, null if this is a one-to-one chat.
     */
    public Long getRoomID() {
        return roomID;
    }

    /**
     * Updates the conversationID value of this instance.
     *
     * Should only be set by ConversationDAO.
     *
     * @param conversationID the new ID.
     */
    void setConversationID(long conversationID) {
        this.conversationID = conversationID;
    }

    /**
     * Returns the unique ID of the conversation. A unique ID is only meaningful when conversation archiving is enabled. Therefore, this method
     * returns <tt>-1</tt> if archiving is not turned on.
     * 
     * @return the unique ID of the conversation, or <tt>-1</tt> if conversation archiving is not enabled.
     */
    public long getConversationID() {
        return conversationID;
    }

    /**
     * Returns the JID of the room where the group conversation took place. If the conversation was a one-to-one chat then a <tt>null</tt> value is
     * returned.
     *
     * This identifier differs from the ID provided in {@link #roomID}. When a room gets destroyed, and is recreated, it
     * <em>can</em> be recreated using the same JID. The numeric identifier however, will be unique.
     *
     * @return the JID of room or null if this was a one-to-one chat.
     */
    public JID getRoom() {
        return room;
    }

    /**
     * Returns the conversation participants.
     * 
     * @return the two conversation participants. Returned JIDs are full JIDs.
     */
    public Collection<JID> getParticipants() {
        List<JID> users = new ArrayList<>();
        for (String key : participants.keySet()) {
            users.add(new JID(key));
        }
        return users;
    }

    /**
     * Returns the participations of the specified user (full JID) in this conversation. Each participation will hold the time when the user joined
     * and left the conversation and the nickname if the room happened in a room.
     * 
     * @param user
     *            the full JID of the user.
     * @return the participations of the specified user (full JID) in this conversation.
     */
    public Collection<ConversationParticipation> getParticipations(JID user) {
        UserParticipations userParticipations = participants.get(user.toString());
        if (userParticipations == null) {
            return Collections.emptyList();
        }
        return userParticipations.getParticipations();
    }

    /**
     * Returns true if one of the conversation participants is on an external server.
     * 
     * @return true if one of the conversation participants is on an external server.
     */
    public boolean isExternal() {
        return external;
    }

    /**
     * Returns the starting timestamp of the conversation.
     * 
     * @return the start date.
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Returns the timestamp the last message was receieved.
     * 
     * @return the last activity.
     */
    public Date getLastActivity() {
        return lastActivity;
    }

    /**
     * Returns the number of messages that make up the conversation.
     *
     * Note that this count includes private messages exchanged in a chat room, which might no be retrievable by all
     * participants of the chat room.
     *
     * @return the message count.
     */
    public int getMessageCount() {
        return messageCount;
    }

    /**
     * Returns the archived messages in the conversation. If message archiving is not enabled, this method will always return an empty collection.
     * This method will only return messages that have already been batch-archived to the database; in other words, it does not provide a real-time
     * view of new messages.
     * 
     * @return the archived messages in the conversation.
     */
    public List<ArchivedMessage> getMessages(@Nonnull final ConversationManager conversationManager) {
        return ConversationDAO.getMessages(this, conversationManager);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("Conversation [").append(conversationID).append("]");
        if (room != null) {
            buf.append(" in room").append(room);
        }
        buf.append(" between ").append(participants);
        buf.append(". started ").append(JiveGlobals.formatDateTime(startDate));
        buf.append(", last active ").append(JiveGlobals.formatDateTime(lastActivity));
        buf.append(". Total messages: ").append(messageCount);
        return buf.toString();
    }

    /**
     * Called when a new message for the conversation is received. Each time a new message is received, the last activity date will be updated and the
     * message count incremented.
     * 
     * @param entity
     *            JID of the entity that sent the message.
     * @param date
     *            the date the message was sent.
     */
    synchronized void messageReceived(JID entity, Date date) {
        lastActivity = date;
        messageCount++; // TODO shouldn't this be persisted in the database?
    }

    synchronized void participantJoined(ConversationManager conversationManager, JID user, String nickname, long timestamp) {
        // Add the sender of the message as a participant of this conversation. If the sender
        // was already a participant then he/she will appear just once. Rooms are never considered
        // as participants
        UserParticipations userParticipations = participants.get(user.toString());
        if (userParticipations == null) {
            userParticipations = new UserParticipations(true);
            participants.put(user.toString(), userParticipations);
        } else {
            // Get last known participation and check that the user has finished it
            ConversationParticipation lastParticipation = userParticipations.getRecentParticipation();
            if (lastParticipation != null && lastParticipation.getLeft() == null) {
                Log.warn("Found user that never left a previous conversation: " + user);
                lastParticipation.participationEnded(new Date(timestamp));
                // Queue storeage of updated participation information
                conversationManager.queueParticipantLeft(this, user, lastParticipation);
            }
        }
        ConversationParticipation newParticipation = new ConversationParticipation(new Date(timestamp), nickname);
        // Add element to the beginning of the list
        userParticipations.addParticipation(newParticipation);
        // If archiving is enabled, insert the conversation into the database (if not persistent yet).
        if (conversationManager.isMetadataArchivingEnabled()) {
            try {
                if (conversationID == -1) {
                    // Save new conversation to the database
                    ConversationDAO.insertIntoDb(this);
                } else {
                    // Store new participation information
                    ConversationDAO.insertIntoDb(conversationID, user, nickname, timestamp);
                }
            } catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    synchronized void participantLeft(ConversationManager conversationManager, JID user, long timestamp) {
        // Get the list of participations of the specified user
        UserParticipations userParticipations = participants.get(user.toString());
        if (userParticipations == null) {
            Log.warn("Found user that left a conversation but never started it: " + user);
        } else {
            // Get last known participation and check that the user has not finished it
            ConversationParticipation currentParticipation = userParticipations.getRecentParticipation();
            if (currentParticipation == null || currentParticipation.getLeft() != null) {
                Log.warn("Found user that left a conversation but never started it: " + user);
            } else {
                currentParticipation.participationEnded(new Date(timestamp));
                // Queue storeage of updated participation information
                conversationManager.queueParticipantLeft(this, user, currentParticipation);
            }
        }
    }

    /**
     * Notification message implicating that conversation has finished so remaining participants should be marked that they left the conversation.
     * 
     * @param nowDate
     *            the date when the conversation was finished
     */
    void conversationEnded(ConversationManager conversationManager, Date nowDate) {
        for (Map.Entry<String, UserParticipations> entry : participants.entrySet()) {
            ConversationParticipation currentParticipation = entry.getValue().getRecentParticipation();
            if (currentParticipation.getLeft() == null) {
                currentParticipation.participationEnded(nowDate);
                // Queue storage of updated participation information
                conversationManager.queueParticipantLeft(this, new JID(entry.getKey()), currentParticipation);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Conversation that = (Conversation) o;
        return conversationID == that.conversationID && external == that.external && messageCount == that.messageCount && Objects.equals(participants, that.participants) && Objects.equals(startDate, that.startDate) && Objects.equals(lastActivity, that.lastActivity) && Objects.equals(roomID, that.roomID) && Objects.equals(room, that.room);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationID, participants, external, startDate, lastActivity, messageCount, roomID, room);
    }

    /**
     * Convert the conversation to an XML representation.
     *
     * @return XML representation of the conversation.
     * @throws IOException On any issue that occurs when marshalling this instance to XML.
     */
    public String toXml() throws IOException {
        return XmlSerializer.getInstance().marshall(this);
    }

    /**
     * Create a new conversation object based on the XML representation.
     *
     * @param xmlString The XML representation.
     * @return A newly instantiated conversation object containing state as included in the XML representation.
     * @throws IOException On any issue that occurs when unmarshalling XML to an instance of Conversation.
     */
    public static Conversation fromXml(final String xmlString) throws IOException {
        return (Conversation) XmlSerializer.getInstance().unmarshall(xmlString);
    }
}
