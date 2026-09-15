package org.jivesoftware.smackx.muc;

import org.igniterealtime.smack.inttest.SmackIntegrationTestEnvironment;
import org.igniterealtime.smack.inttest.TestNotPossibleException;
import org.igniterealtime.smack.inttest.annotations.AfterClass;
import org.igniterealtime.smack.inttest.annotations.SmackIntegrationTest;
import org.igniterealtime.smack.inttest.annotations.SpecificationReference;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaCollector;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.filter.IQReplyFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.formtypes.FormFieldRegistry;
import org.jivesoftware.smackx.mam.MamManager;
import org.jivesoftware.smackx.mam.extended.FlipPageElement;
import org.jivesoftware.smackx.mam.extended.MamMetadataRequest;
import org.jivesoftware.smackx.mam.extended.MamMetadataResult;
import org.jivesoftware.smackx.mam.extended.MamMetadataResultProvider;
import org.jivesoftware.smackx.mam.element.MamElementFactory;
import org.jivesoftware.smackx.mam.element.MamElements.MamResultExtension;
import org.jivesoftware.smackx.mam.element.MamQueryIQ;
import org.jivesoftware.smackx.mam.element.MamVersion;
import org.jivesoftware.smackx.mam.filter.MamResultFilter;
import org.jivesoftware.smackx.rsm.packet.RSMSet;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.parts.Resourcepart;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for the 'urn:xmpp:mam:2#extended' features of XEP-0313 (before-id, after-id, ids,
 * flipped pages, and archive metadata), as applied to a MUC archive.
 * <p>
 * These features are orthogonal to room anonymity configuration, so (unlike {@link AbstractMamTest} and its
 * subclasses) this test uses a single (open, non-anonymous) room configuration rather than being repeated for
 * every anonymity variant.
 */
@SpecificationReference(document = "XEP-0313", version = "1.1.3")
public class MamExtendedFeaturesTest extends AbstractMultiUserChatIntegrationTest
{
    static {
        // Smack does not (yet) know about the mam:2#extended query fields (see FlipPageElement/MamMetadataRequest
        // javadoc). Without this, Smack's DataFormProvider falls back to assuming 'text-single' for these fields,
        // which fails to parse when the server (correctly) echoes the original query - including a multi-value
        // 'ids' field - back inside an item-not-found error response.
        FormFieldRegistry.register(MamVersion.MAM2.getNamespace(), "before-id", org.jivesoftware.smackx.xdata.FormField.Type.text_single);
        FormFieldRegistry.register(MamVersion.MAM2.getNamespace(), "after-id", org.jivesoftware.smackx.xdata.FormField.Type.text_single);
        FormFieldRegistry.register(MamVersion.MAM2.getNamespace(), "ids", org.jivesoftware.smackx.xdata.FormField.Type.list_multi);

        org.jivesoftware.smack.provider.ProviderManager.addIQProvider(MamMetadataResult.ELEMENT, MamMetadataResult.NAMESPACE, new MamMetadataResultProvider());
    }

    private static final String MSG_1 = "mam-extended message 1";
    private static final String MSG_2 = "mam-extended message 2";
    private static final String MSG_3 = "mam-extended message 3";
    private static final String MSG_4 = "mam-extended message 4";
    private static final String MSG_5 = "mam-extended message 5";

    private final EntityBareJid mucAddress;
    private final MultiUserChat mucAsSeenByOwner;
    private final MamManager mamManager;

    /**
     * Stanza IDs of MSG_1 .. MSG_5 (in that order), as assigned by the archive.
     */
    private final List<String> ids = new ArrayList<>();

    public MamExtendedFeaturesTest(final SmackIntegrationTestEnvironment environment) throws Exception
    {
        super(environment);

        mucAddress = getRandomRoom("mam-extended");
        mucAsSeenByOwner = mucManagerOne.getMultiUserChat(mucAddress);
        createMucNonAnonymous(mucAsSeenByOwner, nicknameOne);

        mamManager = MamManager.getInstanceFor(mucAsSeenByOwner);
        final String mamNamespace = mamManager.getMamNamespace(); // Required to be able to query the archive without explicitly setting preferences.

        if (!MamVersion.MAM2.getNamespace().equals(mamNamespace)) {
            throw new TestNotPossibleException("Server does not support MAM v2 for MUC archives.");
        }

        // Note: deliberately NOT gating test execution on the server advertising 'urn:xmpp:mam:2#extended' for the
        // room here (unlike the MAM v2 check above). Whether that's advertised is itself under test - see
        // testExtendedImpliesMam2() - and the functional behavior of the extended query fields is tested
        // independently of what's advertised in disco#info.

        mucAsSeenByOwner.sendMessage(MSG_1);
        mucAsSeenByOwner.sendMessage(MSG_2);
        mucAsSeenByOwner.sendMessage(MSG_3);
        mucAsSeenByOwner.sendMessage(MSG_4);
        mucAsSeenByOwner.sendMessage(MSG_5);

        // Wait for messages to be archived - without this, CI will frequently fail as the query below might race the archiving.
        Thread.sleep(500);

        final MamManager.MamQuery baseline = mamManager.queryArchive(MamManager.MamQueryArgs.builder().build());
        for (final String body : new String[] { MSG_1, MSG_2, MSG_3, MSG_4, MSG_5 }) {
            ids.add(idOfMessageWithBody(baseline, body));
        }
    }

