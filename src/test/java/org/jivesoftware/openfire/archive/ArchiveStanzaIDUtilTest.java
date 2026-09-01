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
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the implementation of {@link ArchiveStanzaIDUtil}, which adds XEP-0359 'Unique and Stable Stanza IDs' to
 * one-to-one messages that are stored in the archive.
 */
public class ArchiveStanzaIDUtilTest {

    /**
     * The domain that, in the context of these tests, is served by the local server. Every account on this domain is
     * assumed to be a local user (which is the only kind of entity for which this implementation generates
     * identifiers).
     */
    private static final String LOCAL_DOMAIN = "local.example";

    /**
     * Substitute for the implementation that, at runtime, determines if an entity is an account that is registered
     * with the local server (which requires a running server instance).
     */
    private static final Predicate<JID> IS_LOCAL_USER = jid -> jid != null && jid.getNode() != null && LOCAL_DOMAIN.equals(jid.getDomain());

    private static final JID ROMEO = new JID("romeo@" + LOCAL_DOMAIN + "/orchard");
    private static final JID JULIET = new JID("juliet@" + LOCAL_DOMAIN + "/balcony");
    private static final JID BASTANIO = new JID("bastanio@remote.example/home");
    private static final JID ROOM = new JID("room@conference." + LOCAL_DOMAIN);

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

    private static Element addStanzaId(final Message message, final String id, final String by) {
        final Element element = message.getElement().addElement(QName.get("stanza-id", "urn:xmpp:sid:0"));
        element.addAttribute("id", id);
        element.addAttribute("by", by);
        return element;
    }

    /**
     * Asserts that the stanza that is delivered to a local recipient contains a stanza-id for the archive of that
     * recipient (which allows the recipient to correlate the message with the message in its archive).
     */
    @Test
    public void testStampsRoutedStanzaForLocalRecipient() {
        // Setup test fixture.
        final Message input = newMessage(ROMEO, JULIET);

        // Execute system under test.
        final String id = ArchiveStanzaIDUtil.addStanzaIDToRoutedStanza(input, JULIET, IS_LOCAL_USER);

        // Verify results.
        assertNotNull(id);
        assertEquals(1, getStanzaIdElements(input).size());
        assertEquals(id, StanzaIDUtil.findFirstUniqueAndStableStanzaID(input, JULIET.toBareJID()));
    }

    /**
     * Asserts that the stanza that is delivered to a local recipient does not contain a stanza-id for the archive of
     * the sender (a user should not learn the identifier that is used in the archive of another user).
     */
    @Test
    public void testRoutedStanzaDoesNotContainIdOfSender() {
        // Setup test fixture.
        final Message input = newMessage(ROMEO, JULIET);

        // Execute system under test.
        ArchiveStanzaIDUtil.addStanzaIDToRoutedStanza(input, JULIET, IS_LOCAL_USER);

        // Verify results.
        assertNull(StanzaIDUtil.findFirstUniqueAndStableStanzaID(input, ROMEO.toBareJID()));
    }

    /**
     * Asserts that no stanza-id is added to a stanza that is routed to an entity that is not a local user (this
     * implementation has no authority to generate identifiers on behalf of such an entity).
     */
    @Test
    public void testDoesNotStampRoutedStanzaForRemoteRecipient() {
        // Setup test fixture.
        final Message input = newMessage(ROMEO, BASTANIO);

        // Execute system under test.
        final String id = ArchiveStanzaIDUtil.addStanzaIDToRoutedStanza(input, BASTANIO, IS_LOCAL_USER);

        // Verify results.
        assertNull(id);
        assertTrue(getStanzaIdElements(input).isEmpty());
    }

    /**
     * Asserts that a (spoofed) stanza-id that claims to be generated by a local user is removed from a stanza that is
     * being routed, while identifiers of other entities (such as a chat room, or a remote server) are retained.
     */
    @Test
    public void testRemovesSpoofedStanzaIdsOfLocalUsersFromRoutedStanza() {
        // Setup test fixture.
        final Message input = newMessage(ROMEO, JULIET);
        addStanzaId(input, "spoofed-recipient-id", JULIET.toBareJID());
        addStanzaId(input, "spoofed-sender-id", ROMEO.toBareJID());
        addStanzaId(input, "remote-id", "remote.example");

        // Execute system under test.
        final String id = ArchiveStanzaIDUtil.addStanzaIDToRoutedStanza(input, JULIET, IS_LOCAL_USER);

        // Verify results.
        assertEquals(2, getStanzaIdElements(input).size());
        assertEquals(id, StanzaIDUtil.findFirstUniqueAndStableStanzaID(input, JULIET.toBareJID()));
        assertNotEquals("spoofed-recipient-id", id);
        assertNull(StanzaIDUtil.findFirstUniqueAndStableStanzaID(input, ROMEO.toBareJID()));
        assertEquals("remote-id", StanzaIDUtil.findFirstUniqueAndStableStanzaID(input, "remote.example"));
    }

