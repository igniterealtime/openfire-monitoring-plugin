package org.jivesoftware.smackx.muc;

import org.igniterealtime.smack.inttest.SmackIntegrationTestEnvironment;
import org.igniterealtime.smack.inttest.annotations.SmackIntegrationTest;
import org.igniterealtime.smack.inttest.annotations.SpecificationReference;
import org.jivesoftware.smackx.mam.MamManager;
import org.jivesoftware.smackx.xdata.FormField;
import org.jxmpp.jid.parts.Resourcepart;

import java.util.List;

/**
 * Integration tests for XEP-0313 MAM queries executed against a NON-ANONYMOUS MUC archive.
 *
 * All test messages are sent once during class initialization. Each test method queries
 * the same static archive with different filters or from different perspectives.
 */
@SpecificationReference(document = "XEP-0313", version = "1.1.3")
public class MamForNonAnonymousRoomTest extends AbstractMamTest
{
    public MamForNonAnonymousRoomTest(SmackIntegrationTestEnvironment environment)
        throws Exception
    {
        super(environment);
    }

    @Override
    protected void createAndConfigureRoom() throws Exception
    {
        mucAddress = getRandomRoom("mam-filter-nonanon");
        mucAsSeenByOwner = mucManagerOne.getMultiUserChat(mucAddress);

        // Create non-anonymous room
        createMucNonAnonymous(mucAsSeenByOwner, nicknameOne);

        // Make the second user join the chatroom.
        mucAsSeenByParticipant = mucManagerTwo.getMultiUserChat(mucAddress);
        mucAsSeenByParticipant.join(Resourcepart.from("participant-" + randomString));

        // Init the non-occupant objects. Initially make it join the chatroom (to populate it with messages).
        mucAsSeenByNonOccupant = mucManagerThree.getMultiUserChat(mucAddress);
        mucAsSeenByNonOccupant.join(Resourcepart.from("nonoccupant-" + randomString));
    }

    @Override
    protected void postMessagePopulationRoomConfiguration() throws Exception
    {
        mucAsSeenByNonOccupant.leave();
    }

    /**
     * Verifies that querying the MUC archive without filters returns public messages.
     */
    @SmackIntegrationTest(section = "6.1.2", quote = "A MUC archives allows a user to view the conversation within a room. All messages sent to the room that contain a <body> element SHOULD be stored [...] apart from those messages that the room rejects")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testMucMamContainsPublicMessages() throws Exception
    {
        super.testMucMamContainsPublicMessages();
    }

    /**
     * Verifies that querying the MUC archive with a text filter returns the appropriate public messages.
     */
    @SmackIntegrationTest
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testMucMamContainsPublicMessagesTextFilter() throws Exception
    {
        super.testMucMamContainsPublicMessagesTextFilter();
    }

    /**
     * Verifies that querying the MUC archive doesn't require a user to be in the chatroom.
     */
    @SmackIntegrationTest
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testMucMamNonOccupant() throws Exception
    {
        super.testMucMamNonOccupant();
    }

    /**
     * Verifies that querying the MUC archive with a text filter doesn't require a user to be in the chatroom.
     */
    @SmackIntegrationTest
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testMucMamNonOccupantTextFilter() throws Exception
    {
        super.testMucMamNonOccupantTextFilter();
    }

    /**
     * Verifies that filtering a MUC archive by a specific user's real bare JID returns all (public) messages sent by
     * that user, without including other messages in rooms that allow for the real JID to be used, or an error is
     * returned for rooms in which real JIDs are not supposed to be known.
     *
     * In this test, the query is performed by a room owner. They should always be able to know the real JID of the
     * other participant, and this query should return results (rather than an error).
     *
     * This tests a scenario defined as: Retrieve all public messages in a particular MUC from a specific participant
     * (e.g.: "what did Joe say in 'projectchat' the other day?"
     */
    @Override
    // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest
    public void testMucMamContainsPublicMessagesFromOccupantByBareJidForOwner() throws Exception
    {
        super.testMucMamContainsPublicMessagesFromOccupantByBareJidForOwner();
    }

