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
import org.jivesoftware.openfire.archive.EmptyMessageUtils.EmptyMessageType;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.muc.MUCEventDispatcher;
import org.jivesoftware.openfire.muc.MUCEventListener;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.Date;

/**
 * Interceptor of MUC events of the local conferencing service. The interceptor is responsible
 * for reacting to users joining and leaving rooms as well as messages being sent to rooms.
 *
 * @author Gaston Dombiak
 */
public class GroupConversationInterceptor implements MUCEventListener {

    private static final Logger Log = LoggerFactory.getLogger(GroupConversationInterceptor.class);

    private ConversationManager conversationManager;

    private static final SystemProperty<Boolean> PM_IN_PERSONAL_ARCHIVE = SystemProperty.Builder.ofType( Boolean.class )
       .setKey("conversation.roomArchiving.PMinPersonalArchive" )
       .setDefaultValue( false )
       .setDynamic( true )
       .setPlugin(MonitoringConstants.PLUGIN_NAME)
       .build();

    private static final SystemProperty<Boolean> PM_IN_ROOM_ARCHIVE = SystemProperty.Builder.ofType( Boolean.class )
       .setKey("conversation.roomArchiving.PMinRoomArchive" )
       .setDefaultValue( true )
       .setDynamic( true )
       .setPlugin(MonitoringConstants.PLUGIN_NAME)
       .build();

    public GroupConversationInterceptor(ConversationManager conversationManager) {
        this.conversationManager = conversationManager;
    }

    @Override
    public void roomCreated(JID roomJID) {
        //Do nothing
    }

    @Override
    public void occupantNickKicked(JID jid, String nickname) {
        // Do nothing. This will result in users being removed from rooms, which should trigger an occupantLeft invocation for each room involved.
    }

    @Override
    public void roomDestroyed(JID roomJID) {
        // Process this event in the senior cluster member or local JVM when not in a cluster
        if (ClusterManager.isSeniorClusterMember()) {
            conversationManager.roomConversationEnded(roomJID, new Date());
        }
        else {
            ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();
            eventsQueue.addGroupChatEvent(conversationManager.getRoomConversationKey(roomJID),
                    ConversationEvent.roomDestroyed(roomJID, new Date()));
        }
    }

    @Override
    public void occupantJoined(JID roomJID, JID user, String nickname) {
        // Process this event in the senior cluster member or local JVM when not in a cluster
        if (ClusterManager.isSeniorClusterMember()) {
            conversationManager.joinedGroupConversation(roomJID, user, nickname, new Date());
        }
        else {
            ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();
            eventsQueue.addGroupChatEvent(conversationManager.getRoomConversationKey(roomJID),
                    ConversationEvent.occupantJoined(roomJID, user, nickname, new Date()));
        }
    }

