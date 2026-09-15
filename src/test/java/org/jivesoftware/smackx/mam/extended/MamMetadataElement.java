package org.jivesoftware.smackx.mam.extended;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

/**
 * A {@code <metadata xmlns='urn:xmpp:mam:2'/>} element as it appears inline inside a Bind 2 (XEP-0386)
 * {@code <bound/>} response (XEP-0386 §3.2), describing the state of the user's personal archive at the time of
 * resource binding. Structurally identical to (and re-uses the parsing of) the {@code <metadata/>} payload of a
 * standalone {@link MamMetadataResult} IQ, as defined by XEP-0313 §5 "Archive metadata".
 * <p>
 * Not (yet) supported by Smack's own MAM/Bind2 implementation, so this test-only element, and its accompanying
 * {@link MamMetadataElementProvider}, are used to parse it directly. The provider must be registered explicitly
 * (merely declaring a variable of this type does not trigger this class's own static initialization, so
 * self-registration here would not reliably run in time).
 */
public final class MamMetadataElement implements ExtensionElement
{
    public static final String ELEMENT = "metadata";
    public static final String NAMESPACE = "urn:xmpp:mam:2";

    private final MamMetadataBoundary start;
    private final MamMetadataBoundary end;

    MamMetadataElement(final MamMetadataBoundary start, final MamMetadataBoundary end)
    {
        this.start = start;
        this.end = end;
    }

    public MamMetadataBoundary getStart()
    {
        return start;
    }

    public MamMetadataBoundary getEnd()
    {
        return end;
    }

    public boolean isEmpty()
    {
        return start == null && end == null;
    }

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
        // This class is only ever used to represent a parsed, incoming element: it is never re-serialized.
        return new XmlStringBuilder(this, xmlEnvironment).closeEmptyElement();
    }
}
