package com.reucon.openfire.plugin.archive.xep0136;

import java.util.ArrayList;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.handler.IQHandler;

import com.reucon.openfire.plugin.archive.xep.AbstractXepSupport;

/**
 * Encapsulates support for <a
 * href="http://www.xmpp.org/extensions/xep-0136.html">XEP-0136</a>.
 */
public class Xep0136Support extends AbstractXepSupport {

	private static final String NAMESPACE = "urn:xmpp:archive:auto";

	public Xep0136Support(XMPPServer server) {
		super(server, NAMESPACE, "XEP-0136 IQ Dispatcher");

		iqHandlers = new ArrayList<IQHandler>();

		// support for #ns-pref
		// iqHandlers.add(new IQPrefHandler());

    private static final String NAMESPACE_AUTO = "urn:xmpp:archive:auto";
    private static final String IQ_NAMESPACE = "urn:xmpp:archive";

}
