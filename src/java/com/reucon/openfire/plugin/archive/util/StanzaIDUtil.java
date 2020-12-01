package com.reucon.openfire.plugin.archive.util;

import org.dom4j.Element;
import org.dom4j.QName;
import org.xmpp.packet.*;

import java.util.List;

/*
 * This is a partial copy of the implementation provided in Openfire 4.5.2 by
 * {@link org.jivesoftware.openfire.stanzaid.StanzaIDUtil}. The implementation is copied into this plugin to retain
 * compatibility with older versions of Openfire. This class should be removed when this plugin starts requiring
 * Openfire 4.5.2 or later.
 *
 * @see <a href="https://github.com/igniterealtime/openfire-monitoring-plugin/issues/118">Issue #118</a>
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
// FIXME Remove/Replace with org.jivesoftware.openfire.stanzaid.StanzaIDUtil when minimum server requirement becomes 4.5.2 or later.
public class StanzaIDUtil
{
    /**
     * Returns the first stable and unique stanza-id value from the packet, that is defined
     * for a particular 'by' value.
     *
     * This method does not evaluate 'origin-id' elements in the packet.
     *
     * @param packet The stanza (cannot be null).
     * @param by The 'by' value for which to return the ID (cannot be null or an empty string).
     * @return The unique and stable ID, or null if no such ID is found.
     */
    public static String findFirstUniqueAndStableStanzaID( final Packet packet, final String by )
    {
        if ( packet == null )
        {
            throw new IllegalArgumentException( "Argument 'packet' cannot be null." );
        }
        if ( by == null || by.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'by' cannot be null or an empty string." );
        }

        final List<Element> sids = packet.getElement().elements( QName.get( "stanza-id", "urn:xmpp:sid:0" ) );
        if ( sids == null )
        {
            return null;
        }

        for ( final Element sid : sids )
        {
            if ( by.equals( sid.attributeValue( "by" ) ) )
            {
                final String result = sid.attributeValue( "id" );
                if ( result != null && !result.isEmpty() )
                {
                    return result;
                }
            }
        }

        return null;
    }
}
