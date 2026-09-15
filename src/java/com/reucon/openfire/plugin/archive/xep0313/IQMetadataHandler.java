/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package com.reucon.openfire.plugin.archive.xep0313;

import com.reucon.openfire.plugin.archive.impl.DataRetrievalException;
import com.reucon.openfire.plugin.archive.model.ArchiveMetadata;
import com.reucon.openfire.plugin.archive.xep.AbstractIQHandler;
import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.muc.Affiliation;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

/**
 * Handles mam:2 archive metadata queries ({@code <metadata xmlns='urn:xmpp:mam:2'/>}).
 */
class IQMetadataHandler extends AbstractIQHandler
{
    private static final Logger Log = LoggerFactory.getLogger(IQMetadataHandler.class);
    private static final String MODULE_NAME = "Message Archive Management Metadata Handler v2";
    private static final String NAMESPACE = "urn:xmpp:mam:2";

    IQMetadataHandler()
    {
        super(MODULE_NAME, "metadata", NAMESPACE);
    }

    @Override
    public IQ handleIQ(final IQ packet) throws UnauthorizedException
    {
        JID archiveJid = packet.getTo();
        if (archiveJid == null) {
            archiveJid = packet.getFrom().asBareJID();
        }
        Log.debug("Archive metadata requested for: {}", archiveJid);

        MultiUserChatService service = null;
        MUCRoom room = null;
        if (!XMPPServer.getInstance().isLocal(archiveJid)) {
            service = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(archiveJid);
            if (service != null) {
                room = service.getChatRoom(archiveJid.getNode());
            }
            if (room == null) {
                return buildErrorResponse(packet, PacketError.Condition.item_not_found,
                    "The archive '" + archiveJid + "' cannot be found or is not accessible.");
            }
        }

        final JID requestor = packet.getFrom().asBareJID();
        if (room != null) {
            boolean pass = false;
            if (service.isSysadmin(requestor)) {
                pass = true;
            }
            final Affiliation aff = room.getAffiliation(requestor);
            if (aff != Affiliation.outcast) {
                if (aff == Affiliation.owner || aff == Affiliation.admin) {
                    pass = true;
                } else if (room.isMembersOnly()) {
                    if (aff == Affiliation.member) {
                        pass = true;
                    }
                } else {
                    pass = true;
                }
            }
            if (!pass) {
                return buildErrorResponse(packet, PacketError.Condition.forbidden,
                    "You are currently not allowed to access the archive of room '" + room.getJID() + "'.");
            }
        } else if (!archiveJid.asBareJID().equals(requestor)) {
            if (!XMPPServer.getInstance().getAdmins().contains(requestor)) {
                return buildErrorResponse(packet, PacketError.Condition.forbidden,
                    "You are not allowed to access the archive of '" + archiveJid + "'.");
            }
        }

        final ArchiveMetadata metadata;
        try {
            metadata = getPersistenceManager(archiveJid.asBareJID()).getArchiveMetadata(archiveJid.asBareJID());
        } catch (DataRetrievalException e) {
            Log.error("Failed to retrieve archive metadata for {}", archiveJid, e);
            return buildErrorResponse(packet, PacketError.Condition.internal_server_error,
                "An unexpected exception occurred while retrieving archive metadata.");
        }

        final IQ result = IQ.createResultIQ(packet);
        final Element metadataElement = result.setChildElement("metadata", NAMESPACE);
        ArchiveMetadataElement.populate(metadataElement, metadata, archiveJid.asBareJID());
        return result;
    }

    private IQ buildErrorResponse(final IQ packet, final PacketError.Condition condition, final String message)
    {
        final IQ reply = IQ.createResultIQ(packet);
        reply.setChildElement(packet.getChildElement().createCopy());
        final PacketError packetError = new PacketError(condition);
        if (message != null && !message.isEmpty()) {
            packetError.setText(message);
        }
        reply.setError(packetError);
        return reply;
    }
}
