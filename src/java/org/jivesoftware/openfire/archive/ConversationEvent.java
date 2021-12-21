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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.Date;
import java.util.Objects;

/**
 * Conversation events are only used when running in a cluster as a way to send to the senior cluster
 * member information about a conversation that is taking place in this cluster node.
 *
 * @author Gaston Dombiak
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ConversationEvent {
    private static final Logger Log = LoggerFactory.getLogger(ConversationEvent.class);

    private Type type;

    private Date date;

    private String body;

    private String stanza;

    @XmlJavaTypeAdapter(XmlSerializer.JidAdapter.class)
    private JID sender;

    @XmlJavaTypeAdapter(XmlSerializer.JidAdapter.class)
    private JID receiver;

    @XmlJavaTypeAdapter(XmlSerializer.JidAdapter.class)
    private JID roomJID;

    @XmlJavaTypeAdapter(XmlSerializer.JidAdapter.class)
    private JID user;

    private String nickname;

    /**
     * Do not use this constructor. It only exists for serialization purposes.
     */
    public ConversationEvent() {
    }

    public void run(ConversationManager conversationManager) {
        Log.debug("Processing {} chat event dated {}", type, date);
        if (Type.chatMessageReceived == type) {
            conversationManager.processMessage(sender, receiver, body, stanza, date);
        }
        else if (Type.roomDestroyed == type) {
            conversationManager.roomConversationEnded(roomJID, date);
        }
        else if (Type.occupantJoined == type) {
            conversationManager.joinedGroupConversation(roomJID, user, nickname, date);
        }
        else if (Type.occupantLeft == type) {
            conversationManager.leftGroupConversation(roomJID, user, date);
            // If there are no more occupants then consider the group conversation over
            MUCRoom mucRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomJID.getNode());
            if (mucRoom != null &&  mucRoom.getOccupantsCount() == 0) {
                conversationManager.roomConversationEnded(roomJID, date);
            }
        }
        else if (Type.nicknameChanged == type) {
            conversationManager.leftGroupConversation(roomJID, user, date);
            conversationManager.joinedGroupConversation(roomJID, user, nickname, new Date(date.getTime() + 1));
        }
        else if (Type.roomMessageReceived == type) {
            conversationManager.processRoomMessage(roomJID, user, receiver, nickname, body, stanza, date);
        }
    }

    public static ConversationEvent chatMessageReceived(JID sender, JID receiver, String body, String stanza, Date date) {
        ConversationEvent event = new ConversationEvent();
        event.type = Type.chatMessageReceived;
        event.sender = sender;
        event.receiver = receiver;
        event.body = body;
        event.stanza = stanza;
        event.date = date;
        return event;
    }

    public static ConversationEvent roomDestroyed(JID roomJID, Date date) {
        ConversationEvent event = new ConversationEvent();
        event.type = Type.roomDestroyed;
        event.roomJID = roomJID;
        event.date = date;
        return event;
    }

    public static ConversationEvent occupantJoined(JID roomJID, JID user, String nickname, Date date) {
        ConversationEvent event = new ConversationEvent();
        event.type = Type.occupantJoined;
        event.roomJID = roomJID;
        event.user = user;
        event.nickname = nickname;
        event.date = date;
        return event;
    }

    public static ConversationEvent occupantLeft(JID roomJID, JID user, Date date) {
        ConversationEvent event = new ConversationEvent();
        event.type = Type.occupantLeft;
        event.roomJID = roomJID;
        event.user = user;
        event.date = date;
        return event;
    }

    public static ConversationEvent nicknameChanged(JID roomJID, JID user, String newNickname, Date date) {
        ConversationEvent event = new ConversationEvent();
        event.type = Type.nicknameChanged;
        event.roomJID = roomJID;
        event.user = user;
        event.nickname = newNickname;
        event.date = date;
        return event;
    }

    public static ConversationEvent roomMessageReceived(JID roomJID, JID user, JID receiverIfPM, String nickname, String body,
                                                        String stanza, Date date) {
        ConversationEvent event = new ConversationEvent();
        event.type = Type.roomMessageReceived;
        event.roomJID = roomJID;
        event.user = user;
        event.nickname = nickname;
        event.body = body;
        event.stanza = stanza;
        event.date = date;
        event.receiver = receiverIfPM;
        return event;
    }

    private enum Type {
        /**
         * Event triggered when a room was destroyed.
         */
        roomDestroyed,
        /**
         * Event triggered when a new occupant joins a room.
         */
        occupantJoined,
        /**
         * Event triggered when an occupant left a room.
         */
        occupantLeft,
        /**
         * Event triggered when an occupant changed his nickname in a room.
         */
        nicknameChanged,
        /**
         * Event triggered when a room occupant sent a message to a room.
         */
        roomMessageReceived,
        /**
         * Event triggered when a user sent a message to another user.
         */
        chatMessageReceived
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConversationEvent that = (ConversationEvent) o;
        return type == that.type && Objects.equals(date, that.date) && Objects.equals(body, that.body) && Objects.equals(stanza, that.stanza) && Objects.equals(sender, that.sender) && Objects.equals(receiver, that.receiver) && Objects.equals(roomJID, that.roomJID) && Objects.equals(user, that.user) && Objects.equals(nickname, that.nickname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, date, body, stanza, sender, receiver, roomJID, user, nickname);
    }
}
