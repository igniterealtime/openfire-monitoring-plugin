package com.reucon.openfire.plugin.archive.xep0313;

import com.reucon.openfire.plugin.archive.impl.DataRetrievalException;
import com.reucon.openfire.plugin.archive.model.ArchiveMetadata;
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

import java.util.Collections;
import java.util.List;

/**
 * XEP-0313 IQ Query Handler
 */
class IQQueryHandler2 extends IQQueryHandler {

    private static final Logger Log = LoggerFactory.getLogger( IQQueryHandler2.class);
    private static final String MODULE_NAME = "Message Archive Management Query Handler v2";

    IQQueryHandler2() {
        super(MODULE_NAME, "urn:xmpp:mam:2");
    }

    @Override
    boolean usesUniqueAndStableIDs()
    {
        return true;
    }

    @Override
    boolean advertisesExtended()
    {
        return true;
    }

    /**
     * Besides {@code <query/>}, this handler also handles {@code <metadata/>} IQs directly (rather than through a
     * dedicated handler instance). This is needed because Openfire core's per-MUC-service IQ handler registry
     * ({@code MultiUserChatServiceImpl}) is keyed by namespace only, not by element name: registering a second
     * handler under the same {@code urn:xmpp:mam:2} namespace would silently overwrite this one for MUC rooms,
     * causing one of the two element types to never reach its intended handler.
     */
    @Override
    public List<String> getAdditionalElementNames()
    {
        return Collections.singletonList("metadata");
    }

    @Override
    public IQ handleIQ(final IQ packet) throws UnauthorizedException
    {
        if ("metadata".equals(packet.getChildElement().getName())) {
            return handleMetadataQuery(packet);
        }
        return super.handleIQ(packet);
    }

    /**
     * Handles mam:2 archive metadata queries ({@code <metadata xmlns='urn:xmpp:mam:2'/>}), as defined by
     * XEP-0313 §5 "Archive metadata".
     *
     * @param packet Received metadata request (cannot be null).
     * @return The response to the request (never null).
     */
    private IQ handleMetadataQuery(final IQ packet) throws UnauthorizedException
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
                return buildMetadataErrorResponse(packet, PacketError.Condition.item_not_found,
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
                return buildMetadataErrorResponse(packet, PacketError.Condition.forbidden,
                    "You are currently not allowed to access the archive of room '" + room.getJID() + "'.");
            }

            // Password protected room
            if (room.isPasswordProtected() && room.getOccupantByFullJID(packet.getFrom()) == null) {
                // no occupant so currently not authenticated to query archive
                return buildMetadataErrorResponse(packet, PacketError.Condition.forbidden,
                    "You are currently not allowed to access the archive of room '" + room.getJID() + "'.");
            }
        } else if (!archiveJid.asBareJID().equals(requestor)) {
            if (!XMPPServer.getInstance().getAdmins().contains(requestor)) {
                return buildMetadataErrorResponse(packet, PacketError.Condition.forbidden,
                    "You are not allowed to access the archive of '" + archiveJid + "'.");
            }
        }

        final ArchiveMetadata metadata;
        try {
            metadata = getPersistenceManager(archiveJid.asBareJID()).getArchiveMetadata(archiveJid.asBareJID());
        } catch (DataRetrievalException e) {
            Log.error("Failed to retrieve archive metadata for {}", archiveJid, e);
            return buildMetadataErrorResponse(packet, PacketError.Condition.internal_server_error,
                "An unexpected exception occurred while retrieving archive metadata.");
        }

        final IQ result = IQ.createResultIQ(packet);
        final Element metadataElement = result.setChildElement("metadata", NAMESPACE);
        ArchiveMetadataElement.populate(metadataElement, metadata, archiveJid.asBareJID());
        return result;
    }

    private IQ buildMetadataErrorResponse(final IQ packet, final PacketError.Condition condition, final String message)
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

    @Override
    protected void sendEndQuery(IQ packet, JID jid, QueryRequest queryRequest) {
        sendAcknowledgementResult(packet, jid, queryRequest);
    }

    /**
     * Send result packet to client acknowledging query.
     * @param packet Received query packet
     * @param from to respond to
     */
    private void sendAcknowledgementResult(IQ packet, JID from, QueryRequest queryRequest) {
        if (packet.getTo() == null) {
            packet.setTo(from);
        }

        IQ result = IQ.createResultIQ(packet);
        Element fin = result.setChildElement("fin", NAMESPACE);
        completeFinElement(queryRequest, fin);
        router.route(result);
    }
}
