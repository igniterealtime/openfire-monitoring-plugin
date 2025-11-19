package com.reucon.openfire.plugin.archive.xep0313;

import com.reucon.openfire.plugin.archive.PersistenceManager;
import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.handler.IQHandler;
import com.reucon.openfire.plugin.archive.model.MamArchiveMetadata;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.xmpp.packet.IQ;

public class MamMetadataIQHandler extends IQHandler {
    private final PersistenceManager persistenceManager;

    public MamMetadataIQHandler(PersistenceManager persistenceManager) {
        super("MAM Metadata Handler");
        this.persistenceManager = persistenceManager;
    }

    @Override
    public IQ handleIQ(IQ iq) {
        if (iq.getType() != IQ.Type.get) {
            return IQ.createResultIQ(iq);
        }

        Element metadata = iq.getChildElement();
        if (metadata == null || !metadata.getNamespaceURI().equals("urn:xmpp:mam:2")) {
            return IQ.createResultIQ(iq);
        }

        MamArchiveMetadata archiveMetadata = persistenceManager.getArchiveMetadata(iq.getFrom());

        IQ result = IQ.createResultIQ(iq);
        Element resultMetadata = result.setChildElement("metadata", "urn:xmpp:mam:2");

        if (!archiveMetadata.isEmpty()) {
            Element start = resultMetadata.addElement("start");
            start.addAttribute("id", archiveMetadata.getStartId());
            start.addAttribute("timestamp", XMPPDateTimeFormat.format(archiveMetadata.getStartTimestamp()));

            Element end = resultMetadata.addElement("end");
            end.addAttribute("id", archiveMetadata.getEndId());
            end.addAttribute("timestamp", XMPPDateTimeFormat.format(archiveMetadata.getEndTimestamp()));
        }

        return result;
    }

    @Override
    public IQHandlerInfo getInfo() {
        return new IQHandlerInfo("metadata", "urn:xmpp:mam:2");
    }
}
