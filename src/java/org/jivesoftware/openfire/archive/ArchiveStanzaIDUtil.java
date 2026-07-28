/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.archive;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Utility methods that add XEP-0359 'Unique and Stable Stanza IDs' to one-to-one messages that are stored in a message
 * archive.
 *
 * Openfire adds such identifiers to messages that are exchanged in a chat room, but not to one-to-one messages. Without
 * such an identifier, the archive cannot provide clients with stable identifiers for one-to-one messages (which,
 * historically, caused this implementation to expose database identifiers to clients instead).
 *
 * An identifier is scoped to the entity that generated it (expressed in the 'by' attribute of the element). As a
 * one-to-one message is part of the archive of both participants of the conversation, two identifiers are needed: one
 * for each archive. To prevent one participant from learning the identifier that is used in the archive of the other
 * participant, these identifiers are treated differently:
 *
 * <ul>
 *     <li>The identifier for the archive of a <em>local recipient</em> is added to the stanza before it is routed. This
 *         allows the recipient to correlate the message that is delivered to it with the message in its archive.</li>
 *     <li>The identifier for the archive of a <em>local sender</em> is added to the representation of the stanza that
 *         is stored in the archive only. It is never transmitted with the routed stanza.</li>
 *     <li>When a message is retrieved from an archive, all identifiers that were generated for other local users are
 *         removed from the stanza (see {@link #filterForArchiveOwner(Element, JID)}).</li>
 * </ul>
 *
 * No identifiers are generated for entities that are not local users: this implementation has no authority to generate
 * identifiers on their behalf (and does not archive on their behalf either).
 *
 * @see <a href="https://xmpp.org/extensions/xep-0359.html">XEP-0359</a>
 */
public class ArchiveStanzaIDUtil
{
    private static final Logger Log = LoggerFactory.getLogger( ArchiveStanzaIDUtil.class );

    private static final QName STANZA_ID = QName.get( "stanza-id", "urn:xmpp:sid:0" );

    /**
     * Name of the property that controls if this plugin generates XEP-0359 'Unique and Stable Stanza IDs' for
     * one-to-one messages that it archives.
     *
     * When disabled, the archive falls back to using database identifiers, which is the behavior of versions of this
     * plugin that predate the introduction of this functionality.
     */
    public static final String STANZA_ID_ENABLED_PROPERTY = "conversation.stanzaID.enabled";

    /**
     * The test that is used to determine if an entity is an account that is registered with this server. Package
     * visible alternatives of the methods in this class allow this to be substituted, to facilitate testing.
     */
    private static final Predicate<JID> DEFAULT_LOCAL_USER_TEST = ArchiveStanzaIDUtil::isLocalUser;

    private ArchiveStanzaIDUtil() {}

    /**
     * Returns true if this implementation should generate XEP-0359 identifiers.
     *
     * Apart from the plugin-specific configuration, the configuration that Openfire itself uses to generate stanza IDs
     * is honored.
     *
     * @return true if stanza IDs are to be generated, otherwise false.
     */
    public static boolean isEnabled()
    {
        return JiveGlobals.getBooleanProperty( STANZA_ID_ENABLED_PROPERTY, true )
            && JiveGlobals.getBooleanProperty( "xmpp.sid.enabled", true )
            && JiveGlobals.getBooleanProperty( "xmpp.sid.message.enabled", true );
    }

    /**
     * Returns true if the provided JID represents an account that is registered with (a domain of) this server.
     *
     * Only for such entities does this implementation generate stanza IDs.
     *
     * @param jid The address to evaluate (can be null).
     * @return true if the address is that of a local user account, otherwise false.
     */
    public static boolean isLocalUser( final JID jid )
    {
        if ( jid == null || jid.getNode() == null ) {
            return false;
        }
        return XMPPServer.getInstance().isLocal( jid );
    }

    /**
     * Adds a XEP-0359 'stanza-id' element for the provided archive owner to the stanza that is being routed.
     *
     * As required by XEP-0359, any pre-existing 'stanza-id' element that claims to be generated by a local user is
     * removed (irrespective of the archive owner that is provided): this implementation is the only entity that is
     * allowed to generate those. Identifiers that were generated by other entities (such as a chat room, or a remote
     * server) are retained.
     *
     * Unlike {@link #getArchivableStanzaXml(Message, JID...)}, this method modifies the stanza that is provided as an
     * argument (which is the point: the recipient of the stanza is to receive the identifier that is used in its
     * archive).
     *
     * @param message The stanza that is being routed (cannot be null).
     * @param archiveOwner The owner of the archive that the stanza will be stored in (cannot be null).
     * @return The identifier that was added, or null if no identifier was added.
     */
    public static String addStanzaIDToRoutedStanza( final Message message, final JID archiveOwner )
    {
        return addStanzaIDToRoutedStanza( message, archiveOwner, DEFAULT_LOCAL_USER_TEST );
    }

    /**
     * Adds a XEP-0359 'stanza-id' element for the provided archive owner to the stanza that is being routed, using the
     * provided test to determine if an entity is an account that is registered with this server.
     *
     * @param message The stanza that is being routed (cannot be null).
     * @param archiveOwner The owner of the archive that the stanza will be stored in (cannot be null).
     * @param localUserTest Test that determines if an entity is a local user (cannot be null).
     * @return The identifier that was added, or null if no identifier was added.
     * @see #addStanzaIDToRoutedStanza(Message, JID)
     */
    static String addStanzaIDToRoutedStanza( final Message message, final JID archiveOwner, final Predicate<JID> localUserTest )
    {
        if ( message == null ) {
            throw new IllegalArgumentException( "Argument 'message' cannot be null." );
        }
        if ( archiveOwner == null ) {
            throw new IllegalArgumentException( "Argument 'archiveOwner' cannot be null." );
        }

        try {
            if ( !isEnabled() || !localUserTest.test( archiveOwner ) ) {
                return null;
            }

            final Element parentElement = message.getElement();
            removeStanzaIDsOfLocalUsers( parentElement, message, localUserTest );

            final String id = UUID.randomUUID().toString();
            addStanzaID( parentElement, id, archiveOwner.toBareJID() );
            Log.debug( "Added stanza ID '{}' (by '{}') to a stanza that is being routed.", id, archiveOwner.toBareJID() );
            return id;
        } catch ( Exception e ) {
            Log.warn( "An exception occurred while adding a stable and unique stanza ID to a message that is being routed. The message will be processed without such an ID.", e );
            return null;
        }
    }

    /**
     * Returns the XML representation of a stanza, as it should be stored in the message archive of the provided
     * owners.
     *
     * A one-to-one message is stored only once, but is part of the archive of both participants of the conversation.
     * As XEP-0359 identifiers are scoped by the entity that generated them, a 'stanza-id' element is present for each
     * of the provided archive owners (that is a local user). A distinct identifier value is used for each of them.
     *
     * When the stanza already contains an identifier for an archive owner (which is the case for the recipient of the
     * stanza, for which the identifier was added before the stanza was routed, by
     * {@link #addStanzaIDToRoutedStanza(Message, JID)}), that identifier is reused.
     *
     * The stanza that is provided in the argument is not modified: the identifiers are added to a copy of that stanza.
     *
     * @param message The stanza to be archived (cannot be null).
     * @param archiveOwners The owners of the archives that the stanza will be stored in (cannot be null).
     * @return The XML representation of the stanza to be stored in the archive.
     */
    public static String getArchivableStanzaXml( final Message message, final JID... archiveOwners )
    {
        return getArchivableStanzaXml( message, DEFAULT_LOCAL_USER_TEST, archiveOwners );
    }

    /**
     * Returns the XML representation of a stanza, as it should be stored in the message archive of the provided
     * owners, using the provided test to determine if an entity is an account that is registered with this server.
     *
     * @param message The stanza to be archived (cannot be null).
     * @param localUserTest Test that determines if an entity is a local user (cannot be null).
     * @param archiveOwners The owners of the archives that the stanza will be stored in (cannot be null).
     * @return The XML representation of the stanza to be stored in the archive.
     * @see #getArchivableStanzaXml(Message, JID...)
     */
    static String getArchivableStanzaXml( final Message message, final Predicate<JID> localUserTest, final JID... archiveOwners )
    {
        if ( message == null ) {
            throw new IllegalArgumentException( "Argument 'message' cannot be null." );
        }

        try {
            if ( !isEnabled() ) {
                return message.toXML();
            }

            // Deduplicate (a user can send a message to itself) while maintaining a predictable order. Only local users
            // have an archive that is maintained by this implementation.
            final Set<String> owners = new LinkedHashSet<>();
            for ( final JID archiveOwner : archiveOwners ) {
                if ( localUserTest.test( archiveOwner ) ) {
                    owners.add( archiveOwner.toBareJID() );
                }
            }

            if ( owners.isEmpty() ) {
                return message.toXML();
            }

            // Do not modify the stanza that is being routed: only the archived representation is enriched.
            final Message copy = message.createCopy();
            final Element parentElement = copy.getElement();

            for ( final String owner : owners ) {
                if ( findStanzaID( parentElement, owner ) != null ) {
                    // An identifier for this archive owner was already added (before the stanza was routed).
                    continue;
                }
                addStanzaID( parentElement, UUID.randomUUID().toString(), owner );
            }

            return copy.toXML();
        } catch ( Exception e ) {
            Log.warn( "An exception occurred while adding stable and unique stanza IDs to a message that is to be archived. The message will be archived without such IDs.", e );
            return message.toXML();
        }
    }

    /**
     * Removes all XEP-0359 'stanza-id' elements that were generated for local users other than the provided archive
     * owner.
     *
     * A one-to-one message is stored in the archive only once, even though it is part of the archive of both
     * participants of the conversation. The archived representation therefore holds an identifier for each of them.
     * A participant should not learn the identifier that is used in the archive of the other participant, which is why
     * those are removed when a message is retrieved from the archive.
     *
     * Identifiers that were generated by entities that are not local users (such as chat rooms, or remote servers) are
     * retained.
     *
     * @param stanzaElement The element that represents the stanza that is retrieved from the archive (cannot be null).
     * @param archiveOwner The owner of the archive from which the stanza was retrieved (cannot be null).
     */
    public static void filterForArchiveOwner( final Element stanzaElement, final JID archiveOwner )
    {
        filterForArchiveOwner( stanzaElement, archiveOwner, DEFAULT_LOCAL_USER_TEST );
    }

    /**
     * Removes all XEP-0359 'stanza-id' elements that were generated for local users other than the provided archive
     * owner, using the provided test to determine if an entity is an account that is registered with this server.
     *
     * @param stanzaElement The element that represents the stanza that is retrieved from the archive (cannot be null).
     * @param archiveOwner The owner of the archive from which the stanza was retrieved (cannot be null).
     * @param localUserTest Test that determines if an entity is a local user (cannot be null).
     * @see #filterForArchiveOwner(Element, JID)
     */
    static void filterForArchiveOwner( final Element stanzaElement, final JID archiveOwner, final Predicate<JID> localUserTest )
    {
        if ( stanzaElement == null ) {
            throw new IllegalArgumentException( "Argument 'stanzaElement' cannot be null." );
        }
        if ( archiveOwner == null ) {
            throw new IllegalArgumentException( "Argument 'archiveOwner' cannot be null." );
        }

        try {
            final String owner = archiveOwner.toBareJID();
            final Iterator<Element> iterator = stanzaElement.elementIterator( STANZA_ID );
            while ( iterator.hasNext() ) {
                final Element element = iterator.next();
                final String by = element.attributeValue( "by" );
                if ( by == null || owner.equals( by ) ) {
                    continue;
                }
                if ( localUserTest.test( new JID( by ) ) ) {
                    iterator.remove();
                }
            }
        } catch ( Exception e ) {
            Log.warn( "An exception occurred while removing stanza IDs of other users from a message that was retrieved from the archive of '{}'.", archiveOwner, e );
        }
    }

    /**
     * Removes all XEP-0359 'stanza-id' elements that claim to be generated by a local user. Only this implementation
     * is allowed to generate those.
     *
     * @param parentElement The element that holds the 'stanza-id' elements (cannot be null).
     * @param message The message that is being processed, used for logging purposes only (can be null).
     * @param localUserTest Test that determines if an entity is a local user (cannot be null).
     */
    private static void removeStanzaIDsOfLocalUsers( final Element parentElement, final Message message, final Predicate<JID> localUserTest )
    {
        final Iterator<Element> iterator = parentElement.elementIterator( STANZA_ID );
        while ( iterator.hasNext() ) {
            final Element element = iterator.next();
            final String by = element.attributeValue( "by" );
            if ( by == null ) {
                continue;
            }
            if ( localUserTest.test( new JID( by ) ) ) {
                Log.warn( "Removing a 'stanza-id' element from an inbound stanza, as its 'by' attribute value refers to a local user. Offending stanza: {}", message );
                iterator.remove();
            }
        }
    }

    private static String findStanzaID( final Element parentElement, final String by )
    {
        for ( final Element element : parentElement.elements( STANZA_ID ) ) {
            if ( by.equals( element.attributeValue( "by" ) ) ) {
                final String id = element.attributeValue( "id" );
                if ( id != null && !id.isEmpty() ) {
                    return id;
                }
            }
        }
        return null;
    }

    private static void addStanzaID( final Element parentElement, final String id, final String by )
    {
        final Element stanzaIdElement = parentElement.addElement( STANZA_ID );
        stanzaIdElement.addAttribute( "id", id );
        stanzaIdElement.addAttribute( "by", by );
    }
}