    @AfterClass
    public void tearDown() throws Exception
    {
        if (mucAsSeenByOwner != null) {
            tryDestroy(mucAsSeenByOwner);
        }
    }

    /**
     * Verifies that a MUC archive advertises 'urn:xmpp:mam:2#extended' in its disco#info, as required for any
     * archiving entity that supports the extended query fields, flipped pages and archive metadata that are
     * exercised by the other tests in this class.
     */
    @SmackIntegrationTest(section = "7", quote = "If a server or other entity hosts archives and supports MAM queries, it MUST advertise the 'urn:xmpp:mam:2' and 'urn:xmpp:mam:2#extended' features in response to Service Discovery requests made to archiving JIDs")
    public void testMucArchiveAdvertisesExtended() throws Exception
    {
        final DiscoverInfo discoverInfo = ServiceDiscoveryManager.getInstanceFor(conOne).discoverInfo(mucAddress);
        assertTrue("Expected room '" + mucAddress + "' to advertise 'urn:xmpp:mam:2#extended'", discoverInfo.containsFeature(EXTENDED_NAMESPACE));
    }

    /**
     * Verifies that a MUC archive that advertises 'urn:xmpp:mam:2#extended' also advertises 'urn:xmpp:mam:2', as
     * required by the specification.
     */
    @SmackIntegrationTest(section = "7", quote = "The 'urn:xmpp:mam:2#extended' feature MUST NOT be advertised by a server without also advertising 'urn:xmpp:mam:2'.")
    public void testExtendedImpliesMam2() throws Exception
    {
        final DiscoverInfo discoverInfo = ServiceDiscoveryManager.getInstanceFor(conOne).discoverInfo(mucAddress);
        if (!discoverInfo.containsFeature(EXTENDED_NAMESPACE)) {
            return; // Nothing to verify: covered (and reported) by testMucArchiveAdvertisesExtended() instead.
        }
        assertTrue("Expected room '" + mucAddress + "' to advertise 'urn:xmpp:mam:2' (given that it advertises 'urn:xmpp:mam:2#extended')", discoverInfo.containsFeature(MamVersion.MAM2.getNamespace()));
    }

    /**
     * Verifies that a 'before-id' filter returns only the messages that were archived strictly before the given id,
     * excluding the anchor message itself.
     */
    @SmackIntegrationTest(section = "4.1.3", quote = "If the client has already seen some messages, it may choose to restrict its query to before and/or after messages it already knows about. This may be done through the 'before-id' and 'after-id' fields.")
    public void testBeforeId() throws Exception
    {
        final MamManager.MamQuery result = mamManager.queryArchive(MamManager.MamQueryArgs.builder()
            .withAdditionalFormField(FormField.textSingleBuilder("before-id").setValue(ids.get(2)).build()) // anchor: MSG_3
            .build());

        assertEquals("Expected exactly the messages archived before the anchor message", 2, result.getMessages().size());
        assertMamResultContains(result, MSG_1);
        assertMamResultContains(result, MSG_2);
        assertMamResultDoesNotContain(result, MSG_3);
        assertMamResultDoesNotContain(result, MSG_4);
        assertMamResultDoesNotContain(result, MSG_5);
    }

    /**
     * Verifies that an 'after-id' filter returns only the messages that were archived strictly after the given id,
     * excluding the anchor message itself.
     */
    @SmackIntegrationTest(section = "4.1.3", quote = "If the client has already seen some messages, it may choose to restrict its query to before and/or after messages it already knows about. This may be done through the 'before-id' and 'after-id' fields.")
    public void testAfterId() throws Exception
    {
        final MamManager.MamQuery result = mamManager.queryArchive(MamManager.MamQueryArgs.builder()
            .withAdditionalFormField(FormField.textSingleBuilder("after-id").setValue(ids.get(2)).build()) // anchor: MSG_3
            .build());

        assertEquals("Expected exactly the messages archived after the anchor message", 2, result.getMessages().size());
        assertMamResultDoesNotContain(result, MSG_1);
        assertMamResultDoesNotContain(result, MSG_2);
        assertMamResultDoesNotContain(result, MSG_3);
        assertMamResultContains(result, MSG_4);
        assertMamResultContains(result, MSG_5);
    }

