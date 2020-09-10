package com.reucon.openfire.plugin.archive;

import java.util.Date;

import org.jivesoftware.openfire.session.Session;
import com.reucon.openfire.plugin.archive.util.StanzaIDUtil;
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
            Message message, ArchivedMessage.Direction direction, JID owner, JID with) {
        final String sid = StanzaIDUtil.findFirstUniqueAndStableStanzaID( message, owner.toBareJID() );
        final ArchivedMessage archivedMessage;

        archivedMessage = new ArchivedMessage(new Date(), direction, message.getType().toString(), with, sid);
        archivedMessage.setSubject(message.getSubject());
        archivedMessage.setBody(message.getBody());

        return archivedMessage;
    }
}