    /**
     * Asserts that the archived representation of a stanza contains a stanza-id for each of the participants of a
     * one-to-one conversation (as a one-to-one message is stored only once, but is part of the archive of both
     * participants), reusing the identifier that was added to the stanza before it was routed.
     */
    @Test
    public void testAddsStanzaIdForEachLocalArchiveOwner() throws Exception {
        // Setup test fixture.
        final Message input = newMessage(ROMEO, JULIET);
        final String routedId = ArchiveStanzaIDUtil.addStanzaIDToRoutedStanza(input, JULIET, IS_LOCAL_USER);

        // Execute system under test.
        final Message result = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input, IS_LOCAL_USER, ROMEO, JULIET));

        // Verify results.
        assertEquals(2, getStanzaIdElements(result).size());
        assertEquals(routedId, StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, JULIET.toBareJID()));
        assertNotNull(StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, ROMEO.toBareJID()));
    }

    /**
     * Asserts that a distinct identifier value is used for each archive owner (a participant should not be able to
     * guess the identifier that is used in the archive of the other participant).
     */
    @Test
    public void testUsesDistinctIdForEachArchiveOwner() throws Exception {
        // Setup test fixture.
        final Message input = newMessage(ROMEO, JULIET);
        ArchiveStanzaIDUtil.addStanzaIDToRoutedStanza(input, JULIET, IS_LOCAL_USER);

        // Execute system under test.
        final Message result = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input, IS_LOCAL_USER, ROMEO, JULIET));

        // Verify results.
        assertNotEquals(
            StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, ROMEO.toBareJID()),
            StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, JULIET.toBareJID()));
    }

    /**
     * Asserts that the archived representation of a stanza that is addressed to a remote entity contains a stanza-id
     * for the local sender only.
     */
    @Test
    public void testDoesNotAddStanzaIdForRemoteArchiveOwner() throws Exception {
        // Setup test fixture.
        final Message input = newMessage(ROMEO, BASTANIO);

        // Execute system under test.
        final Message result = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input, IS_LOCAL_USER, ROMEO, BASTANIO));

        // Verify results.
        assertEquals(1, getStanzaIdElements(result).size());
        assertNotNull(StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, ROMEO.toBareJID()));
        assertNull(StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, BASTANIO.toBareJID()));
    }

    /**
     * Asserts that the 'by' attribute of the added elements refers to the bare JID of the archive owner (which is the
     * value that the archive implementation uses when it looks up messages by their stanza-id).
     */
    @Test
    public void testUsesBareJidsAsByValue() throws Exception {
        // Setup test fixture.
        final Message input = newMessage(ROMEO, JULIET);

        // Execute system under test.
        final Message result = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input, IS_LOCAL_USER, ROMEO, JULIET));

        // Verify results.
        for (final Element stanzaId : getStanzaIdElements(result)) {
            final String by = stanzaId.attributeValue("by");
            assertTrue("Unexpected 'by' value: " + by, ROMEO.toBareJID().equals(by) || JULIET.toBareJID().equals(by));
        }
    }

    /**
     * Asserts that the stanza that is being routed is not modified when the archived representation is created.
     */
    @Test
    public void testDoesNotModifyOriginalStanza() {
        // Setup test fixture.
        final Message input = newMessage(ROMEO, JULIET);
        final String before = input.toXML();

        // Execute system under test.
        ArchiveStanzaIDUtil.getArchivableStanzaXml(input, IS_LOCAL_USER, ROMEO, JULIET);

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
        final JID otherResource = new JID(ROMEO.toBareJID() + "/other");
        final Message input = newMessage(ROMEO, otherResource);

        // Execute system under test.
        final Message result = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input, IS_LOCAL_USER, ROMEO, otherResource));

        // Verify results.
        assertEquals(1, getStanzaIdElements(result).size());
        assertNotNull(StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, ROMEO.toBareJID()));
    }

    /**
     * Asserts that a stanza-id that was provided by another entity (such as the one that a MUC service adds to a
     * private message that is exchanged in a chat room) is retained in the archived representation.
     */
    @Test
    public void testRetainsStanzaIdOfOtherEntity() throws Exception {
        // Setup test fixture.
        final Message input = newMessage(ROMEO, JULIET);
        addStanzaId(input, "room-provided-id", ROOM.toBareJID());

        // Execute system under test.
        final Message result = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input, IS_LOCAL_USER, ROMEO, JULIET));

        // Verify results.
        assertEquals(3, getStanzaIdElements(result).size());
        assertEquals("room-provided-id", StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, ROOM.toBareJID()));
    }

    /**
     * Asserts that a stanza is archived as-is when no archive owners are provided.
     */
    @Test
    public void testWithoutArchiveOwners() throws Exception {
        // Setup test fixture.
        final Message input = newMessage(ROMEO, JULIET);

        // Execute system under test.
        final Message result = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input, IS_LOCAL_USER));

        // Verify results.
        assertTrue(getStanzaIdElements(result).isEmpty());
        assertNull(StanzaIDUtil.findFirstUniqueAndStableStanzaID(result, ROMEO.toBareJID()));
    }

    /**
     * Asserts that a message that is retrieved from the archive of one user does not contain the identifier that is
     * used in the archive of the other user.
     */
    @Test
    public void testFilterRemovesStanzaIdOfOtherLocalUser() throws Exception {
        // Setup test fixture.
        final Message input = newMessage(ROMEO, JULIET);
        ArchiveStanzaIDUtil.addStanzaIDToRoutedStanza(input, JULIET, IS_LOCAL_USER);
        final Message archived = parse(ArchiveStanzaIDUtil.getArchivableStanzaXml(input, IS_LOCAL_USER, ROMEO, JULIET));
        final String romeosId = StanzaIDUtil.findFirstUniqueAndStableStanzaID(archived, ROMEO.toBareJID());

        // Execute system under test.
        ArchiveStanzaIDUtil.filterForArchiveOwner(archived.getElement(), ROMEO.asBareJID(), IS_LOCAL_USER);

        // Verify results.
        assertEquals(1, getStanzaIdElements(archived).size());
        assertEquals(romeosId, StanzaIDUtil.findFirstUniqueAndStableStanzaID(archived, ROMEO.toBareJID()));
        assertNull(StanzaIDUtil.findFirstUniqueAndStableStanzaID(archived, JULIET.toBareJID()));
    }

    /**
     * Asserts that identifiers that were generated by entities that are not local users (such as a chat room, or a
     * remote server) are retained when a message is retrieved from an archive.
     */
    @Test
    public void testFilterRetainsStanzaIdsOfNonLocalUsers() {
        // Setup test fixture.
        final Message input = newMessage(ROMEO, JULIET);
        addStanzaId(input, "room-provided-id", ROOM.toBareJID());
        addStanzaId(input, "remote-id", "remote.example");

        // Execute system under test.
        ArchiveStanzaIDUtil.filterForArchiveOwner(input.getElement(), ROMEO.asBareJID(), IS_LOCAL_USER);

        // Verify results.
        assertEquals(2, getStanzaIdElements(input).size());
        assertEquals("room-provided-id", StanzaIDUtil.findFirstUniqueAndStableStanzaID(input, ROOM.toBareJID()));
        assertEquals("remote-id", StanzaIDUtil.findFirstUniqueAndStableStanzaID(input, "remote.example"));
    }

    /**
     * Asserts that the identifier that is used in the archive of the recipient of a stanza is not part of the Message
     * Carbons 'sent' copy that is delivered to the other resources of the sender of that stanza.
     */
    @Test
    public void testFilterRemovesRecipientsStanzaIdFromSentCarbon() {
        // Setup test fixture.
        final Message routed = newMessage(ROMEO, JULIET);
        ArchiveStanzaIDUtil.addStanzaIDToRoutedStanza(routed, JULIET, IS_LOCAL_USER);
        final JID otherResource = new JID(ROMEO.toBareJID() + "/other");
        final Message carbon = newSentCarbon(routed, otherResource);

        // Execute system under test.
        final boolean result = ArchiveStanzaIDUtil.filterForRecipient(carbon, otherResource, IS_LOCAL_USER);

        // Verify results.
        assertTrue(result);
        assertTrue(getForwardedStanzaIdElements(carbon).isEmpty());
        assertNull(StanzaIDUtil.findFirstUniqueAndStableStanzaID(getForwardedStanza(carbon), JULIET.toBareJID()));
    }

    /**
     * Asserts that the identifiers that were generated by entities that are not local users (such as a chat room, or a
     * remote server) are part of a Message Carbons 'sent' copy.
     */
    @Test
    public void testFilterRetainsNonLocalStanzaIdsInSentCarbon() {
        // Setup test fixture.
        final Message routed = newMessage(ROMEO, JULIET);
        addStanzaId(routed, "room-provided-id", ROOM.toBareJID());
        addStanzaId(routed, "remote-id", "remote.example");
        ArchiveStanzaIDUtil.addStanzaIDToRoutedStanza(routed, JULIET, IS_LOCAL_USER);
        final JID otherResource = new JID(ROMEO.toBareJID() + "/other");
        final Message carbon = newSentCarbon(routed, otherResource);

        // Execute system under test.
        ArchiveStanzaIDUtil.filterForRecipient(carbon, otherResource, IS_LOCAL_USER);

        // Verify results.
        assertEquals(2, getForwardedStanzaIdElements(carbon).size());
        assertEquals("room-provided-id", StanzaIDUtil.findFirstUniqueAndStableStanzaID(getForwardedStanza(carbon), ROOM.toBareJID()));
        assertEquals("remote-id", StanzaIDUtil.findFirstUniqueAndStableStanzaID(getForwardedStanza(carbon), "remote.example"));
    }

    /**
     * Asserts that the identifier that is used in the archive of the recipient of a stanza is retained in a stanza that
     * is forwarded to that very recipient (as is the case for a Message Carbons 'received' copy).
     */
    @Test
    public void testFilterRetainsOwnStanzaIdInForwardedStanza() {
        // Setup test fixture.
        final Message routed = newMessage(JULIET, ROMEO);
        final String id = ArchiveStanzaIDUtil.addStanzaIDToRoutedStanza(routed, ROMEO, IS_LOCAL_USER);
        final JID otherResource = new JID(ROMEO.toBareJID() + "/other");
        final Message carbon = newSentCarbon(routed, otherResource);

        // Execute system under test.
        final boolean result = ArchiveStanzaIDUtil.filterForRecipient(carbon, otherResource, IS_LOCAL_USER);

        // Verify results.
        assertFalse(result);
        assertEquals(1, getForwardedStanzaIdElements(carbon).size());
        assertEquals(id, StanzaIDUtil.findFirstUniqueAndStableStanzaID(getForwardedStanza(carbon), ROMEO.toBareJID()));
    }

    /**
     * Asserts that the stanza that is delivered to a local recipient retains the identifier that is used in the archive
     * of that recipient.
     */
    @Test
    public void testFilterRetainsStanzaIdOfRecipient() {
        // Setup test fixture.
        final Message routed = newMessage(ROMEO, JULIET);
        final String id = ArchiveStanzaIDUtil.addStanzaIDToRoutedStanza(routed, JULIET, IS_LOCAL_USER);

        // Execute system under test.
        final boolean result = ArchiveStanzaIDUtil.filterForRecipient(routed, JULIET, IS_LOCAL_USER);

        // Verify results.
        assertFalse(result);
        assertEquals(1, getStanzaIdElements(routed).size());
        assertEquals(id, StanzaIDUtil.findFirstUniqueAndStableStanzaID(routed, JULIET.toBareJID()));
    }

    /**
     * Asserts that a stanza that is delivered to an entity that is not a local user is not modified (this
     * implementation does not add identifiers of local users to such stanzas in the first place).
     */
    @Test
    public void testFilterDoesNotModifyStanzaDeliveredToNonLocalEntity() {
        // Setup test fixture.
        final Message routed = newMessage(ROMEO, BASTANIO);
        addStanzaId(routed, "juliets-id", JULIET.toBareJID());
        final String before = routed.toXML();

        // Execute system under test.
        final boolean result = ArchiveStanzaIDUtil.filterForRecipient(routed, BASTANIO, IS_LOCAL_USER);

        // Verify results.
        assertFalse(result);
        assertEquals(before, routed.toXML());
    }

    private static Message newSentCarbon(final Message original, final JID to) {
        final Message carbon = new Message();
        carbon.setType(original.getType());
        carbon.setFrom(original.getFrom().asBareJID());
        carbon.setTo(to);
        final Element sent = carbon.getElement().addElement(QName.get("sent", "urn:xmpp:carbons:2"));
        final Element forwarded = sent.addElement(QName.get("forwarded", "urn:xmpp:forward:0"));
        forwarded.add(original.getElement().createCopy());
        return carbon;
    }

    private static Message getForwardedStanza(final Message carbon) {
        return new Message(carbon.getElement()
            .element(QName.get("sent", "urn:xmpp:carbons:2"))
            .element(QName.get("forwarded", "urn:xmpp:forward:0"))
            .element("message"));
    }

    private static List<Element> getForwardedStanzaIdElements(final Message carbon) {
        return getForwardedStanza(carbon).getElement().elements(QName.get("stanza-id", "urn:xmpp:sid:0"));
    }
}