    @Override
    // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest
    public void testMucMamContainsPublicMessagesFromOccupantByBareJidWithMatchingTextFilterForOwner() throws Exception
    {
        super.testMucMamContainsPublicMessagesFromOccupantByBareJidWithMatchingTextFilterForOwner();
    }

    @Override
    // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest
    public void testMucMamContainsPublicMessagesFromOccupantByBareJidWithNonMatchingTextFilterForOwner() throws Exception
    {
        super.testMucMamContainsPublicMessagesFromOccupantByBareJidWithNonMatchingTextFilterForOwner();
    }

    /**
     * Verifies that filtering a MUC archive by a specific user's full bare JID returns all (public) messages sent by
     * that user, without including other messages in rooms that allow for the real JID to be used, or an error is
     * returned for rooms in which real JIDs are not supposed to be known.
     *
     * In this test, the query is performed by a room owner. They should always be able to know the real JID of the
     * other participant, and this query should return results (rather than an error).
     *
     * This tests a scenario defined as: Retrieve all public messages in a particular MUC from a specific participant
     * (e.g.: "what did Joe say in 'projectchat' the other day?"
     */
    @Override
    // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest
    public void testMucMamContainsPublicMessagesFromOccupantByFullJidForOwner() throws Exception
    {
        super.testMucMamContainsPublicMessagesFromOccupantByFullJidForOwner();
    }

    @Override
    // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest
    public void testMucMamContainsPublicMessagesFromOccupantByFullJidWithMatchingTextFilterForOwner() throws Exception
    {
        super.testMucMamContainsPublicMessagesFromOccupantByFullJidWithMatchingTextFilterForOwner();
    }

    @Override
    // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest
    public void testMucMamContainsPublicMessagesFromOccupantByFullJidWithNonMatchingTextFilterForOwner() throws Exception
    {
        super.testMucMamContainsPublicMessagesFromOccupantByFullJidWithNonMatchingTextFilterForOwner();
    }

