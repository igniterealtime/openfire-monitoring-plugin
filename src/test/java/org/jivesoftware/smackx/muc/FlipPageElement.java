package org.jivesoftware.smackx.muc;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

/**
 * The {@code <flip-page/>} element defined by XEP-0313 mam:2#extended. Including this (otherwise empty) element in
 * a MAM query requests that the server transmit the messages that match the query in reverse (newest-first) order.
 * Per the specification, this does not affect <em>which</em> messages are returned, only the order in which they
 * are sent to the client.
 * <p>
 * Not (yet) supported by Smack's own MAM implementation, so this test-only element is used to construct queries
 * directly rather than through {@code MamManager}.
 */
final class FlipPageElement implements ExtensionElement
{
    static final String ELEMENT = "flip-page";
    static final String NAMESPACE = "urn:xmpp:mam:2";

    @Override
    public String getElementName()
    {
        return ELEMENT;
    }

    @Override
    public String getNamespace()
    {
        return NAMESPACE;
    }

    @Override
    public CharSequence toXML(final XmlEnvironment xmlEnvironment)
    {
        return new XmlStringBuilder(this, xmlEnvironment).closeEmptyElement();
    }
}
