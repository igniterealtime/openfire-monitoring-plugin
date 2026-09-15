package org.jivesoftware.smackx.mam.extended;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.SimpleIQ;

/**
 * A request for archive metadata, as defined by XEP-0313 §5 "Archive metadata": {@code <metadata xmlns='urn:xmpp:mam:2'/>}
 * sent as an IQ of type 'get' to an archive's address.
 * <p>
 * Not (yet) supported by Smack's own MAM implementation, so this test-only element is used to construct the request
 * directly.
 */
public final class MamMetadataRequest extends SimpleIQ
{
    public static final String ELEMENT = "metadata";
    public static final String NAMESPACE = "urn:xmpp:mam:2";

    public MamMetadataRequest()
    {
        super(ELEMENT, NAMESPACE);
        setType(IQ.Type.get);
    }
}
