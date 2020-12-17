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

import org.dom4j.DocumentHelper;
import org.jivesoftware.database.JiveID;
import org.jivesoftware.database.SequenceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.Date;

/**
 * Represents an archived message.
 *
 * @author Matt Tucker
 */
@JiveID(604)
public class ArchivedMessage {

    private static final Logger Log = LoggerFactory.getLogger( ArchivedMessage.class );

    static {
        // Instantiate a sequence manager to ensure that a block size larger than the default value of '1' is used.
        new SequenceManager(604, 50);
    }

    private final long conversationID;
    private final JID fromJID;
    private final JID toJID;
    private final Date sentDate;
    private final String body;
    private final String stanza;
    private final boolean roomEvent;
    private final long id;
    private final JID isPMforJID;

    /**
     * Creates a new archived message.
     *
     * @param conversationID the ID of the conversation that the message is associated with.
     * @param fromJID the JID of the user that sent the message.
     * @param toJID the JID of the user that the message was sent to.
     * @param sentDate the date the message was sent.
     * @param body the body of the message
     * @param roomEvent true if the message belongs to a room event. Eg. User joined room.
     * @param isPMforJID the JID of the user that is the recipient of the message, if the message was a PM sent in a MUC.
     */
    public ArchivedMessage(long conversationID, JID fromJID, JID toJID, Date sentDate, String body, boolean roomEvent, JID isPMforJID) {
        this(conversationID, fromJID, toJID, sentDate, body, null, roomEvent, isPMforJID);
    }

    public ArchivedMessage(long conversationID, JID fromJID, JID toJID, Date sentDate, String body, String stanza, boolean roomEvent, JID isPMforJID) {
        // OF-2157: It is important to assign a message ID, which is used for ordering messages in a conversation, soon
        // after the message arrived, as opposed to just before the message gets written to the database. In the latter
        // scenario, the message ID values might no longer reflect the order of the messages in a conversation, as
        // database writes are batched up together for performance reasons. Using these batches won't affect the
        // database-insertion order (as compared to the order of messages in the conversation) on a single Openfire
        // server, but when running in a cluster, these batches do have a good chance to mess up the order of things.
        this.id = SequenceManager.nextID(this);
        this.conversationID = conversationID;
        // Convert both JID's to bare JID's so that we don't store resource information.
        this.fromJID = fromJID;
        this.toJID = toJID;
        this.sentDate = sentDate;
        this.body = body;
        this.roomEvent = roomEvent;
        this.stanza = stanza;
        this.isPMforJID = isPMforJID;
    }

    /**
     * The conversation ID that the message is associated with.
     *
     * @return the conversation ID.
     */
    public long getConversationID() {
        return conversationID;
    }

    /**
     * The ID that the message is associated with.
     *
     * This value is used to order messages in a conversation.
     *
     * @return the conversation ID.
     */
    public long getID() {
        return id;
    }

    /**
     * The JID of the user that sent the message.
     *
     * @return the sender JID.
     */
    public JID getFromJID() {
        return fromJID;
    }

    /**
     * The JID of the user that received the message.
     *
     * @return the recipient JID.
     */
    public JID getToJID() {
        return toJID;
    }

    /**
     * The date the message was sent.
     *
     * @return the date the message was sent.
     */
    public Date getSentDate() {
        return sentDate;
    }

    /**
     * The body of the message.
     *
     * @return the body of the message.
     */
    public String getBody() {
        return body;
    }

    /**
     * String encoded message stanza.
     *
     * @return string encoded message stanza.
     */
    public String getStanza() {
        return stanza;
    }

    /**
     * Returns true if the message belongs to a room event. Examples of room events are:
     * user joined the room or user left the room.
     *
     * @return true if the message belongs to a room event.
     */
    public boolean isRoomEvent() {
        return roomEvent;
    }

    /**
     * Returns the JID of the user that this message was addressed at, in case the message was a Private Message
     * exchanged in a MUC room.
     *
     * @return A JID
     */
    public JID getIsPMforJID() {
        return isPMforJID;
    }

    /**
     * Returns the nickname of the occupant that this message was addressed at, in case the message was a Private Message
     * exchanged in a MUC room.
     *
     * @return A nickname
     */
    public String getIsPMforNickname() {
        String result = null;
        if (isPMforJID != null) {
            // Use the real JID as a fallback.
            result= isPMforJID.toBareJID();
            try {
                // Prefer to use the nickname, which we can only get by parsing the original stanza.
                if (stanza != null) {
                    final org.dom4j.Document doc = DocumentHelper.parseText(stanza);
                    result = new Message(doc.getRootElement()).getTo().getResource();
                }
            } catch (Exception e) {
                Log.warn("Unable to parse then nickname from a private message with message ID {}", id);
            }
        }
        return result;
    }
}
