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

        // Use a 'null' value for the numeric database ID, as the resulting object has not yet been stored in the database.
        final Long id = null;

        archivedMessage = new ArchivedMessage(id, new Date(), direction, message.getType().toString(), with, sid, message.getBody(), message.toXML());

        return archivedMessage;
    }
}
