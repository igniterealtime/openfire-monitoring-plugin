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
 * Before a message is routed, this interceptor adds a XEP-0359 'Unique and Stable Stanza ID' for the archive of the
 * recipient of that message (if that recipient is a local user). When the message is archived, an identifier for the
 * archive of the sender is added to the archived representation of the message only. The same identifier value is used
 * in both archives.
 *
 * Before a message is delivered to a local user, this interceptor adjusts the identifiers that were generated for other
 * local users in that message. This is needed for Message Carbons copies, that are generated from the stanza that was
 * routed to its recipient: the identifier of that recipient is attributed to the user that the copy is delivered to (as
 * both use the same value), or removed when that user is not a participant of the conversation.
 *
 * A message that is addressed to an entity that is not a local user does not obtain an identifier before it is routed,
 * and the identifier that is used in the archive of the (local) sender of such a message is therefore not part of the
 * copies of it that are delivered to that sender either (notably its Message Carbons 'sent' copy).
 *
 * @author Matt Tucker
 * @see ArchiveStanzaIDUtil
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
        if (packet instanceof Message) {
            // Ignore any outgoing messages (we'll catch them when they're incoming), but do adjust the XEP-0359
            // identifiers that were generated for other users in the stanza that is about to be delivered.
            if (!incoming) {
                if (!processed && session != null) {
                    // A Message Carbons 'sent' copy is generated from the stanza that was routed to the recipient of
                    // that stanza, and therefore contains the identifier that is attributed to that recipient. As the
                    // same value is used in the archive of the sender, it is attributed to the sender here. A user
                    // should not learn an identifier that is attributed to any other user.
                    ArchiveStanzaIDUtil.adjustForRecipient((Message) packet, session.getAddress());
                }
                return;
            }
            Message message = (Message) packet;

            if (incoming && !processed) {
                // XEP-0359 requires an element of which the 'by' attribute matches the value that we would set to be
                // removed, even when no identifier of our own is added. This applies to every message, including the
                // ones that this plugin does not archive (such as messages that are addressed to a chat room, which
                // are stored and served by the MUC service), and irrespective of the configuration that controls the
                // generation of identifiers.
                ArchiveStanzaIDUtil.removeSpoofedStanzaIDs(message);
            }

            // Ignore any messages that don't have a body so that we skip events.
            // Note: XHTML messages should always include a body so we should be ok. It's
            // possible that we may need special XHTML filtering in the future, however.
            if (message.getBody() != null) {
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

                    if (!processed) {
                        // Before the stanza is routed, add a XEP-0359 'Unique and Stable Stanza ID' for the archive of
                        // the recipient (if that is a local user). This allows the recipient to correlate the message
                        // that is delivered to it with the message in its archive. Openfire does this for messages that
                        // are exchanged in a chat room, but not for one-to-one messages.
                        //
                        // Note that no identifier is added for the archive of the sender: that would allow the
                        // recipient to learn the identifier that is used in the archive of the sender. The identifier
                        // for the archive of the sender is added when the message is archived (below).
                        if (to != null && conversationManager.isMessageArchivingEnabled()) {
                            ArchiveStanzaIDUtil.addStanzaIDToRoutedStanza(message, to);
                        }
                        return;
                    }

                    // Add a XEP-0359 'Unique and Stable Stanza ID' for the archive of the sender (if that is a local
                    // user) to the representation of the stanza that is stored in the archive. Unlike the identifier
                    // that is generated for the recipient (above), this identifier is not added to the stanza that is
                    // being routed: it is added to a copy of that stanza, leaving the original unmodified.
                    final String stanza = conversationManager.isMessageArchivingEnabled()
                            ? ArchiveStanzaIDUtil.getArchivableStanzaXml(message, message.getFrom(), to)
                            : message.toXML();

                    // Process this event in the senior cluster member or local JVM when not in a cluster
                    if (ClusterManager.isSeniorClusterMember()) {
                        conversationManager.processMessage(message.getFrom(), message.getTo(), message.getBody(), stanza, new Date());
                    }
                    else {
                        JID sender = message.getFrom();
                        JID receiver = message.getTo();
                        ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();
                        eventsQueue.addChatEvent(conversationManager.getConversationKey(sender, receiver),
                                ConversationEvent.chatMessageReceived(sender, receiver,
                                        conversationManager.isMessageArchivingEnabled() ? message.getBody() : null,
                                        conversationManager.isMessageArchivingEnabled() ? stanza : null,
                                        new Date()));
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
