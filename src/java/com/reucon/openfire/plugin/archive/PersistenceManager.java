package com.reucon.openfire.plugin.archive;

import com.reucon.openfire.plugin.archive.impl.DataRetrievalException;
import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import com.reucon.openfire.plugin.archive.model.Conversation;
import com.reucon.openfire.plugin.archive.xep0059.XmppResultSet;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.packet.JID;

import java.util.Collection;
import java.util.Date;

/**
 * Manages database persistence.
 */
public interface PersistenceManager
{
    /**
     * Searches for conversations.
     *
     * @param startDate earliest start date of the conversation to find or <code>null</code> for any.
     * @param endDate   latest end date of the conversation to find or <code>null</code> for any.
     * @param owner     bare jid of the owner of the conversation to find or <code>null</code> for any.
     * @param with      bare jid of the communication partner or <code>null</code> for any. This is either
     *                  the jid of another XMPP user or the jid of a group chat.
     * @return the conversations that matched search criteria without messages and participants.
     */
    Collection<Conversation> findConversations(Date startDate, Date endDate, JID owner, JID with, XmppResultSet xmppResultSet);

    /**
     * Searches for messages.
     *
     * @param startDate     earliest start date of the message to find or <code>null</code> for any.
     * @param endDate       latest end date of the message to find or <code>null</code> for any.
     * @param archiveOwner  bare jid of the owner of the archive in which to find messages or <code>null</code> for any.
     * @param messageOwner  bare jid of the owner of the message to find or <code>null</code> for any.
     * @param with          jid of the communication partner or <code>null</code> for any. This is either
     *                      the jid of another XMPP user or the jid of a group chat.
     * @param query         A query string, typically representing keywords or a partial text, or <code>null</code>.
     * @param useStableID   true if MAM2 or another protocol is used that depends on XEP-0359.
     * @return the messages that matched search criteria (possibly empty, never null).
     */
    Collection<ArchivedMessage> findMessages( Date startDate, Date endDate, JID archiveOwner, JID messageOwner, JID with, String query, XmppResultSet xmppResultSet, boolean useStableID) throws NotFoundException, DataRetrievalException;

    /**
     * Returns the conversation with the given owner, with and start time including participants and messages.
     *
     * @param owner bare jid of the conversation's owner.
     * @param with  bare jid of the communication partner.
     * @param start    exact start time
     * @return the matching conversation or <code>null</code> if none matches.
     */
    Conversation getConversation(JID owner, JID with, Date start); // TODO move to ConversationManager?
}
