package org.jivesoftware.smackx.mam.extended;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jxmpp.JxmppContext;

import java.io.IOException;
import java.text.ParseException;

/**
 * Parses a {@link MamMetadataElement} from a {@code <metadata xmlns='urn:xmpp:mam:2'>} element nested inside a
 * Bind 2 (XEP-0386) {@code <bound/>} response. Must be registered as an extension element provider (for element
 * "metadata", namespace "urn:xmpp:mam:2") before use.
 */
public final class MamMetadataElementProvider extends ExtensionElementProvider<MamMetadataElement>
{
    @Override
    public MamMetadataElement parse(final XmlPullParser parser, final int initialDepth, final XmlEnvironment xmlEnvironment, final JxmppContext jxmppContext) throws XmlPullParserException, IOException, SmackParsingException, ParseException
    {
        final MamMetadataParsing.Boundaries boundaries = MamMetadataParsing.parseBoundaries(parser, initialDepth);
        return new MamMetadataElement(boundaries.start, boundaries.end);
    }
}
