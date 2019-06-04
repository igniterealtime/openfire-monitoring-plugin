package com.reucon.openfire.plugin.archive.xep0313;

import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

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
