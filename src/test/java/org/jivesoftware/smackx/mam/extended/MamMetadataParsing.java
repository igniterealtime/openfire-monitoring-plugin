package org.jivesoftware.smackx.mam.extended;

import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;

/**
 * Shared parsing logic for a XEP-0313 §5 "Archive metadata" {@code <metadata/>} element's {@code <start/>} and
 * {@code <end/>} children, used both when {@code <metadata/>} appears as the child of a standalone IQ
 * ({@link MamMetadataResult}) and when it appears inline inside a Bind 2 (XEP-0386) {@code <bound/>} element
 * ({@link MamMetadataElement}).
 */
final class MamMetadataParsing
{
    private MamMetadataParsing()
    {
        // Utility class.
    }

    static final class Boundaries
    {
        final MamMetadataBoundary start;
        final MamMetadataBoundary end;

        Boundaries(final MamMetadataBoundary start, final MamMetadataBoundary end)
        {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Parses the {@code <start/>} and {@code <end/>} children of a {@code <metadata/>} element. The parser must be
     * positioned such that the next event is the first child of (or the closing tag of) that element.
     */
    static Boundaries parseBoundaries(final XmlPullParser parser, final int initialDepth) throws XmlPullParserException, IOException
    {
        MamMetadataBoundary start = null;
        MamMetadataBoundary end = null;

        outerloop:
        while (true) {
            final XmlPullParser.Event eventType = parser.next();
            switch (eventType) {
                case START_ELEMENT:
                    final String name = parser.getName();
                    final String id = parser.getAttributeValue("", "id");
                    final String timestamp = parser.getAttributeValue("", "timestamp");
                    if ("start".equals(name)) {
                        start = new MamMetadataBoundary(id, timestamp);
                    } else if ("end".equals(name)) {
                        end = new MamMetadataBoundary(id, timestamp);
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

        return new Boundaries(start, end);
    }
}
