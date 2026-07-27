/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.stanzaid.StanzaIDUtil;
import org.junit.Test;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the implementation of {@link ArchiveStanzaIDUtil#getArchivableStanzaXml(Message, JID...)}, which adds
 * XEP-0359 'Unique and Stable Stanza IDs' to the representation of a one-to-one message that is stored in the archive.
 */
public class ArchiveStanzaIDUtilTest {

    private static Message parse(final String xml) throws Exception {
        final Document document = DocumentHelper.parseText(xml);
        return new Message(document.getRootElement());
    }

    private static List<Element> getStanzaIdElements(final Message message) {
        return message.getElement().elements(QName.get("stanza-id", "urn:xmpp:sid:0"));
    }

    private static Message newMessage(final JID from, final JID to) {
        final Message message = new Message();
        message.setFrom(from);
        message.setTo(to);
        message.setType(Message.Type.chat);
        message.setBody("Hello!");
        return message;
    }

    /**
     * Asserts that a stanza-id is added for each of the participants of a one-to-one conversation (as a one-to-one
     * message is stored only once, but is part of the archive of both participants).
     */
    @Test
    public void testAddsStanzaIdForEachArchiveOwner() throws Exception {
        // Setup test fixture.
        final JID from = new JID("john@example.org/laptop");
        final JID to = new JID("jane@example.com/phone");
        final Message input = newMessage(from, to);

        // Execute system under test.
        final Message result = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input, from, to));

        // Verify results.
        final List<Element> stanzaIds = getStanzaIdElements(result);
        assertEquals(2, stanzaIds.size());
        assertNotNull(StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, from.toBareJID()));
        assertNotNull(StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, to.toBareJID()));
    }

    /**
     * Asserts that the same identifier value is used for each of the archive owners. This makes the value usable even
     * by clients that do not verify the 'by' attribute of the element.
     */
    @Test
    public void testUsesSameIdForEachArchiveOwner() throws Exception {
        // Setup test fixture.
        final JID from = new JID("john@example.org/laptop");
        final JID to = new JID("jane@example.com/phone");
        final Message input = newMessage(from, to);

        // Execute system under test.
        final Message result = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input, from, to));

        // Verify results.
        assertEquals(
            StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, from.toBareJID()),
            StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, to.toBareJID()));
    }

    /**
     * Asserts that the 'by' attribute of the added elements refers to the bare JID of the archive owner (which is the
     * value that the archive implementation uses when it looks up messages by their stanza-id).
     */
    @Test
    public void testUsesBareJidsAsByValue() throws Exception {
        // Setup test fixture.
        final JID from = new JID("john@example.org/laptop");
        final JID to = new JID("jane@example.com/phone");
        final Message input = newMessage(from, to);

        // Execute system under test.
        final Message result = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input, from, to));

        // Verify results.
        for (final Element stanzaId : getStanzaIdElements(result)) {
            final String by = stanzaId.attributeValue("by");
            assertTrue("Unexpected 'by' value: " + by, from.toBareJID().equals(by) || to.toBareJID().equals(by));
        }
    }

    /**
     * Asserts that the stanza that is being routed is not modified (only the representation that is archived is).
     */
    @Test
    public void testDoesNotModifyOriginalStanza() throws Exception {
        // Setup test fixture.
        final JID from = new JID("john@example.org/laptop");
        final JID to = new JID("jane@example.com/phone");
        final Message input = newMessage(from, to);
        final String before = input.toXML();

        // Execute system under test.
        ArchiveStanzaIDUtil.getArchivableStanzaXml(input, from, to);

        // Verify results.
        assertEquals(before, input.toXML());
        assertTrue(getStanzaIdElements(input).isEmpty());
    }

    /**
     * Asserts that only one stanza-id is added when both archive owners are the same entity (a user can send a message
     * to itself).
     */
    @Test
    public void testDeduplicatesArchiveOwners() throws Exception {
        // Setup test fixture.
        final JID from = new JID("john@example.org/laptop");
        final JID to = new JID("john@example.org/phone");
        final Message input = newMessage(from, to);

        // Execute system under test.
        final Message result = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input, from, to));

        // Verify results.
        assertEquals(1, getStanzaIdElements(result).size());
        assertNotNull(StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, from.toBareJID()));
    }

    /**
     * Asserts that a stanza-id that was provided by another entity (such as the one that a MUC service adds to a
     * private message that is exchanged in a chat room) is retained.
     */
    @Test
    public void testRetainsStanzaIdOfOtherEntity() throws Exception {
        // Setup test fixture.
        final JID from = new JID("john@example.org/laptop");
        final JID to = new JID("jane@example.com/phone");
        final Message input = newMessage(from, to);
        final Element roomStanzaId = input.getElement().addElement(QName.get("stanza-id", "urn:xmpp:sid:0"));
        roomStanzaId.addAttribute("id", "room-provided-id");
        roomStanzaId.addAttribute("by", "room@conference.example.org");

        // Execute system under test.
        final Message result = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input, from, to));

        // Verify results.
        assertEquals(3, getStanzaIdElements(result).size());
        assertEquals("room-provided-id", StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, "room@conference.example.org"));
    }

    /**
     * Asserts that a (spoofed) stanza-id that claims to be provided by one of the archive owners is replaced, as
     * required by XEP-0359.
     */
    @Test
    public void testReplacesSpoofedStanzaId() throws Exception {
        // Setup test fixture.
        final JID from = new JID("john@example.org/laptop");
        final JID to = new JID("jane@example.com/phone");
        final Message input = newMessage(from, to);
        final Element spoofed = input.getElement().addElement(QName.get("stanza-id", "urn:xmpp:sid:0"));
        spoofed.addAttribute("id", "spoofed-id");
        spoofed.addAttribute("by", to.toBareJID());

        // Execute system under test.
        final Message result = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input, from, to));

        // Verify results.
        assertEquals(2, getStanzaIdElements(result).size());
        for (final Element stanzaId : getStanzaIdElements(result)) {
            assertTrue("Spoofed value was not removed.", !"spoofed-id".equals(stanzaId.attributeValue("id")));
        }
    }

    /**
     * Asserts that a stanza is archived as-is when no archive owners are provided.
     */
    @Test
    public void testWithoutArchiveOwners() throws Exception {
        // Setup test fixture.
        final JID from = new JID("john@example.org/laptop");
        final JID to = new JID("jane@example.com/phone");
        final Message input = newMessage(from, to);

        // Execute system under test.
        final Message result = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input));

        // Verify results.
        assertTrue(getStanzaIdElements(result).isEmpty());
        assertNull(StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, from.toBareJID()));
    }
}
