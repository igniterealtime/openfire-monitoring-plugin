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
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Utility methods that add XEP-0359 'Unique and Stable Stanza IDs' to messages that are about to be stored in a
 * message archive.
 *
 * Openfire adds such identifiers to messages that are exchanged in a chat room, but not to one-to-one messages. Without
 * such an identifier, the archive cannot provide clients with stable identifiers for one-to-one messages (which,
 * historically, caused this implementation to expose database identifiers to clients instead).
 *
 * @see <a href="https://xmpp.org/extensions/xep-0359.html">XEP-0359</a>
 */
public class ArchiveStanzaIDUtil
{
    private static final Logger Log = LoggerFactory.getLogger( ArchiveStanzaIDUtil.class );

    private static final QName STANZA_ID = QName.get( "stanza-id", "urn:xmpp:sid:0" );

    private ArchiveStanzaIDUtil() {}

    /**
     * Returns the XML representation of a stanza, as it should be stored in the message archive of the provided
     * owners.
     *
     * A one-to-one message is stored only once, but is part of the archive of both participants of the conversation.
     * As XEP-0359 identifiers are scoped by the entity that generated them, a 'stanza-id' element is added for each of
     * the provided archive owners. The same identifier value is used for each of them, which makes the value usable
     * even by clients that do not verify the 'by' attribute of the element.
     *
     * The stanza that is provided in the argument is not modified: the identifiers are added to a copy of that stanza.
     *
     * @param message The stanza to be archived (cannot be null).
     * @param archiveOwners The owners of the archives that the stanza will be stored in (cannot be null).
     * @return The XML representation of the stanza to be stored in the archive.
     */
    public static String getArchivableStanzaXml( final Message message, final JID... archiveOwners )
    {
        if ( message == null ) {
            throw new IllegalArgumentException( "Argument 'message' cannot be null." );
        }

        // Deduplicate (a user can send a message to itself) while maintaining a predictable order.
        final Set<String> owners = new LinkedHashSet<>();
        for ( final JID archiveOwner : archiveOwners ) {
            if ( archiveOwner != null ) {
                owners.add( archiveOwner.toBareJID() );
            }
        }

        if ( owners.isEmpty() ) {
            return message.toXML();
        }

        try {
            // Honor the same configuration options that Openfire itself uses to generate stanza IDs.
            if ( !JiveGlobals.getBooleanProperty( "xmpp.sid.enabled", true ) || !JiveGlobals.getBooleanProperty( "xmpp.sid.message.enabled", true ) ) {
                return message.toXML();
            }

            // Do not modify the stanza that is being routed: only the archived representation is enriched.
            final Message copy = message.createCopy();
            final Element parentElement = copy.getElement();

            // Stanza ID generating entities, which encounter a <stanza-id/> element where the 'by' attribute matches
            // the 'by' attribute they would otherwise set, MUST delete that element (XEP-0359).
            final Iterator<Element> existingElementIterator = parentElement.elementIterator( STANZA_ID );
            while ( existingElementIterator.hasNext() ) {
                final Element element = existingElementIterator.next();
                if ( owners.contains( element.attributeValue( "by" ) ) ) {
                    Log.warn( "Removing a 'stanza-id' element from a stanza that is to be archived, as its 'by' attribute value matches the value that we would set. Offending stanza: {}", message );
                    existingElementIterator.remove();
                }
            }

            final String id = UUID.randomUUID().toString();
            for ( final String owner : owners ) {
                final Element stanzaIdElement = parentElement.addElement( STANZA_ID );
                stanzaIdElement.addAttribute( "id", id );
                stanzaIdElement.addAttribute( "by", owner );
            }

            return copy.toXML();
        } catch ( Exception e ) {
            Log.warn( "An exception occurred while adding stable and unique stanza IDs to a message that is to be archived. The message will be archived without such IDs.", e );
            return message.toXML();
        }
    }
}
