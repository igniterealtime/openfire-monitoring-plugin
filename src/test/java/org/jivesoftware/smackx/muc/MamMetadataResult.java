package org.jivesoftware.smackx.muc;

import org.jivesoftware.smack.packet.IQ;

/**
 * The response to a {@link MamMetadataRequest}, as defined by XEP-0313 §5 "Archive metadata".
 * <p>
 * If the archive is not empty, this contains a {@link Boundary} describing the first ({@link #getStart()}) and last
 * ({@link #getEnd()}) message in the archive. If the archive is empty, both are {@code null}.
 * <p>
 * Not (yet) supported by Smack's own MAM implementation, so this test-only element, and its accompanying
 * {@link MamMetadataResultProvider}, are used to parse the response directly. The provider is registered explicitly
 * by {@link MamExtendedFeaturesTest}'s static initializer (merely declaring a variable of this type does not
 * trigger this class's own static initialization, so self-registration here would not reliably run in time).
 */
final class MamMetadataResult extends IQ
{
    static final String ELEMENT = "metadata";
    static final String NAMESPACE = "urn:xmpp:mam:2";

    private final Boundary start;
    private final Boundary end;

    MamMetadataResult(final Boundary start, final Boundary end)
    {
        super(ELEMENT, NAMESPACE);
        this.start = start;
        this.end = end;
        setType(IQ.Type.result);
    }

    Boundary getStart()
    {
        return start;
    }

    Boundary getEnd()
    {
        return end;
    }

    boolean isEmpty()
    {
        return start == null && end == null;
    }

    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(final IQChildElementXmlStringBuilder xml)
    {
        // This class is only ever used to represent a parsed, incoming response: it is never re-serialized.
        xml.setEmptyElement();
        return xml;
    }

    static final class Boundary
    {
        private final String id;
        private final String timestamp;

        Boundary(final String id, final String timestamp)
        {
            this.id = id;
            this.timestamp = timestamp;
        }

        String getId()
        {
            return id;
        }

        String getTimestamp()
        {
            return timestamp;
        }
    }
}
