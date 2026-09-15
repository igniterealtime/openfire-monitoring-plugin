package org.jivesoftware.smackx.mam.extended;

import org.jivesoftware.smack.packet.IQ;

/**
 * The response to a {@link MamMetadataRequest}, as defined by XEP-0313 §5 "Archive metadata".
 * <p>
 * If the archive is not empty, this contains a {@link MamMetadataBoundary} describing the first ({@link #getStart()})
 * and last ({@link #getEnd()}) message in the archive. If the archive is empty, both are {@code null}.
 * <p>
 * Not (yet) supported by Smack's own MAM implementation, so this test-only element, and its accompanying
 * {@link MamMetadataResultProvider}, are used to parse the response directly. The provider must be registered
 * explicitly (merely declaring a variable of this type does not trigger this class's own static initialization, so
 * self-registration here would not reliably run in time).
 */
public final class MamMetadataResult extends IQ
{
    public static final String ELEMENT = "metadata";
    public static final String NAMESPACE = "urn:xmpp:mam:2";

    private final MamMetadataBoundary start;
    private final MamMetadataBoundary end;

    MamMetadataResult(final MamMetadataBoundary start, final MamMetadataBoundary end)
    {
        super(ELEMENT, NAMESPACE);
        this.start = start;
        this.end = end;
        setType(IQ.Type.result);
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
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(final IQChildElementXmlStringBuilder xml)
    {
        // This class is only ever used to represent a parsed, incoming response: it is never re-serialized.
        xml.setEmptyElement();
        return xml;
    }
}
