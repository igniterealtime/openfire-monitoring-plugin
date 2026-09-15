package org.jivesoftware.smackx.muc;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.SimpleIQ;

/**
 * A request for archive metadata, as defined by XEP-0313 §5 "Archive metadata": {@code <metadata xmlns='urn:xmpp:mam:2'/>}
 * sent as an IQ of type 'get' to an archive's address.
 * <p>
 * Not (yet) supported by Smack's own MAM implementation, so this test-only element is used to construct the request
 * directly.
 */
final class MamMetadataRequest extends SimpleIQ
{
    static final String ELEMENT = "metadata";
    static final String NAMESPACE = "urn:xmpp:mam:2";

    MamMetadataRequest()
    {
        super(ELEMENT, NAMESPACE);
        setType(IQ.Type.get);
    }
}
