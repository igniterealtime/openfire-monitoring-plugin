package com.reucon.openfire.plugin.archive.xep;

import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

import com.reucon.openfire.plugin.archive.PersistenceManager;

import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for XEP-specific IQ Handlers.
 */
public abstract class AbstractIQHandler extends IQHandler {

    private final IQHandlerInfo info;

    protected AbstractIQHandler(String moduleName, String elementName, String namespace) {
        super(moduleName);
        this.info = new IQHandlerInfo(elementName, namespace);
    }

    public final IQHandlerInfo getInfo() {
        return info;
    }

    /**
     * Additional element names (besides {@link #getInfo()}'s element name) that this handler handles within its
     * namespace. Used by {@link AbstractXepSupport} to route IQs of multiple element names in the same namespace to
     * a single handler instance, which is needed for MUC dispatch: {@code MultiUserChatServiceImpl} registers IQ
     * handlers by namespace only, so a namespace can only ever be served by a single handler instance.
     *
     * @return A list of additional element names (possibly empty, never null).
     */
    public List<String> getAdditionalElementNames() {
        return Collections.emptyList();
    }

    protected PersistenceManager getPersistenceManager(JID jid) {
        return MonitoringPlugin.getInstance().getPersistenceManager(jid);
    }

    protected IQ error(Packet packet, PacketError.Condition condition) {
        IQ reply;

        reply = new IQ(IQ.Type.error, packet.getID());
        reply.setFrom(packet.getTo());
        reply.setTo(packet.getFrom());
        reply.setError(condition);
        return reply;
    }
}
