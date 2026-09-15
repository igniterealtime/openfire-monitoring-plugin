package com.reucon.openfire.plugin.archive.xep0313;

import com.reucon.openfire.plugin.archive.xep.AbstractXepSupport;
import org.jivesoftware.openfire.XMPPServer;

import java.util.ArrayList;

/**
 * Encapsulates support for <a
 * href="http://www.xmpp.org/extensions/xep-0313.html">XEP-0313</a>.
 */
public class Xep0313Support2 extends AbstractXepSupport {

    private static final String NAMESPACE = "urn:xmpp:mam:2";

    public Xep0313Support2( XMPPServer server) {
        super(server, NAMESPACE,NAMESPACE, "XEP-0313 IQ Dispatcher", true);

        this.iqHandlers = new ArrayList<>();
        // IQQueryHandler2 handles both 'query' and 'metadata' elements directly (see its
        // getAdditionalElementNames()). It must not be paired with a separate metadata handler here: Openfire
        // core's MultiUserChatServiceImpl registers IQ handlers by namespace only, so a second handler for the
        // same 'urn:xmpp:mam:2' namespace would silently overwrite this one for MUC rooms.
        iqHandlers.add(new IQQueryHandler2());
    }

}
