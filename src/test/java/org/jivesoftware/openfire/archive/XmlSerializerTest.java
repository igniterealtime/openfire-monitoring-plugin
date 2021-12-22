/*
 * Copyright (C) 2021 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.archive;

import org.junit.Test;
import org.xmpp.packet.JID;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the implementation of {@link XmlSerializer}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class XmlSerializerTest {

    /**
     * Checks that an instance of {@link Conversation} can be marshalled to XML, and back to an object again,
     * verifying that the resulting object is equal to the original input.
     */
    @Test
    public void testXmlMarshallingConversationTest() throws Exception {
        // Setup test fixture.
        final Map<String, UserParticipations> participations = new HashMap<>();
        final UserParticipations userParticipations = new UserParticipations(true);
        userParticipations.addParticipation(new ConversationParticipation(new Date(2), "unittest"));
        userParticipations.addParticipation(new ConversationParticipation(new Date(3)));
        final ConversationParticipation participation = new ConversationParticipation(new Date(4), "unittest");
        participation.participationEnded(new Date(5));
        userParticipations.addParticipation(participation);
        participations.put("g@d", userParticipations);
        participations.put("a@s/f", userParticipations);
        final Conversation input = new Conversation(new JID("a@b.c"), true, new Date(1), new Date(2), 8, participations);

        // Execute system under test.
        final String xml = XmlSerializer.getInstance().marshall(input);
        final Object result = XmlSerializer.getInstance().unmarshall(xml);

        // Verify result.
        assertTrue(result instanceof Conversation);
        assertEquals("Marshalled content didn't unmarshall as equal object. Marshalled content: " + xml, input, result);
    }

    /**
     * Checks that an instance of {@link UserParticipations} can be marshalled to XML, and back to an object again,
     * verifying that the resulting object is equal to the original input.
     */
    @Test
    public void testXmlMarshallingUserParticipationsTest() throws Exception {
        // Setup test fixture.
        final UserParticipations input = new UserParticipations(true);
        input.addParticipation(new ConversationParticipation(new Date(2), "unittest"));
        input.addParticipation(new ConversationParticipation(new Date(3)));
        final ConversationParticipation participation = new ConversationParticipation(new Date(4), "unittest");
        participation.participationEnded(new Date(5));
        input.addParticipation(participation);

        // Execute system under test.
        final String xml = XmlSerializer.getInstance().marshall(input);
        final Object result = XmlSerializer.getInstance().unmarshall(xml);

        // Verify result.
        assertTrue(result instanceof UserParticipations);
        assertEquals("Marshalled content didn't unmarshall as equal object. Marshalled content: " + xml, input, result);
    }

    /**
     * Checks that an instance of {@link ConversationParticipation} can be marshalled to XML, and back to an object again,
     * verifying that the resulting object is equal to the original input.
     */
    @Test
    public void testXmlMarshallingConversationParticipationTest() throws Exception {
        // Setup test fixture.
        final ConversationParticipation input = new ConversationParticipation(new Date(2), "unittest");

        // Execute system under test.
        final String xml = XmlSerializer.getInstance().marshall(input);
        final Object result = XmlSerializer.getInstance().unmarshall(xml);

        // Verify result.
        assertTrue(result instanceof ConversationParticipation);
        assertEquals("Marshalled content didn't unmarshall as equal object. Marshalled content: " + xml, input, result);
    }

    /**
     * Checks that an instance of {@link ConversationEvent} can be marshalled to XML, and back to an object again,
     * verifying that the resulting object is equal to the original input.
     */
    @Test
    public void testXmlMarshallingConversationEventTest() throws Exception {
        // Setup test fixture.
        final ConversationEvent input = ConversationEvent.chatMessageReceived(new JID("a@b.c"), new JID("d@e.f/g"), "body", "stanza", new Date(1));

        // Execute system under test.
        final String xml = XmlSerializer.getInstance().marshall(input);
        final Object result = XmlSerializer.getInstance().unmarshall(xml);

        // Verify result.
        assertTrue(result instanceof ConversationEvent);
        assertEquals("Marshalled content didn't unmarshall as equal object. Marshalled content: " + xml, input, result);
    }
}
