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

import org.jivesoftware.openfire.archive.EmptyMessageUtils.EmptyMessageType;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.privacy.PrivacyList;
import org.jivesoftware.openfire.privacy.PrivacyListManager;
import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.util.Date;

/**
 * Intercepts packets to track conversations. Only the following messages
 * are processed:
 * <ul>
 *  <li>Messages sent between local users.</li>
 *  <li>Messages sent between local user and remote entities (e.g. remote users).</li>
 *  <li>Messages sent between local users and users using legacy networks (i.e. transports).</li>
 * </ul>
 * Therefore, messages that are sent to Publish-Subscribe or any other internal service are ignored.
 *
 * @author Matt Tucker
 */
public class ArchiveInterceptor implements PacketInterceptor {

    private ConversationManager conversationManager;
    private static final Logger Log = LoggerFactory.getLogger(ArchiveInterceptor.class);

    public ArchiveInterceptor(ConversationManager conversationManager) {
        this.conversationManager = conversationManager;
    }

    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed)
            throws PacketRejectedException
    {
        // Ignore any packets that haven't already been processed by interceptors.
        if (!processed) {
            return;
        }
        if (packet instanceof Message) {
            // Ignore any outgoing messages (we'll catch them when they're incoming).
            if (!incoming) {
                return;
            }
            Message message = (Message) packet;
            // Ignore any messages that don't have a body so that we skip events.
            // Note: XHTML messages should always include a body so we should be ok. It's
            // possible that we may need special XHTML filtering in the future, however.
            if (message.getBody() != null||this.conversationManager.isEmptyMessageArchivingEnabled()) {
                // Only process messages that are between two users, group chat rooms, or gateways.
                if (conversationManager.isConversation(message)) {
                    //take care on blocklist
                    JID to = message.getTo();
                    if (to!=null)
                    {
                        final PrivacyList defaultPrivacyList = PrivacyListManager.getInstance().getDefaultPrivacyList(to.getNode());
                        if (defaultPrivacyList!=null&&defaultPrivacyList.shouldBlockPacket(message)) {
                            Log.debug( "Not storing message, as it is rejected by the default privacy list of the recipient ({}).", to.getNode() );
                            return;
                        }
                    }
                    // Process this event in the senior cluster member or local JVM when not in a cluster
                    if (ClusterManager.isSeniorClusterMember()) {
                        conversationManager.processMessage(message.getFrom(), message.getTo(), message.getBody(), message.toXML(), new Date());
                    }
                    else {
                        JID sender = message.getFrom();
                        JID receiver = message.getTo();
                        ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();
                        if (message.getBody()!=null)
                        {
                            eventsQueue.addChatEvent(conversationManager.getConversationKey(sender, receiver),
                                ConversationEvent.chatMessageReceived(sender, receiver,
                                        conversationManager.isMessageArchivingEnabled() ? message.getBody() : null,
                                        conversationManager.isMessageArchivingEnabled() ? message.toXML() : null,
                                        new Date()));
                        }
                        else
                        {
                            EmptyMessageType emptyMessageType = EmptyMessageUtils.getMessageType(message.getElement()); 

                            long bitmask = conversationManager.getSpeficifEmptyMessageArchivingEnabled();

                            if (emptyMessageType==EmptyMessageType.TYPE_UNKNOWN && ((bitmask & EmptyMessageType.TYPE_UNKNOWN.getValue())==EmptyMessageType.TYPE_UNKNOWN.getValue())||
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
                                (bitmask & EmptyMessageType.TYPE_MESSAGE_DELIVERY_RECEIPTS_REQUEST.getValue())==EmptyMessageType.TYPE_MESSAGE_DELIVERY_RECEIPTS_REQUEST.getValue())
                            {
                                eventsQueue.addChatEvent(conversationManager.getConversationKey(sender, receiver),
                                    ConversationEvent.getEmptyMessageReceivedEvent(sender, receiver,
                                            emptyMessageType,
                                            conversationManager.isMessageArchivingEnabled() ? message.toXML() : null,
                                            new Date()));
                            }
                        }
                    }
                }
            }
        }
    }

    public void start() {
        InterceptorManager.getInstance().addInterceptor(this);
    }

    public void stop() {
        InterceptorManager.getInstance().removeInterceptor(this);
        conversationManager = null;
    }
}
