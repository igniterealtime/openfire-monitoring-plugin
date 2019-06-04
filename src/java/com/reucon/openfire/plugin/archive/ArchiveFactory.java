package com.reucon.openfire.plugin.archive;

import java.util.Date;
import java.util.UUID;

import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.stanzaid.StanzaIDUtil;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import com.reucon.openfire.plugin.archive.model.ArchivedMessage;

/**
 * Factory to create model objects.
 */
public class ArchiveFactory {
    private ArchiveFactory() {

    }

    public static ArchivedMessage createArchivedMessage(Session session,
            Message message, ArchivedMessage.Direction direction, JID ownerJid, JID withJid) {
        final UUID sid = StanzaIDUtil.parseUniqueAndStableStanzaID( message, ownerJid.toBareJID() );
        final ArchivedMessage archivedMessage;

        archivedMessage = new ArchivedMessage(new Date(), direction, message
                .getType().toString(), withJid, sid);
        archivedMessage.setSubject(message.getSubject());
        archivedMessage.setBody(message.getBody());

        return archivedMessage;
    }
}
