package com.reucon.openfire.plugin.archive.impl;

import com.reucon.openfire.plugin.archive.ArchiveFactory;
import com.reucon.openfire.plugin.archive.ArchiveManager;
import com.reucon.openfire.plugin.archive.IndexManager;
import com.reucon.openfire.plugin.archive.PersistenceManager;
import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import com.reucon.openfire.plugin.archive.model.Conversation;
import com.reucon.openfire.plugin.archive.model.Participant;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.ChatMarker;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Default implementation of ArchiveManager.
 */
public class ArchiveManagerImpl implements ArchiveManager
{
    private final PersistenceManager persistenceManager;
    private final IndexManager indexManager;
    private final Collection<Conversation> activeConversations;
    private int conversationTimeout;

    public ArchiveManagerImpl(PersistenceManager persistenceManager, IndexManager indexManager, int conversationTimeout)
    {
        this.persistenceManager = persistenceManager;
        this.indexManager = indexManager;
        this.conversationTimeout = conversationTimeout;

        activeConversations = persistenceManager.getActiveConversations(conversationTimeout);
    }

    public void archiveMessage(Session session, Message message, boolean incoming)
    {
        final XMPPServer server = XMPPServer.getInstance();
        final ArchivedMessage.Direction direction;
        final ArchivedMessage archivedMessage;
        final Conversation conversation;
        final JID owner;
        final JID with;
        ChatMarker.TYPE chatmarker = ChatMarker.searchForXep0333(message.toXML());

        // TODO support groupchat
        if (message.getType() != Message.Type.chat && message.getType() != Message.Type.normal)
        {
            if (chatmarker==ChatMarker.TYPE.NONE)
            {
                return;
            }
        }

        if (server.isLocal(message.getFrom()) && incoming)
        {
            owner = message.getFrom();
            with = message.getTo();
            // sent by the owner => to
            direction = ArchivedMessage.Direction.to;
        }
        else if (server.isLocal(message.getTo()) && ! incoming)
        {
            owner = message.getTo();
            with = message.getFrom();
            // received by the owner => from
            direction = ArchivedMessage.Direction.from;
        }
        else
        {
            return;
        }

        archivedMessage = ArchiveFactory.createArchivedMessage(session, message, direction, owner, with);
        if (chatmarker==ChatMarker.TYPE.NONE&&archivedMessage.isEmpty())
        {
            return;
        }

        conversation = determineConversation(owner, with, message.getSubject(), message.getThread(), archivedMessage);
        archivedMessage.setConversation(conversation);

        persistenceManager.createMessage(archivedMessage);
        if (indexManager != null)
        {
            indexManager.indexObject(archivedMessage);
        }
    }

    public void setConversationTimeout(int conversationTimeout)
    {
        this.conversationTimeout = conversationTimeout;
    }

    private Conversation determineConversation(JID owner, JID with, String subject, String thread, ArchivedMessage archivedMessage)
    {
        Conversation conversation = null;
        Collection<Conversation> staleConversations;

        staleConversations = new ArrayList<Conversation>();
        synchronized (activeConversations)
        {
            for (Conversation c : activeConversations)
            {
                if (c.isStale(conversationTimeout))
                {
                    staleConversations.add(c);
                    continue;
                }

                if (matches(owner, with, thread, c))
                {
                    conversation = c;
                    break;
                }
            }

            activeConversations.removeAll(staleConversations);
            
            if (conversation == null)
            {
                final Participant p1;
                final Participant p2;

                conversation = new Conversation(archivedMessage.getTime(), owner, with, subject, thread);
                persistenceManager.createConversation(conversation);

                p1 = new Participant(archivedMessage.getTime(), owner.asBareJID());
                conversation.addParticipant(p1);
                persistenceManager.createParticipant(p1, conversation.getId());

                p2 = new Participant(archivedMessage.getTime(), with.asBareJID());
                conversation.addParticipant(p2);
                persistenceManager.createParticipant(p2, conversation.getId());
                activeConversations.add(conversation);
            }
            else
            {
                conversation.setEnd(archivedMessage.getTime());
                persistenceManager.updateConversationEnd(conversation);
            }
        }

        return conversation;
    }

    private boolean matches(JID owner, JID with, String thread, Conversation c)
    {
        if (! owner.asBareJID().equals(c.getOwnerBareJid()))
        {
            return false;
        }
        if (! with.asBareJID().equals(c.getWithBareJid()))
        {
            return false;
        }

        if (thread != null)
        {
            if (! thread.equals(c.getThread()))
            {
                return false;
            }
        }
        else
        {
            if (c.getThread() != null)
            {
                return false;
            }
        }

        return true;
    }
}