    @Override
    public void occupantLeft(JID roomJID, JID user, String nickname) {
        // Process this event in the senior cluster member or local JVM when not in a cluster
        if (ClusterManager.isSeniorClusterMember()) {
            conversationManager.leftGroupConversation(roomJID, user, new Date());
            // If there are no more occupants then consider the group conversation over
            MUCRoom mucRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomJID.getNode());
            if (mucRoom != null &&  mucRoom.getOccupantsCount() == 0) {
                conversationManager.roomConversationEnded(roomJID, new Date());
            }
        }
        else {
            ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();
            eventsQueue.addGroupChatEvent(conversationManager.getRoomConversationKey(roomJID),
                    ConversationEvent.occupantLeft(roomJID, user, new Date()));
        }
    }

    @Override
    public void nicknameChanged(JID roomJID, JID user, String oldNickname, String newNickname) {
        // Process this event in the senior cluster member or local JVM when not in a cluster
        if (ClusterManager.isSeniorClusterMember()) {
            occupantLeft(roomJID, user, oldNickname);
            // Sleep 1 millisecond so that there is a delay between logging out and logging in
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore
            }
            occupantJoined(roomJID, user, newNickname);
        }
        else {
            ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();
            eventsQueue.addGroupChatEvent(conversationManager.getRoomConversationKey(roomJID),
                    ConversationEvent.nicknameChanged(roomJID, user, newNickname, new Date()));
        }
    }

    @Override
    public void messageReceived(JID roomJID, JID user, String nickname, Message message) {
        final Date now = new Date();

        // Process this event in the senior cluster member or local JVM when not in a cluster
        if (ClusterManager.isSeniorClusterMember()) {
            Log.trace("Message received on senior node for room: {}", roomJID);
            conversationManager.processRoomMessage(roomJID, user, null, nickname, message.getBody(), message.toXML(), now);
        }
        else {
            Log.trace("Message received on junior node for room: {}", roomJID);
            boolean withBody = conversationManager.isRoomArchivingEnabled() && (conversationManager.getRoomsArchived().isEmpty() ||
                               conversationManager.getRoomsArchived().contains(roomJID.getNode()));

            if (withBody)
            {
                ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();

                eventsQueue.addGroupChatEvent(conversationManager.getRoomConversationKey(roomJID),
                                              ConversationEvent.roomMessageReceived(roomJID, user, null, nickname, withBody ? message.getBody() : null, message.toXML(), now));
            }
            else
            {
                EmptyMessageType emptyMessageType = EmptyMessageUtils.getMessageType(message.getElement());

                long bitmask = conversationManager.getSpeficifEmptyMessageArchivingForMUCEnabled();

                if (emptyMessageType!=EmptyMessageType.TYPE_EVENT && (
                    emptyMessageType==EmptyMessageType.TYPE_UNKNOWN && ((bitmask & EmptyMessageType.TYPE_UNKNOWN.getValue())==EmptyMessageType.TYPE_UNKNOWN.getValue())||
                    (bitmask & EmptyMessageType.TYPE_CHATMARKER_MARKABLE.getValue())==EmptyMessageType.TYPE_CHATMARKER_MARKABLE.getValue()||
                    (bitmask & EmptyMessageType.TYPE_CHATMARKER_RECEIVED.getValue())==EmptyMessageType.TYPE_CHATMARKER_RECEIVED.getValue()||
                    (bitmask & EmptyMessageType.TYPE_CHATMARKER_DISPLAYED.getValue())==EmptyMessageType.TYPE_CHATMARKER_DISPLAYED.getValue()||
                    (bitmask & EmptyMessageType.TYPE_CHATMARKER_ACKNOWLEDGED.getValue())==EmptyMessageType.TYPE_CHATMARKER_ACKNOWLEDGED.getValue()||
                    (bitmask & EmptyMessageType.TYPE_MESSAGE_RETRACTION.getValue())==EmptyMessageType.TYPE_MESSAGE_RETRACTION.getValue()||
                    (bitmask & EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_ACTIVE.getValue())==EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_ACTIVE.getValue()||
                    (bitmask & EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_COMPOSING.getValue())==EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_COMPOSING.getValue()||
                    (bitmask & EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_PAUSED.getValue())==EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_PAUSED.getValue()||
                    (bitmask & EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_INACTIVE.getValue())==EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_INACTIVE.getValue()||
                    (bitmask & EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_GONE.getValue())==EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_GONE.getValue()||
                    (bitmask & EmptyMessageType.TYPE_MESSAGE_DELIVERY_RECEIPTS_RECEIVED.getValue())==EmptyMessageType.TYPE_MESSAGE_DELIVERY_RECEIPTS_RECEIVED.getValue()||
                    (bitmask & EmptyMessageType.TYPE_MESSAGE_DELIVERY_RECEIPTS_REQUEST.getValue())==EmptyMessageType.TYPE_MESSAGE_DELIVERY_RECEIPTS_REQUEST.getValue()))
                {
                    ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();

                    eventsQueue.addGroupChatEvent(conversationManager.getRoomConversationKey(roomJID),
                            ConversationEvent.getEmptyMessageReceivedEvent(roomJID, user, emptyMessageType,
                                    conversationManager.isMessageArchivingEnabled() ? message.toXML() : null, new Date()));
                }
            }
        }
    }

    @Override
    public void privateMessageRecieved(JID toJID, JID fromJID, Message message) {
        if(message.getBody() != null) {
            final JID roomJID = message.getFrom().asBareJID();
            final String senderNickname = message.getFrom().getResource();
            final Date now = new Date();
            if (ClusterManager.isSeniorClusterMember()) {
                if (PM_IN_PERSONAL_ARCHIVE.getValue()) {
                    // Historically, private messages are saved as regular 'one-on-one' messages.
                    conversationManager.processMessage(fromJID, toJID, message.getBody(), message.toXML(), now);
                }

                if (PM_IN_ROOM_ARCHIVE.getValue()) {
                    // Since issue #133 they also get stored specifically as a PM in MUC context.
                    conversationManager.processRoomMessage(roomJID, fromJID, toJID, senderNickname, message.getBody(), message.toXML(), now);
                }
            }
            else {
                ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();

                if (PM_IN_PERSONAL_ARCHIVE.getValue()) {
                    eventsQueue.addChatEvent(
                        conversationManager.getConversationKey(fromJID, toJID),
                        ConversationEvent.chatMessageReceived(toJID, fromJID,
                            conversationManager.isMessageArchivingEnabled() ? message.getBody() : null,
                            conversationManager.isMessageArchivingEnabled() ? message.toXML() : null,
                            now));
                }

                if (PM_IN_ROOM_ARCHIVE.getValue()) {
                    eventsQueue.addGroupChatEvent(
                        conversationManager.getRoomConversationKey(roomJID),
                        ConversationEvent.roomMessageReceived(roomJID, fromJID, toJID, senderNickname, conversationManager.isMessageArchivingEnabled() ? message.getBody() : null, message.toXML(), now));
                }
             }
        }
    }

    @Override
    public void roomSubjectChanged(JID roomJID, JID user, String newSubject) {
        // Do nothing
    }

    public void start() {
        MUCEventDispatcher.addListener(this);
    }

    public void stop() {
        MUCEventDispatcher.removeListener(this);
        conversationManager = null;
    }
}