    /**
     * Verifies that 'before-id' and 'after-id' can be combined to retrieve a range of messages strictly between two
     * known messages.
     */
    @SmackIntegrationTest(section = "4.1.3", quote = "Querying the archive for all messages between two known messages")
    public void testBeforeIdAndAfterIdCombined() throws Exception
    {
        final MamManager.MamQuery result = mamManager.queryArchive(MamManager.MamQueryArgs.builder()
            .withAdditionalFormField(FormField.textSingleBuilder("after-id").setValue(ids.get(0)).build()) // anchor: MSG_1
            .withAdditionalFormField(FormField.textSingleBuilder("before-id").setValue(ids.get(3)).build()) // anchor: MSG_4
            .build());

        assertEquals("Expected exactly the messages archived strictly between the two anchor messages", 2, result.getMessages().size());
        assertMamResultDoesNotContain(result, MSG_1);
        assertMamResultContains(result, MSG_2);
        assertMamResultContains(result, MSG_3);
        assertMamResultDoesNotContain(result, MSG_4);
        assertMamResultDoesNotContain(result, MSG_5);
    }

    /**
     * Verifies that the 'ids' filter returns exactly the requested messages (and no others), sorted in chronological
     * order regardless of the order in which the ids were listed in the request.
     */
    @SmackIntegrationTest(section = "4.1.3", quote = "If the client already knows the UID of one or more messages it wants to fetch, it can use the 'ids' field")
    public void testIds() throws Exception
    {
        final MamManager.MamQuery result = mamManager.queryArchive(MamManager.MamQueryArgs.builder()
            // Deliberately listed out of chronological order, to verify that the response is nonetheless chronological.
            .withAdditionalFormField(FormField.listMultiBuilder("ids").addValue(ids.get(4)).addValue(ids.get(0)).addValue(ids.get(2)).build())
            .build());

        assertEquals("Expected exactly the three requested messages", 3, result.getMessages().size());
        assertEquals("Expected the ids-based result to be sorted in chronological order", MSG_1, result.getMessages().get(0).getBody());
        assertEquals("Expected the ids-based result to be sorted in chronological order", MSG_3, result.getMessages().get(1).getBody());
        assertEquals("Expected the ids-based result to be sorted in chronological order", MSG_5, result.getMessages().get(2).getBody());
    }

    /**
     * Verifies that a 'before-id' value that does not correspond to a message in the archive results in an
     * item-not-found error.
     */
    @SmackIntegrationTest(section = "4.1.3", quote = "If any UID requested by the client in any of the 'before-id', 'after-id' or 'ids' form fields is not present in the archive, the server MUST return an item-not-found error in response to the query.")
    public void testBeforeIdNotFound() throws Exception
    {
        final XMPPErrorException e = assertThrows(XMPPErrorException.class, () -> mamManager.queryArchive(MamManager.MamQueryArgs.builder()
            .withAdditionalFormField(FormField.textSingleBuilder("before-id").setValue("this-id-does-not-exist-" + randomString).build())
            .build()));
        assertEquals(org.jivesoftware.smack.packet.StanzaError.Condition.item_not_found, e.getStanzaError().getCondition());
    }

    /**
     * Verifies that an 'after-id' value that does not correspond to a message in the archive results in an
     * item-not-found error.
     */
    @SmackIntegrationTest(section = "4.1.3", quote = "If any UID requested by the client in any of the 'before-id', 'after-id' or 'ids' form fields is not present in the archive, the server MUST return an item-not-found error in response to the query.")
    public void testAfterIdNotFound() throws Exception
    {
        final XMPPErrorException e = assertThrows(XMPPErrorException.class, () -> mamManager.queryArchive(MamManager.MamQueryArgs.builder()
            .withAdditionalFormField(FormField.textSingleBuilder("after-id").setValue("this-id-does-not-exist-" + randomString).build())
            .build()));
        assertEquals(org.jivesoftware.smack.packet.StanzaError.Condition.item_not_found, e.getStanzaError().getCondition());
    }

