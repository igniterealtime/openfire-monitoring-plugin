package org.jivesoftware.openfire.archive;

import org.junit.Test;
import org.xmpp.packet.JID;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConversationManagerTest {

    @Test
    public void testXmlMarshallingConversationEventTest() throws Exception {
        // Setup test fixture.
        final ConversationEvent input = ConversationEvent.chatMessageReceived(new JID("a@b.c"), new JID("d@e.f/g"), "body", "stanza", new Date(1));
        final XmlSerializer serializer = new XmlSerializer(
            Conversation.class,
            UserParticipations.class,
            ConversationParticipation.class,
            ConversationEvent.class
        );

        // Execute system under test.
        final String xml = serializer.marshall(input);
        final Object result = serializer.unmarshall(xml);

        // Verify result.
        assertTrue(result instanceof ConversationEvent);
        assertEquals("Marshalled content didn't unmarshall as equal object. Marshalled content: " + xml, input, result);
    }
}