    /**
     * Verifies that filtering a MUC archive by a specific user's real bare JID returns all (public) messages sent by
     * that user, without including other messages in rooms that allow for the real JID to be used, or an error is
     * returned for rooms in which real JIDs are not supposed to be known.
     *
     * In this test, the query is performed by a room owner. They should always be able to know the real JID of the
     * other participant, and this query should return results (rather than an error).
     *
     * This tests a scenario defined as: Retrieve all public messages in a particular MUC from a specific participant
     * (e.g.: "what did Joe say in 'projectchat' the other day?"
     */
    @Override
    // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest
    public void testMucMamContainsPublicMessagesFromOccupantByBareJidForParticipant() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conOne.getUser().asBareJid()) // real JID of the owner
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserTwo = mucMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: Only public messages from that participant are included
        assertMamResultContains(queryResultForUserTwo, MUC_BY_USER1); // message by owner
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER2); // other participant's message
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER3); // non-participant's message
    }

    @Override
    // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest
    public void testMucMamContainsPublicMessagesFromOccupantByBareJidWithMatchingTextFilterForParticipant() throws Exception
    {
        // Setup test fixture.
        final List<FormField> searchFields = List.of(FormField.textSingleBuilder("{urn:xmpp:fulltext:0}fulltext").setValue("train").build());
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conOne.getUser().asBareJid()) // real JID of the owner
            .withAdditionalFormFields(searchFields)
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserTwo = mucMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: Only public messages from that participant are included
        assertMamResultContains(queryResultForUserTwo, MUC_BY_USER1); // message by owner
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER2); // other participant's message
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER3); // non-participant's message
    }

    @Override
    // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest
    public void testMucMamContainsPublicMessagesFromOccupantByBareJidWithNonMatchingTextFilterForParticipant() throws Exception
    {
        // Setup test fixture.
        final List<FormField> searchFields = List.of(FormField.textSingleBuilder("{urn:xmpp:fulltext:0}fulltext").setValue("apple").build());
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conOne.getUser().asBareJid()) // real JID of the owner
            .withAdditionalFormFields(searchFields)
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserTwo = mucMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: Only public messages from that participant are included
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER1); // message by owner
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER2); // other participant's message
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER3); // non-participant's message
    }

    /**
     * Verifies that filtering a MUC archive by a specific user's full bare JID returns all (public) messages sent by
     * that user, without including other messages in rooms that allow for the real JID to be used, or an error is
     * returned for rooms in which real JIDs are not supposed to be known.
     *
     * In this test, the query is performed by a room owner. They should always be able to know the real JID of the
     * other participant, and this query should return results (rather than an error).
     *
     * This tests a scenario defined as: Retrieve all public messages in a particular MUC from a specific participant
     * (e.g.: "what did Joe say in 'projectchat' the other day?"
     */
    @Override
    // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest
    public void testMucMamContainsPublicMessagesFromOccupantByFullJidForParticipant() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conOne.getUser().asFullJidOrThrow()) // real JID of the owner
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserTwo = mucMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: Only public messages from that participant are included
        assertMamResultContains(queryResultForUserTwo, MUC_BY_USER1); // message by owner
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER2); // other participant's message
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER3); // non-participant's message
    }

    @Override
    // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest
    public void testMucMamContainsPublicMessagesFromOccupantByFullJidWithMatchingTextFilterForParticipant() throws Exception
    {
        // Setup test fixture.
        final List<FormField> searchFields = List.of(FormField.textSingleBuilder("{urn:xmpp:fulltext:0}fulltext").setValue("train").build());
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conOne.getUser().asFullJidOrThrow()) // real JID of the owner
            .withAdditionalFormFields(searchFields)
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserTwo = mucMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: Only public messages from that participant are included
        assertMamResultContains(queryResultForUserTwo, MUC_BY_USER1); // message by owner
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER2); // other participant's message
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER3); // non-participant's message
    }

    @Override
    // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest
    public void testMucMamContainsPublicMessagesFromOccupantByFullJidWithNonMatchingTextFilterForParticipant() throws Exception
    {
        // Setup test fixture.
        final List<FormField> searchFields = List.of(FormField.textSingleBuilder("{urn:xmpp:fulltext:0}fulltext").setValue("apple").build());
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conOne.getUser().asFullJidOrThrow()) // real JID of the owner
            .withAdditionalFormFields(searchFields)
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserTwo = mucMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: Only public messages from that participant are included
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER1); // message by owner
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER2); // other participant's message
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER3); // non-participant's message
    }

    /**
     * Verifies that querying the MUC archive without filters does NOT return private MUC messages.
     */
    @SmackIntegrationTest(section = "6.1.2", quote = "A MUC archive MUST NOT include 'private message' results")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testMucMamDoesNotContainPrivateMucMessages() throws Exception
    {
        super.testMucMamDoesNotContainPrivateMucMessages();
    }

    @SmackIntegrationTest(section = "6.1.2", quote = "A MUC archive MUST NOT include 'private message' results")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testMucMamDoesNotContainPrivateMucMessagesWithTextFilter() throws Exception
    {
        super.testMucMamDoesNotContainPrivateMucMessagesWithTextFilter();
    }

    /**
     * Verifies that querying the MUC archive without filters does NOT return direct (non-MUC) messages.
     */
    @SmackIntegrationTest(quote = "Although not explicitly stated in the specification, a MUC archive must not contain non-MUC data.")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testMucMamDoesNotContainDirectMessages() throws Exception
    {
        super.testMucMamDoesNotContainDirectMessages();
    }

    @SmackIntegrationTest(quote = "Although not explicitly stated in the specification, a MUC archive must not contain non-MUC data.")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testMucMamDoesNotContainDirectMessagesWithTextFilter() throws Exception
    {
        super.testMucMamDoesNotContainDirectMessagesWithTextFilter();
    }

    /**
     * Verifies that MUC archive queries do not return private messages even when filtering by the occupant JID of the
     * other user.
     */
    @SmackIntegrationTest(section = "6.1.2", quote = "A MUC archive MUST NOT include 'private message' results")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testMucMamExcludesPrivateMessagesWithOccupantJidFilter() throws Exception
    {
        super.testMucMamExcludesPrivateMessagesWithOccupantJidFilter();
    }

    /**
     * Verifies that MUC archive queries do not return private messages even when filtering by a participant's real bare
     * JID.
     */
    @SmackIntegrationTest(section = "6.1.2", quote = "A MUC archive MUST NOT include 'private message' results")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testMucMamExcludesPrivateMessagesWithRealBareJidFilter() throws Exception
    {
        super.testMucMamExcludesPrivateMessagesWithRealBareJidFilter();
    }

    /**
     * Verifies that MUC archive queries do not return private messages even when filtering by a participant's real full
     * JID.
     */
    @SmackIntegrationTest(section = "6.1.2", quote = "A MUC archive MUST NOT include 'private message' results")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testMucMamExcludesPrivateMessagesWithRealFullJidFilter() throws Exception
    {
        super.testMucMamExcludesPrivateMessagesWithRealFullJidFilter();
    }

    /**
     * Verifies that private MUC messages can be retrieved from the user's personal archive when no filter is applied.
     */
    @SmackIntegrationTest(quote = "There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testPersonalMamIncludesPrivateMessagesNoFilter() throws Exception
    {
        super.testPersonalMamIncludesPrivateMessagesNoFilter();
    }

    @SmackIntegrationTest(quote = "There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testPersonalMamIncludesPrivateMessagesWithMatchingTextFilter() throws Exception
    {
        super.testPersonalMamIncludesPrivateMessagesWithMatchingTextFilter();
    }

    @SmackIntegrationTest(quote = "There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testPersonalMamIncludesPrivateMessagesWithNonMatchingTextFilter() throws Exception
    {
        super.testPersonalMamIncludesPrivateMessagesWithNonMatchingTextFilter();
    }

    /**
     * Verifies that private MUC messages can be retrieved from the user's personal archive when filtering by the room
     * JID.
     */
    @SmackIntegrationTest(section = "4.1.1", quote = "If a 'with' field is present in the form, it contains a JID against which to match messages. The server MUST only return messages if they match the supplied JID. A message in a user's archive matches if the JID matches either the to or from of the message.")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testPersonalMamIncludesPrivateMessagesWithRoomJidFilter() throws Exception
    {
        super.testPersonalMamIncludesPrivateMessagesWithRoomJidFilter();
    }

    /**
     * Verifies that when retrieving private MUC messages from the user's personal archive by filtering by the room
     * JID, direct (non-room) messages are not included in the result
     */
    @SmackIntegrationTest(section = "4.1.1", quote = "If a 'with' field is present in the form, it contains a JID against which to match messages. The server MUST only return messages if they match the supplied JID. A message in a user's archive matches if the JID matches either the to or from of the message.")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testPersonalMamExcludesDirectMessagesWithRoomJidFilter() throws Exception
    {
        super.testPersonalMamExcludesDirectMessagesWithRoomJidFilter();
    }

    /**
     * Verifies that private MUC messages can be retrieved from the user's personal archive when filtering by the
     * occupant JID (room@service/nick).
     */
    @SmackIntegrationTest(section = "4.1.1", quote = "If a 'with' field is present in the form, it contains a JID against which to match messages. The server MUST only return messages if they match the supplied JID. A message in a user's archive matches if the JID matches either the to or from of the message.")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    // There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.
    public void testPersonalMamIncludesPrivateMessagesWithOccupantJidFilter() throws Exception
    {
        super.testPersonalMamIncludesPrivateMessagesWithOccupantJidFilter();
    }

    /**
     * Verifies that no unrelated private MUC messages are returned from the user's personal archive when filtering by
     * the occupant JID (room@service/nick) of a(nother) user.
     */
    @SmackIntegrationTest(section = "4.1.1", quote = "If a 'with' field is present in the form, it contains a JID against which to match messages. The server MUST only return messages if they match the supplied JID. A message in a user's archive matches if the JID matches either the to or from of the message.")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    // There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.
    public void testPersonalMamExcludesPrivateMessagesWithOccupantJidFilter() throws Exception
    {
        super.testPersonalMamExcludesPrivateMessagesWithOccupantJidFilter();
    }

    /**
     * Verifies that when retrieving private MUC messages from the user's personal archive by filtering by the
     * occupant JID (room@service/nick), direct (non-room) messages are not included in the result
     */
    @SmackIntegrationTest(section = "4.1.1", quote = "If a 'with' field is present in the form, it contains a JID against which to match messages. The server MUST only return messages if they match the supplied JID. A message in a user's archive matches if the JID matches either the to or from of the message.")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testPersonalMamExcludesDirectMessagesWithOccupantJidFilter() throws Exception
    {
        super.testPersonalMamExcludesDirectMessagesWithOccupantJidFilter();
    }

    /**
     * Verifies that filtering the personal archive by another user's real bare JID returns direct messages with that
     * user.
     */
    @SmackIntegrationTest(section = "4.1.1", quote = "If a 'with' field is present in the form, it contains a JID against which to match messages. The server MUST only return messages if they match the supplied JID. A message in a user's archive matches if the JID matches either the to or from of the message.")
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testPersonalMamIncludesDirectMessagesWithRealBareJidFilter() throws Exception
    {
        super.testPersonalMamIncludesDirectMessagesWithRealBareJidFilter();
    }

    /**
     * Verifies that filtering the personal archive by another user's real bare JID <em>does not</em> return MUC private
     * messages exchanged with that user, when the user that performed the query is not a power
     * user (eg: not an owner).
     *
     * Rationale: XEP-0313 defines that a filter value is to be applied to the stanza to/from attribute value. But, even
     * besides that: In some configurations (eg: a non-anonymous room) the real JID of occupants is visible, which would,
     * strictly speaking, allow for the private messages to be found by the query that's tested here. However, that
     * opens the door for very confusing UX - in some configurations, certain messages would be returned, while in other
     * configurations, similar messages would _not_ be returned. For consistency, private messages (exchanged in a MUC)
     * should only be returned when filtering by the room JID.
     */
    @SmackIntegrationTest(section = "4.1.1", quote = "If a 'with' field is present in the form, it contains a JID against which to match messages. The server MUST only return messages if they match the supplied JID. A message in a user's archive matches if the JID matches either the to or from of the message.")
    // There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    public void testPersonalMamExcludesPrivateMessagesWithRealBareJidFilterForParticipant() throws Exception
    {
        super.testPersonalMamExcludesPrivateMessagesWithRealBareJidFilterForParticipant();
    }

    /**
     * Verifies that filtering the personal archive by another user's real bare JID <em>does not</em> return MUC private
     * messages exchanged with that user, when the user that performed the query is a power
     * user (eg: an owner).
     *
     * Rationale: XEP-0313 defines that a filter value is to be applied to the stanza to/from attribute value. But, even
     * besides that: In some configurations (eg: a non-anonymous room) the real JID of occupants is visible, which would,
     * strictly speaking, allow for the private messages to be found by the query that's tested here. However, that
     * opens the door for very confusing UX - in some configurations, certain messages would be returned, while in other
     * configurations, similar messages would _not_ be returned. For consistency, private messages (exchanged in a MUC)
     * should only be returned when filtering by the room JID.
     */
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest(section = "4.1.1", quote = "If a 'with' field is present in the form, it contains a JID against which to match messages. The server MUST only return messages if they match the supplied JID. A message in a user's archive matches if the JID matches either the to or from of the message.")
    // There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.
    public void testPersonalMamExcludesPrivateMessagesWithRealBareJidFilterForOwner() throws Exception
    {
        super.testPersonalMamExcludesPrivateMessagesWithRealBareJidFilterForOwner();
    }

    /**
     * Verifies that filtering the personal archive by another user's real full JID returns direct messages with that
     * user.
     */
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest()
    public void testPersonalMamIncludesDirectMessagesWithRealFullJidFilter() throws Exception
    {
        super.testPersonalMamIncludesDirectMessagesWithRealFullJidFilter();
    }

    /**
     * Verifies that filtering the personal archive by another user's real full JID <em>does not</em> return MUC private
     * messages exchanged with that user, when the user that performed the query is not a power
     * user (eg: not an owner).
     *
     * Rationale: XEP-0313 defines that a filter value is to be applied to the stanza to/from attribute value. But, even
     * besides that: In some configurations (eg: a non-anonymous room) the real JID of occupants is visible, which would,
     * strictly speaking, allow for the private messages to be found by the query that's tested here. However, that
     * opens the door for very confusing UX - in some configurations, certain messages would be returned, while in other
     * configurations, similar messages would _not_ be returned. For consistency, private messages (exchanged in a MUC)
     * should only be returned when filtering by the room JID.
     */
    // There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest(section = "4.1.1", quote = "If a 'with' field is present in the form, it contains a JID against which to match messages. The server MUST only return messages if they match the supplied JID. A message in a user's archive matches if the JID matches either the to or from of the message.")
    public void testPersonalMamExcludesPrivateMessagesWithRealFullJidFilterForParticipant() throws Exception
    {
        super.testPersonalMamExcludesPrivateMessagesWithRealFullJidFilterForParticipant();
    }

    /**
     * Verifies that filtering the personal archive by another user's real full JID <em>does not</em> return MUC private
     * messages exchanged with that user, when the user that performed the query is a power
     * user (eg: an owner).
     *
     * Rationale: XEP-0313 defines that a filter value is to be applied to the stanza to/from attribute value. But, even
     * besides that: In some configurations (eg: a non-anonymous room) the real JID of occupants is visible, which would,
     * strictly speaking, allow for the private messages to be found by the query that's tested here. However, that
     * opens the door for very confusing UX - in some configurations, certain messages would be returned, while in other
     * configurations, similar messages would _not_ be returned. For consistency, private messages (exchanged in a MUC)
     * should only be returned when filtering by the room JID.
     */
    // There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.
    @Override // Sadly, the SINT test framework requires a method in the child class. Working around code duplication using this override that delegates to the parent class.
    @SmackIntegrationTest(section = "4.1.1", quote = "If a 'with' field is present in the form, it contains a JID against which to match messages. The server MUST only return messages if they match the supplied JID. A message in a user's archive matches if the JID matches either the to or from of the message.")
    public void testPersonalMamExcludesPrivateMessagesWithRealFullJidFilterForOwner() throws Exception
    {
        super.testPersonalMamExcludesPrivateMessagesWithRealFullJidFilterForOwner();
    }

    /**
     * Verifies that archived private messages that were shared in a non-anonymous room contain the real JID of the
     * sender.
     */
    @SmackIntegrationTest()
    public void testPrivateMessagesContainRealJid() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgsForUserOne = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(mucAsSeenByParticipant.getMyRoomJid()) // Room JID of the 'other' user.
            .build();
        final MamManager.MamQueryArgs mamQueryArgsForUserTwo = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(mucAsSeenByOwner.getMyRoomJid()) // Room JID of the 'other' user.
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = personalMamManagerUserOne.queryArchive(mamQueryArgsForUserOne);
        final MamManager.MamQuery queryResultForUserTwo = personalMamManagerUserTwo.queryArchive(mamQueryArgsForUserTwo);

        // Verify: sent and received private messages should contain the 'ofrom' of the sender.
        assertMessageContainsOFrom(findMessageWithBody(queryResultForUserOne, MUC_PM_USER1_TO_USER2), conOne.getUser());
        assertMessageContainsOFrom(findMessageWithBody(queryResultForUserOne, MUC_PM_USER2_TO_USER1), conTwo.getUser());
        assertMessageContainsOFrom(findMessageWithBody(queryResultForUserTwo, MUC_PM_USER1_TO_USER2), conOne.getUser());
        assertMessageContainsOFrom(findMessageWithBody(queryResultForUserTwo, MUC_PM_USER2_TO_USER1), conTwo.getUser());
    }

    /**
     * Verifies that archived public messages that were shared in a non-anonymous room contain the real JID of the
     * sender.
     */
    @SmackIntegrationTest()
    public void testMucMessagesContainRealJid() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = mucMamManagerUserOne.queryArchive(mamQueryArgs);
        final MamManager.MamQuery queryResultForUserTwo = mucMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: sent and received public messages should contain the 'ofrom' of the sender.
        assertMessageContainsOFrom(findMessageWithBody(queryResultForUserOne, MUC_BY_USER1), conOne.getUser());
        assertMessageContainsOFrom(findMessageWithBody(queryResultForUserOne, MUC_BY_USER2), conTwo.getUser());
        assertMessageContainsOFrom(findMessageWithBody(queryResultForUserTwo, MUC_BY_USER1), conOne.getUser());
        assertMessageContainsOFrom(findMessageWithBody(queryResultForUserTwo, MUC_BY_USER2), conTwo.getUser());
    }
}