    /**
     * Verifies that an 'ids' value that does not correspond to a message in the archive results in an
     * item-not-found error, even when other requested ids do exist.
     */
    @SmackIntegrationTest(section = "4.1.3", quote = "If any UID requested by the client in any of the 'before-id', 'after-id' or 'ids' form fields is not present in the archive, the server MUST return an item-not-found error in response to the query.")
    public void testIdsNotFound() throws Exception
    {
        final XMPPErrorException e = assertThrows(XMPPErrorException.class, () -> mamManager.queryArchive(MamManager.MamQueryArgs.builder()
            .withAdditionalFormField(FormField.listMultiBuilder("ids").addValue(ids.get(0)).addValue("this-id-does-not-exist-" + randomString).build())
            .build()));
        assertEquals(org.jivesoftware.smack.packet.StanzaError.Condition.item_not_found, e.getStanzaError().getCondition());
    }

    /**
     * Verifies that including {@code <flip-page/>} in a query changes only the order in which matching messages are
     * transmitted (newest-first instead of chronological), and not which messages are returned.
     */
    @SmackIntegrationTest(section = "4.1.4", quote = "It is important to note that flipping a page does not affect what results are returned in response to the query. It only affects the order in which they are transmitted from the server to the client.")
    public void testFlipPageReversesTransmissionOrderOnly() throws Exception
    {
        // Setup test fixture: the first page (3 oldest messages) without flip-page, for comparison.
        final MamManager.MamQuery unflipped = mamManager.queryArchive(MamManager.MamQueryArgs.builder()
            .setResultPageSizeTo(3)
            .build());
        assertEquals(3, unflipped.getMessages().size());
        assertEquals(MSG_1, unflipped.getMessages().get(0).getBody());
        assertEquals(MSG_2, unflipped.getMessages().get(1).getBody());
        assertEquals(MSG_3, unflipped.getMessages().get(2).getBody());

        // Execute system under test.
        final List<Message> flipped = queryWithFlipPage(3, null);

        // Verify: same set of messages, but in reverse order.
        assertEquals(3, flipped.size());
        assertEquals("Expected flip-page to reverse the transmission order of the page", MSG_3, flipped.get(0).getBody());
        assertEquals("Expected flip-page to reverse the transmission order of the page", MSG_2, flipped.get(1).getBody());
        assertEquals("Expected flip-page to reverse the transmission order of the page", MSG_1, flipped.get(2).getBody());
    }

    /**
     * Verifies that an archive metadata query against a non-empty MUC archive returns 'start' and 'end' elements
     * describing the first and last message in the archive, using the same ids that are used in MAM query results.
     */
    @SmackIntegrationTest(section = "5", quote = "The server response includes a <metadata/> element containing information about the archive. If the archive is not empty, this element MUST include <start/> and <end/> elements, which each have an 'id' and XEP-0082 formatted 'timestamp' of the first and last messages in the archive respectively.")
    public void testArchiveMetadataNonEmpty() throws Exception
    {
        final MamMetadataRequest request = new MamMetadataRequest();
        request.setTo(mucAddress);

        final MamMetadataResult result = conOne.sendIqRequestAndWaitForResponse(request);

        assertFalse("Expected metadata for a non-empty archive to contain 'start' and 'end' boundaries", result.isEmpty());
        assertNotNull("Expected a 'start' boundary", result.getStart());
        assertNotNull("Expected an 'end' boundary", result.getEnd());
        assertEquals("Expected the 'start' boundary id to match the first archived message", ids.get(0), result.getStart().getId());
        assertEquals("Expected the 'end' boundary id to match the last archived message", ids.get(4), result.getEnd().getId());
        assertNotNull("Expected the 'start' boundary to include a timestamp", result.getStart().getTimestamp());
        assertNotNull("Expected the 'end' boundary to include a timestamp", result.getEnd().getTimestamp());
    }

    /**
     * Verifies that an archive metadata query against an empty MUC archive returns an empty {@code <metadata/>}
     * element (no 'start' or 'end' boundaries).
     */
    @SmackIntegrationTest(section = "5", quote = "If the archive is empty, the server MUST instead send an empty <metadata/> element.")
    public void testArchiveMetadataEmpty() throws Exception
    {
        // Setup test fixture: a freshly created room, to which no messages have been sent.
        final EntityBareJid emptyRoomAddress = getRandomRoom("mam-extended-empty");
        final MultiUserChat emptyRoom = mucManagerOne.getMultiUserChat(emptyRoomAddress);
        createMucNonAnonymous(emptyRoom, Resourcepart.from("owner-" + randomString));
        try {
            final MamMetadataRequest request = new MamMetadataRequest();
            request.setTo(emptyRoomAddress);

            // Execute system under test.
            final MamMetadataResult result = conOne.sendIqRequestAndWaitForResponse(request);

            // Verify.
            assertTrue("Expected metadata for an empty archive to be an empty <metadata/> element", result.isEmpty());
            assertNull(result.getStart());
            assertNull(result.getEnd());
        } finally {
            tryDestroy(emptyRoom);
        }
    }

