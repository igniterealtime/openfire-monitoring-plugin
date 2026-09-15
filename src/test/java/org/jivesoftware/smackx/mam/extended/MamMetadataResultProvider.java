package org.jivesoftware.smackx.mam.extended;

import org.jivesoftware.smack.packet.IqData;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.IqProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jxmpp.JxmppContext;

import java.io.IOException;
import java.text.ParseException;

/**
 * Parses a {@link MamMetadataResult} from {@code <metadata xmlns='urn:xmpp:mam:2'>} IQ responses, as defined by
 * XEP-0313 §5 "Archive metadata". Must be registered as an IQ provider (for element "metadata", namespace
 * "urn:xmpp:mam:2") before use.
 */
public final class MamMetadataResultProvider extends IqProvider<MamMetadataResult>
{
    @Override
    public MamMetadataResult parse(final XmlPullParser parser, final int initialDepth, final IqData iqData, final XmlEnvironment xmlEnvironment, final JxmppContext jxmppContext) throws XmlPullParserException, IOException, SmackParsingException, ParseException
    {
        final MamMetadataParsing.Boundaries boundaries = MamMetadataParsing.parseBoundaries(parser, initialDepth);
        return new MamMetadataResult(boundaries.start, boundaries.end);
    }
}
