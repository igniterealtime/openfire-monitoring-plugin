package org.jivesoftware.smackx.muc;

import org.jivesoftware.smack.packet.IqData;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.IqProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jxmpp.JxmppContext;

import java.io.IOException;

/**
 * Parses a {@link MamMetadataResult} from {@code <metadata xmlns='urn:xmpp:mam:2'>} IQ responses, as defined by
 * XEP-0313 §5 "Archive metadata".
 */
final class MamMetadataResultProvider extends IqProvider<MamMetadataResult>
{
    @Override
    public MamMetadataResult parse(final XmlPullParser parser, final int initialDepth, final IqData iqData, final XmlEnvironment xmlEnvironment, final JxmppContext jxmppContext) throws XmlPullParserException, IOException, SmackParsingException
    {
        MamMetadataResult.Boundary start = null;
        MamMetadataResult.Boundary end = null;

        outerloop:
        while (true) {
            final XmlPullParser.Event eventType = parser.next();
            switch (eventType) {
                case START_ELEMENT:
                    final String name = parser.getName();
                    final String id = parser.getAttributeValue("", "id");
                    final String timestamp = parser.getAttributeValue("", "timestamp");
                    if ("start".equals(name)) {
                        start = new MamMetadataResult.Boundary(id, timestamp);
                    } else if ("end".equals(name)) {
                        end = new MamMetadataResult.Boundary(id, timestamp);
                    }
                    break;
                case END_ELEMENT:
                    if (parser.getDepth() == initialDepth) {
                        break outerloop;
                    }
                    break;
                default:
                    // Catch all for incomplete switch (MissingCasesInEnumSwitch) statement.
                    break;
            }
        }

        return new MamMetadataResult(start, end);
    }
}