    // ===== Helper methods =====

    private static final String EXTENDED_NAMESPACE = "urn:xmpp:mam:2#extended";

    /**
     * Finds the MAM result id (as used in before-id/after-id/ids and in {@code <result id='.../>}) of the archived
     * message with the given body.
     * <p>
     * Note: the {@code <result/>} extension (which carries the id) is attached to the carrier {@code <message/>}
     * stanza, not to the forwarded (archived) message that {@link MamManager.MamQuery#getMessages()} returns - so
     * the id has to be looked up via the parallel {@link MamManager.MamQuery#getMamResultExtensions()} list instead
     * of via the forwarded message itself.
     */
    private static String idOfMessageWithBody(final MamManager.MamQuery queryResult, final String expectedBody)
    {
        final List<Message> messages = queryResult.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            if (expectedBody.equals(messages.get(i).getBody())) {
                return queryResult.getMamResultExtensions().get(i).getId();
            }
        }
        throw new AssertionError("Expected message with body '" + expectedBody + "' not found in query result.");
    }

    private static void assertMamResultContains(final MamManager.MamQuery mamQuery, final String messageBody)
    {
        assertTrue("Expected MAM result to contain message with body '" + messageBody + "' (but it did not)",
            mamQuery.getMessages().stream().anyMatch(msg -> messageBody.equals(msg.getBody())));
    }

    private static void assertMamResultDoesNotContain(final MamManager.MamQuery mamQuery, final String messageBody)
    {
        assertFalse("Expected MAM query result to NOT contain a message with body '" + messageBody + "' (but it did).",
            mamQuery.getMessages().stream().anyMatch(msg -> messageBody.equals(msg.getBody())));
    }

    /**
     * Sends a mam:2 query for the archive, requesting a flipped page, and returns the messages in the order they
     * were transmitted by the server (i.e. not re-sorted by this method).
     * <p>
     * Reimplements (a subset of) {@code MamManager}'s internal query/collect logic, since Smack's {@code MamManager}
     * does not (yet) support constructing a query that includes {@code <flip-page/>}.
     *
     * @param max the RSM page size to request (must not be null).
     * @param afterId the RSM 'after' anchor, or null to request the first page.
     */
    private List<Message> queryWithFlipPage(final int max, final String afterId) throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException
    {
        final DataForm.Builder formBuilder = DataForm.builder();
        formBuilder.addField(FormField.buildHiddenFormType(MamVersion.MAM2.getNamespace()));
        final DataForm dataForm = formBuilder.build();

        final MamElementFactory factory = MamVersion.MAM2.newElementFactory();
        final MamQueryIQ mamQueryIQ = factory.newQueryIQ(org.jivesoftware.smack.util.StringUtils.secureUniqueRandomString(), null, dataForm);
        mamQueryIQ.setType(IQ.Type.set);
        mamQueryIQ.setTo(mucAddress);
        mamQueryIQ.addExtension(new FlipPageElement());
        mamQueryIQ.addExtension(afterId == null ? new RSMSet(max) : new RSMSet(max, afterId, RSMSet.PageDirection.after));

        final StanzaCollector finCollector = conOne.createStanzaCollector(new IQReplyFilter(mamQueryIQ, conOne));
        final StanzaCollector.Configuration resultCollectorConfig = StanzaCollector.newConfiguration()
            .setStanzaFilter(new MamResultFilter(mamQueryIQ))
            .setCollectorToReset(finCollector);

        final StanzaCollector cancelledResultCollector;
        try (StanzaCollector resultCollector = conOne.createStanzaCollector(resultCollectorConfig)) {
            conOne.sendStanza(mamQueryIQ);
            finCollector.nextResultOrThrow(); // Throws XMPPErrorException on an IQ error response.
            cancelledResultCollector = resultCollector;
        }
        // Only valid to call after the collector has been cancelled, which happens when the try-with-resources
        // block above closes it (mirroring MamManager.queryArchivePage()'s use of the same StanzaCollector idiom).

        final List<Message> messages = new ArrayList<>();
        for (final Stanza stanza : cancelledResultCollector.getCollectedStanzasAfterCancelled()) {
            final Message carrier = (Message) stanza;
            messages.add(MamResultExtension.from(carrier).getForwarded().getForwardedStanza());
        }
        return messages;
    }
}
