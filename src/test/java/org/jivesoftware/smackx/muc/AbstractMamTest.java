package org.jivesoftware.smackx.muc;

import org.igniterealtime.smack.inttest.SmackIntegrationTestEnvironment;
import org.igniterealtime.smack.inttest.annotations.AfterClass;
import org.igniterealtime.smack.inttest.annotations.SpecificationReference;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.MessageBuilder;
import org.jivesoftware.smackx.address.packet.MultipleAddresses;
import org.jivesoftware.smackx.mam.MamManager;
import org.jivesoftware.smackx.xdata.FormField;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Base class for MAM filter tests in MUC environments.
 * Sets up a message archive once during class initialization that all tests query against.
 *
 * This class sets up three users:
 * <ol>
 * <li>A user that is the owner of a chatroom</li>
 * <li>A user that is a participant of a chatroom</li>
 * <li>A user that was, but no longer is a participant of a chatroom</li>
 * </ol>
 * For all of these users, various messages are sent during the setup phase of the test. This intends to populate the
 * various message archives that are being tested by this implementation.
 */
@SpecificationReference(document = "XEP-0313", version = "1.1.3")
public abstract class AbstractMamTest extends AbstractMultiUserChatIntegrationTest
{
    // Common test data - messages that will be sent during setup
    protected static final String DIRECT_USER1_TO_USER2_BAREJID = "Direct message from User 1 to User 2 (bare JID)";
    protected static final String DIRECT_USER1_TO_USER2_FULLJID = "Direct message from User 1 to User 2 (full JID)";
    protected static final String DIRECT_USER1_TO_USER3_BAREJID = "Direct message from User 1 to User 3 (bare JID)";
    protected static final String DIRECT_USER1_TO_USER3_FULLJID = "Direct message from User 1 to User 3 (full JID)";
    protected static final String DIRECT_USER2_TO_USER1_BAREJID = "Direct message from User 2 to User 1 (bare JID)";
    protected static final String DIRECT_USER2_TO_USER1_FULLJID = "Direct message from User 2 to User 1 (full JID)";
    protected static final String DIRECT_USER2_TO_USER3_BAREJID = "Direct message from User 2 to User 3 (bare JID)";
    protected static final String DIRECT_USER2_TO_USER3_FULLJID = "Direct message from User 2 to User 3 (full JID)";
    protected static final String DIRECT_USER3_TO_USER1_BAREJID = "Direct message from User 3 to User 1 (bare JID)";
    protected static final String DIRECT_USER3_TO_USER1_FULLJID = "Direct message from User 3 to User 1 (full JID)";
    protected static final String DIRECT_USER3_TO_USER2_BAREJID = "Direct message from User 3 to User 2 (bare JID)";
    protected static final String DIRECT_USER3_TO_USER2_FULLJID = "Direct message from User 3 to User 2 (full JID)";
    protected static final String MUC_BY_USER1 = "Public chat room message by User 1, with random word train to be used as a search needle.";
    protected static final String MUC_BY_USER2 = "Public chat room message by User 2, with random word apple to be used as a search needle.";
    protected static final String MUC_BY_USER3 = "Public chat room message by User 3, with random word river to be used as a search needle.";
    protected static final String MUC_PM_USER1_TO_USER2 = "Private chat room message from User 1 to User 2";
    protected static final String MUC_PM_USER1_TO_USER3 = "Private chat room message from User 1 to User 3";
    protected static final String MUC_PM_USER2_TO_USER1 = "Private chat room message from User 2 to User 1";
    protected static final String MUC_PM_USER2_TO_USER3 = "Private chat room message from User 2 to User 3";
    protected static final String MUC_PM_USER3_TO_USER1 = "Private chat room message from User 3 to User 1";
    protected static final String MUC_PM_USER3_TO_USER2 = "Private chat room message from User 3 to User 2";

    // Common MAM managers
    protected MamManager mucMamManagerUserOne;
    protected MamManager mucMamManagerUserTwo;
    protected MamManager mucMamManagerUserThree;
    protected MamManager personalMamManagerUserOne;
    protected MamManager personalMamManagerUserTwo;
    
    // Common MUC instances
    protected EntityBareJid mucAddress;
    protected MultiUserChat mucAsSeenByOwner;
    protected MultiUserChat mucAsSeenByParticipant;
    protected MultiUserChat mucAsSeenByNonOccupant;

    public AbstractMamTest(SmackIntegrationTestEnvironment environment) throws Exception {
        super(environment);
        
        // Create a room and send all the messages once during construction.
        createAndConfigureRoom();

        // Send all test messages once - these populate the archive for all tests
        sendAllTestMessages();

        // Finish setting up the room.
        postMessagePopulationRoomConfiguration();

        // Initialize managers that subclasses use to interact with the various message archives.
        initializeMamManagers();

        // Wait for messages to be archived
        Thread.sleep(1000);
    }

    /**
     * Create and configure a MUC room with the appropriate anonymity setting.
     * Subclasses override to specify room configuration.
     *
     * Must populate mucAddress, mucAsSeenByOwner, mucAsSeenByParticipant, mucAsSeenByNonOccupant.
     */
    protected abstract void createAndConfigureRoom() throws Exception;

    /**
     * After the room has been created and populated with messages, do any additional configuration that is required.
     * This, for example, allows occupants to be removed from the room again, while membership is being revoked.
     */
    protected abstract void postMessagePopulationRoomConfiguration() throws Exception;

    /**
     * Initialize the MAM managers that are used by the subclasses to query the various message archives.
     */
    private void initializeMamManagers() throws Exception
    {
        // Initialize MAM managers
        mucMamManagerUserOne = MamManager.getInstanceFor(mucAsSeenByOwner);
        mucMamManagerUserOne.getMamNamespace(); // Required to be able to query the archive without explicitly setting preferences.

        mucMamManagerUserTwo = MamManager.getInstanceFor(mucAsSeenByParticipant);
        mucMamManagerUserTwo.getMamNamespace(); // Required to be able to query the archive without explicitly setting preferences.

        mucMamManagerUserThree = MamManager.getInstanceFor(mucAsSeenByNonOccupant);
        mucMamManagerUserThree.getMamNamespace(); // Required to be able to query the archive without explicitly setting preferences.

        personalMamManagerUserOne = MamManager.getInstanceFor(conOne);
        personalMamManagerUserOne.getMamNamespace(); // Required to be able to query the archive without explicitly setting preferences.

        personalMamManagerUserTwo = MamManager.getInstanceFor(conTwo);
        personalMamManagerUserTwo.getMamNamespace(); // Required to be able to query the archive without explicitly setting preferences.
    }

    /**
     * Send all test messages ONCE during setup to populate the archive.
     * All tests will query against this static set of archived messages.
     */
    private void sendAllTestMessages() throws Exception
    {
        // Send direct messages (User1 -> User2)
        conOne.sendStanza(MessageBuilder.buildMessage()
            .to(conTwo.getUser().asEntityBareJid())
            .setBody(DIRECT_USER1_TO_USER2_BAREJID)
            .build());
        
        conOne.sendStanza(MessageBuilder.buildMessage()
            .to(conTwo.getUser().asFullJidOrThrow())
            .setBody(DIRECT_USER1_TO_USER2_FULLJID)
            .build());

        // Send direct messages (User1 -> User3)
        conOne.sendStanza(MessageBuilder.buildMessage()
            .to(conThree.getUser().asEntityBareJid())
            .setBody(DIRECT_USER1_TO_USER3_BAREJID)
            .build());

        conOne.sendStanza(MessageBuilder.buildMessage()
            .to(conThree.getUser().asFullJidOrThrow())
            .setBody(DIRECT_USER1_TO_USER3_FULLJID)
            .build());

        // Send direct messages (User2 -> User1)
        conTwo.sendStanza(MessageBuilder.buildMessage()
            .to(conOne.getUser().asEntityBareJid())
            .setBody(DIRECT_USER2_TO_USER1_BAREJID)
            .build());

        conTwo.sendStanza(MessageBuilder.buildMessage()
            .to(conOne.getUser().asFullJidOrThrow())
            .setBody(DIRECT_USER2_TO_USER1_FULLJID)
            .build());

        // Send direct messages (User2 -> User3)
        conTwo.sendStanza(MessageBuilder.buildMessage()
            .to(conThree.getUser().asEntityBareJid())
            .setBody(DIRECT_USER2_TO_USER3_BAREJID)
            .build());

        conTwo.sendStanza(MessageBuilder.buildMessage()
            .to(conThree.getUser().asFullJidOrThrow())
            .setBody(DIRECT_USER2_TO_USER3_FULLJID)
            .build());

        // Send direct messages (User3 -> User1)
        conThree.sendStanza(MessageBuilder.buildMessage()
            .to(conOne.getUser().asEntityBareJid())
            .setBody(DIRECT_USER3_TO_USER1_BAREJID)
            .build());

        conThree.sendStanza(MessageBuilder.buildMessage()
            .to(conOne.getUser().asFullJidOrThrow())
            .setBody(DIRECT_USER3_TO_USER1_FULLJID)
            .build());

        // Send direct messages (User3 -> User2)
        conThree.sendStanza(MessageBuilder.buildMessage()
            .to(conTwo.getUser().asEntityBareJid())
            .setBody(DIRECT_USER3_TO_USER2_BAREJID)
            .build());

        conThree.sendStanza(MessageBuilder.buildMessage()
            .to(conTwo.getUser().asFullJidOrThrow())
            .setBody(DIRECT_USER3_TO_USER2_FULLJID)
            .build());


        // Send MUC public messages
        mucAsSeenByOwner.sendMessage(MUC_BY_USER1);
        mucAsSeenByParticipant.sendMessage(MUC_BY_USER2);
        mucAsSeenByNonOccupant.sendMessage(MUC_BY_USER3);

        // Send MUC private messages (User1)
        conOne.sendStanza(MessageBuilder.buildMessage()
            .to(JidCreate.entityFullFrom(mucAsSeenByOwner.getRoom(), mucAsSeenByParticipant.getNickname()))
            .setBody(MUC_PM_USER1_TO_USER2)
            .build());

        conOne.sendStanza(MessageBuilder.buildMessage()
            .to(JidCreate.entityFullFrom(mucAsSeenByOwner.getRoom(), mucAsSeenByNonOccupant.getNickname()))
            .setBody(MUC_PM_USER1_TO_USER3)
            .build());

        // Send MUC private messages (User2)
        conTwo.sendStanza(MessageBuilder.buildMessage()
            .to(JidCreate.entityFullFrom(mucAsSeenByOwner.getRoom(), mucAsSeenByOwner.getNickname()))
            .setBody(MUC_PM_USER2_TO_USER1)
            .build());

        conTwo.sendStanza(MessageBuilder.buildMessage()
            .to(JidCreate.entityFullFrom(mucAsSeenByOwner.getRoom(), mucAsSeenByNonOccupant.getNickname()))
            .setBody(MUC_PM_USER2_TO_USER3)
            .build());

        // Send MUC private messages (User3)
        conThree.sendStanza(MessageBuilder.buildMessage()
            .to(JidCreate.entityFullFrom(mucAsSeenByOwner.getRoom(), mucAsSeenByOwner.getNickname()))
            .setBody(MUC_PM_USER3_TO_USER1)
            .build());

        conThree.sendStanza(MessageBuilder.buildMessage()
            .to(JidCreate.entityFullFrom(mucAsSeenByOwner.getRoom(), mucAsSeenByParticipant.getNickname()))
            .setBody(MUC_PM_USER3_TO_USER2)
            .build());
    }
    
    @AfterClass
    public void tearDown() throws Exception {
        // Cleanup
        if (mucAsSeenByOwner != null) {
            tryDestroy(mucAsSeenByOwner);
        }
    }

    // ===== Shared Test Implementations =====

    /**
     * Verifies that querying the MUC archive without filters returns public messages.
     */
    public void testMucMamContainsPublicMessages() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = mucMamManagerUserOne.queryArchive(mamQueryArgs);
        final MamManager.MamQuery queryResultForUserTwo = mucMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: Public messages present
        assertMamResultContains(queryResultForUserOne, MUC_BY_USER1);
        assertMamResultContains(queryResultForUserOne, MUC_BY_USER2);
        assertMamResultContains(queryResultForUserOne, MUC_BY_USER3);
        assertMamResultContains(queryResultForUserTwo, MUC_BY_USER1);
        assertMamResultContains(queryResultForUserTwo, MUC_BY_USER2);
        assertMamResultContains(queryResultForUserTwo, MUC_BY_USER3);
    }

    /**
     * Verifies that querying the MUC archive with a text filter returns the appropriate public messages.
     */
    public void testMucMamContainsPublicMessagesTextFilter() throws Exception
    {
        // Setup test fixture.
        final List<FormField> searchFields = List.of(FormField.textSingleBuilder("{urn:xmpp:fulltext:0}fulltext").setValue("train").build());
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .withAdditionalFormFields(searchFields).build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = mucMamManagerUserOne.queryArchive(mamQueryArgs);
        final MamManager.MamQuery queryResultForUserTwo = mucMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: Public messages present
        assertMamResultContains(queryResultForUserOne, MUC_BY_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_BY_USER2);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_BY_USER3);
        assertMamResultContains(queryResultForUserTwo, MUC_BY_USER1);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER2);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_BY_USER3);
    }

    /**
     * Verifies that querying the MUC archive doesn't require a user to be in the chatroom.
     */
    public void testMucMamNonOccupant() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserThree = mucMamManagerUserThree.queryArchive(mamQueryArgs);

        // Verify: Public messages present
        assertMamResultContains(queryResultForUserThree, MUC_BY_USER1);
        assertMamResultContains(queryResultForUserThree, MUC_BY_USER2);
        assertMamResultContains(queryResultForUserThree, MUC_BY_USER3);
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
    public void testMucMamContainsPublicMessagesFromOccupantByBareJidForOwner() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conTwo.getUser().asBareJid()) // real JID of the participant
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = mucMamManagerUserOne.queryArchive(mamQueryArgs);

        // Verify: Only public messages from that participant are included
        assertMamResultContains(queryResultForUserOne, MUC_BY_USER2); // message by participant
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_BY_USER1); // other participant's message
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_BY_USER3); // non-participant's message
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
    public void testMucMamContainsPublicMessagesFromOccupantByFullJidForOwner() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conTwo.getUser().asFullJidOrThrow()) // real JID of the participant
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = mucMamManagerUserOne.queryArchive(mamQueryArgs);

        // Verify: Only public messages from that participant are included
        assertMamResultContains(queryResultForUserOne, MUC_BY_USER2); // message by participant
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_BY_USER1); // other participant's message
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_BY_USER3); // non-participant's message
    }

    /**
     * Verifies that filtering a MUC archive by a specific user's real bare JID returns all (public) messages sent by
     * that user, without including other messages in rooms that allow for the real JID to be used, or an error is
     * returned for rooms in which real JIDs are not supposed to be known.
     *
     * In this test, the query is performed by a regular participant. In semi-anonymous rooms, they should not be able
     * to know the real JID of the other participant, and this query should return an error.
     *
     * This tests a scenario defined as: Retrieve all public messages in a particular MUC from a specific participant
     * (e.g.: "what did Joe say in 'projectchat' the other day?"
     */
    abstract void testMucMamContainsPublicMessagesFromOccupantByBareJidForParticipant() throws Exception;

    /**
     * Verifies that filtering a MUC archive by a specific user's full bare JID returns all (public) messages sent by
     * that user, without including other messages in rooms that allow for the real JID to be used, or an error is
     * returned for rooms in which real JIDs are not supposed to be known.
     *
     * In this test, the query is performed by a regular participant. In semi-anonymous rooms, they should not be able
     * to know the real JID of the other participant, and this query should return an error.
     *
     * This tests a scenario defined as: Retrieve all public messages in a particular MUC from a specific participant
     * (e.g.: "what did Joe say in 'projectchat' the other day?"
     */
    abstract void testMucMamContainsPublicMessagesFromOccupantByFullJidForParticipant() throws Exception;

    /**
     * Verifies that querying the MUC archive without filters does NOT return private MUC messages.
     */
    public void testMucMamDoesNotContainPrivateMucMessages() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = mucMamManagerUserOne.queryArchive(mamQueryArgs);
        final MamManager.MamQuery queryResultForUserTwo = mucMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: Private messages NOT present
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER1_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER1_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER3_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER3_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER1_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER1_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER2_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER2_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER3_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER3_TO_USER2);
    }

    /**
     * Verifies that querying the MUC archive without filters does NOT return direct (non-MUC) messages.
     */
    public void testMucMamDoesNotContainDirectMessages() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = mucMamManagerUserOne.queryArchive(mamQueryArgs);
        final MamManager.MamQuery queryResultForUserTwo = mucMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: Direct (non-MUC) messages NOT present
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER2_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER2_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER2_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER2_FULLJID);
    }

    /**
     * Verifies that MUC archive queries do not return private messages even when filtering by the occupant JID of the
     * other user.
     */
    public void testMucMamExcludesPrivateMessagesWithOccupantJidFilter() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgsForUserOne = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(mucAsSeenByOwner.getMyRoomJid()) // Room JID of the 'other' user.
            .build();
        final MamManager.MamQueryArgs mamQueryArgsForUserTwo = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(mucAsSeenByOwner.getMyRoomJid()) // Room JID of the 'other' user.
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = mucMamManagerUserOne.queryArchive(mamQueryArgsForUserOne);
        final MamManager.MamQuery queryResultForUserTwo = mucMamManagerUserTwo.queryArchive(mamQueryArgsForUserTwo);

        // Verify: Private messages NOT present
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER1_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER1_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER3_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER3_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER1_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER1_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER2_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER2_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER3_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER3_TO_USER2);
    }

    /**
     * Verifies that MUC archive queries do not return private messages even when filtering by a participant's real bare
     * JID.
     */
    public void testMucMamExcludesPrivateMessagesWithRealBareJidFilter() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgsForUserOne = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conTwo.getUser().asBareJid()) // Real JID of the 'other' user.
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = mucMamManagerUserOne.queryArchive(mamQueryArgsForUserOne);

        // Verify: Private messages NOT present
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER1_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER1_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER3_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER3_TO_USER2);
    }

    /**
     * Verifies that MUC archive queries do not return private messages even when filtering by a participant's real full
     * JID.
     */
    public void testMucMamExcludesPrivateMessagesWithRealFullJidFilter() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgsForUserOne = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conTwo.getUser().asFullJidOrThrow()) // Real JID of the 'other' user.
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = mucMamManagerUserOne.queryArchive(mamQueryArgsForUserOne);

        // Verify: Private messages NOT present
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER1_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER1_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER3_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER3_TO_USER2);
    }

    /**
     * Verifies that private MUC messages can be retrieved from the user's personal archive when no filter is applied.
     */
    // There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.
    public void testPersonalMamIncludesPrivateMessagesNoFilter() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = personalMamManagerUserOne.queryArchive(mamQueryArgs);
        final MamManager.MamQuery queryResultForUserTwo = personalMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: Both sent and received private messages should be in the personal archive (of each user)
        assertMamResultContains(queryResultForUserOne, MUC_PM_USER1_TO_USER2);
        assertMamResultContains(queryResultForUserOne, MUC_PM_USER1_TO_USER3);
        assertMamResultContains(queryResultForUserOne, MUC_PM_USER2_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER3);
        assertMamResultContains(queryResultForUserOne, MUC_PM_USER3_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER3_TO_USER2);
        assertMamResultContains(queryResultForUserTwo, MUC_PM_USER1_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER1_TO_USER3);
        assertMamResultContains(queryResultForUserTwo, MUC_PM_USER2_TO_USER1);
        assertMamResultContains(queryResultForUserTwo, MUC_PM_USER2_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER3_TO_USER1);
        assertMamResultContains(queryResultForUserTwo, MUC_PM_USER3_TO_USER2);
    }

    /**
     * Verifies that private MUC messages can be retrieved from the user's personal archive when filtering by the room
     * JID.
     */
    // There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.
    public void testPersonalMamIncludesPrivateMessagesWithRoomJidFilter() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(mucAddress)
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = personalMamManagerUserOne.queryArchive(mamQueryArgs);
        final MamManager.MamQuery queryResultForUserTwo = personalMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: Both sent and received private messages should be in the personal archive (of each user)
        assertMamResultContains(queryResultForUserOne, MUC_PM_USER1_TO_USER2);
        assertMamResultContains(queryResultForUserOne, MUC_PM_USER1_TO_USER3);
        assertMamResultContains(queryResultForUserOne, MUC_PM_USER2_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER3);
        assertMamResultContains(queryResultForUserOne, MUC_PM_USER3_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER3_TO_USER2);
        assertMamResultContains(queryResultForUserTwo, MUC_PM_USER1_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER1_TO_USER3);
        assertMamResultContains(queryResultForUserTwo, MUC_PM_USER2_TO_USER1);
        assertMamResultContains(queryResultForUserTwo, MUC_PM_USER2_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER3_TO_USER1);
        assertMamResultContains(queryResultForUserTwo, MUC_PM_USER3_TO_USER2);
    }

    /**
     * Verifies that when retrieving private MUC messages from the user's personal archive by filtering by the room
     * JID, direct (non-room) messages are not included in the result
     */
    public void testPersonalMamExcludesDirectMessagesWithRoomJidFilter() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(mucAddress)
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = personalMamManagerUserOne.queryArchive(mamQueryArgs);
        final MamManager.MamQuery queryResultForUserTwo = personalMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: Should NOT include direct messages (not from the room)
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER2_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER2_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER2_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER2_FULLJID);
    }

    /**
     * Verifies that private MUC messages can be retrieved from the user's personal archive when filtering by the
     * occupant JID (room@service/nick).
     */
    // There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.
    public void testPersonalMamIncludesPrivateMessagesWithOccupantJidFilter() throws Exception
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

        // Verify: Both sent and received private messages should be in the personal archive (of each user)
        assertMamResultContains(queryResultForUserOne, MUC_PM_USER1_TO_USER2);
        assertMamResultContains(queryResultForUserOne, MUC_PM_USER2_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER1_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER3_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER3_TO_USER2);
        assertMamResultContains(queryResultForUserTwo, MUC_PM_USER1_TO_USER2);
        assertMamResultContains(queryResultForUserTwo, MUC_PM_USER2_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER2_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER3_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER1_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER3_TO_USER1);
    }

    /**
     * Verifies that no unrelated private MUC messages are returned from the user's personal archive when filtering by
     * the occupant JID (room@service/nick) of a(nother) user.
     */
    // There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.
    public void testPersonalMamExcludesPrivateMessagesWithOccupantJidFilter() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(JidCreate.entityFullFrom(mucAddress, Resourcepart.from("UnrelatedNickname"))) // Room JID of an unrelated user.
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = personalMamManagerUserOne.queryArchive(mamQueryArgs);
        final MamManager.MamQuery queryResultForUserTwo = personalMamManagerUserTwo.queryArchive(mamQueryArgs);

        // Verify: Both sent and received private messages should be in the personal archive (of each user)
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER1_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER1_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER3_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER3_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER1_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER2_TO_USER1);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER2_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER3_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER1_TO_USER3);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER3_TO_USER1);
    }

    /**
     * Verifies that when retrieving private MUC messages from the user's personal archive by filtering by the
     * occupant JID (room@service/nick), direct (non-room) messages are not included in the result
     */
    public void testPersonalMamExcludesDirectMessagesWithOccupantJidFilter() throws Exception
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

        // Verify: Should NOT include direct messages (not from the room)
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER2_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER2_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER2_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER2_FULLJID);
    }

    /**
     * Verifies that filtering the personal archive by another user's real bare JID returns direct messages with that
     * user.
     */
    public void testPersonalMamIncludesDirectMessagesWithRealBareJidFilter() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgsForUserOne = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conTwo.getUser().asBareJid()) // Real JID of the 'other' user.
            .build();
        final MamManager.MamQueryArgs mamQueryArgsForUserTwo = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conOne.getUser().asBareJid()) // Real JID of the 'other' user.
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = personalMamManagerUserOne.queryArchive(mamQueryArgsForUserOne);
        final MamManager.MamQuery queryResultForUserTwo = personalMamManagerUserTwo.queryArchive(mamQueryArgsForUserTwo);

        // Verify: Both send and received direct messages should be in the result.
        assertMamResultContains(queryResultForUserOne, DIRECT_USER1_TO_USER2_BAREJID);
        assertMamResultContains(queryResultForUserOne, DIRECT_USER1_TO_USER2_FULLJID);
        assertMamResultContains(queryResultForUserOne, DIRECT_USER2_TO_USER1_BAREJID);
        assertMamResultContains(queryResultForUserOne, DIRECT_USER2_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER2_FULLJID);
        assertMamResultContains(queryResultForUserTwo, DIRECT_USER1_TO_USER2_BAREJID);
        assertMamResultContains(queryResultForUserTwo, DIRECT_USER1_TO_USER2_FULLJID);
        assertMamResultContains(queryResultForUserTwo, DIRECT_USER2_TO_USER1_BAREJID);
        assertMamResultContains(queryResultForUserTwo, DIRECT_USER2_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER2_FULLJID);
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
    // There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.
    public void testPersonalMamExcludesPrivateMessagesWithRealBareJidFilterForParticipant() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgsForUserTwo = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conOne.getUser().asBareJid()) // Real JID of the 'other' user.
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserTwo = personalMamManagerUserTwo.queryArchive(mamQueryArgsForUserTwo);

        // Verify: Both sent and received private messages should not be in the personal archive
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER1_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER2_TO_USER1);
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
    // There's no explicit quote in the specification that says that private MUC messages can be retrieved from the user's personal archive, but as it _is_ specified that they MUST NOT be included in the MUC archive, it can be deduced that they need to be in the personal archives.
    public void testPersonalMamExcludesPrivateMessagesWithRealBareJidFilterForOwner() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgsForUserOne = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conTwo.getUser().asBareJid()) // Real JID of the 'other' user.
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = personalMamManagerUserOne.queryArchive(mamQueryArgsForUserOne);

        // Verify: Both sent and received private messages should not be in the personal archive
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER1_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER1);
    }

    /**
     * Verifies that filtering the personal archive by another user's real full JID returns direct messages with that
     * user.
     */
    public void testPersonalMamIncludesDirectMessagesWithRealFullJidFilter() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgsForUserOne = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conTwo.getUser().asFullJidOrThrow()) // Real JID of the 'other' user.
            .build();
        final MamManager.MamQueryArgs mamQueryArgsForUserTwo = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conOne.getUser().asFullJidOrThrow()) // Real JID of the 'other' user.
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = personalMamManagerUserOne.queryArchive(mamQueryArgsForUserOne);
        final MamManager.MamQuery queryResultForUserTwo = personalMamManagerUserTwo.queryArchive(mamQueryArgsForUserTwo);

        // Verify: Both send and received direct messages should be in the result.
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER2_BAREJID);
        assertMamResultContains(queryResultForUserOne, DIRECT_USER1_TO_USER2_FULLJID);
        assertMamResultContains(queryResultForUserOne, DIRECT_USER2_TO_USER1_BAREJID);
        assertMamResultContains(queryResultForUserOne, DIRECT_USER2_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER1_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER2_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserOne, DIRECT_USER3_TO_USER2_FULLJID);
        assertMamResultContains(queryResultForUserTwo, DIRECT_USER1_TO_USER2_BAREJID);
        assertMamResultContains(queryResultForUserTwo, DIRECT_USER1_TO_USER2_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER1_BAREJID);
        assertMamResultContains(queryResultForUserTwo, DIRECT_USER2_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER1_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER3_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER2_TO_USER3_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER1_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER1_FULLJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER2_BAREJID);
        assertMamResultDoesNotContain(queryResultForUserTwo, DIRECT_USER3_TO_USER2_FULLJID);
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
    public void testPersonalMamExcludesPrivateMessagesWithRealFullJidFilterForParticipant() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgsForUserTwo = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conOne.getUser().asFullJidOrThrow()) // Real JID of the 'other' user.
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserTwo = personalMamManagerUserTwo.queryArchive(mamQueryArgsForUserTwo);

        // Verify: Both sent and received private messages should not be in the personal archive
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER1_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserTwo, MUC_PM_USER2_TO_USER1);
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
    public void testPersonalMamExcludesPrivateMessagesWithRealFullJidFilterForOwner() throws Exception
    {
        // Setup test fixture.
        final MamManager.MamQueryArgs mamQueryArgsForUserOne = MamManager.MamQueryArgs.builder()
            .limitResultsToJid(conTwo.getUser().asFullJidOrThrow()) // Real JID of the 'other' user.
            .build();

        // Execute system under test.
        final MamManager.MamQuery queryResultForUserOne = personalMamManagerUserOne.queryArchive(mamQueryArgsForUserOne);

        // Verify: Both sent and received private messages should not be in the personal archive
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER1_TO_USER2);
        assertMamResultDoesNotContain(queryResultForUserOne, MUC_PM_USER2_TO_USER1);
    }

    // ===== Common Helper Methods =====

    /**
     * Finds and retrieves a message from the given MAM query result that matches the specified body text.
     *
     * @param queryResult the MAM query result containing a list of messages to be searched
     * @param expectedBody the text of the body to find in the message
     * @return the message that matches the specified body text
     * @throws AssertionError if no message with the specified body text is found
     */
    protected Message findMessageWithBody(final MamManager.MamQuery queryResult, final String expectedBody)
    {
        return queryResult.getMessages().stream()
            .filter(message -> message.getBody().equals(expectedBody))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected message with body '" + expectedBody + "' not found in query result."));
    }

    /**
     * Verifies that the given {@link Message} contains an 'addresses' extension with an 'ofrom'
     * address matching the specified JID.
     *
     * @param message the {@link Message} to be checked for an 'addresses' extension
     * @param ofrom the {@link Jid} expected to be present as an 'ofrom' address in the message's 'addresses' extension
     */
    protected void assertMessageContainsOFrom(final Message message, final Jid ofrom)
    {
        final MultipleAddresses multipleAddresses = message.getExtension(MultipleAddresses.class);
        assertNotNull("Expected message to contain an 'addresses' extension (but it did not)", multipleAddresses);
        final boolean match = multipleAddresses.getAddressesOfType(MultipleAddresses.Type.ofrom).stream().anyMatch(address -> address.getJid().equals(ofrom));
        assertTrue("Expected message to contain an 'addresses' extension with an 'ofrom' address of '" + ofrom + "' (but it did not)", match);
    }

    /**
     * Verifies that the given {@link Message} does not contain an 'addresses' extension with an 'ofrom'
     * address matching the specified JID.
     *
     * @param message the {@link Message} to be checked for an 'addresses' extension
     * @param ofrom the {@link Jid} expected to be present as an 'ofrom' address in the message's 'addresses' extension
     */
    protected void assertMessageDoesNotContainOFrom(final Message message, final Jid ofrom)
    {
        final MultipleAddresses multipleAddresses = message.getExtension(MultipleAddresses.class);
        if (multipleAddresses != null) {
            final boolean match = multipleAddresses.getAddressesOfType(MultipleAddresses.Type.ofrom).stream().anyMatch(address -> address.getJid().equals(ofrom));
            assertFalse("Expected message to not contain an 'addresses' extension with an 'ofrom' address of '" + ofrom + "' (but it did)", match);
        }
    }

    /**
     * Asserts that a MAM query result contains a message with the specified body.
     *
     * @param mamQuery The MAM query result to check
     * @param messageBody The expected message body to search for
     */
    protected void assertMamResultContains(MamManager.MamQuery mamQuery, String messageBody)
    {
        assertTrue("Expected MAM result to contain message with body '" + messageBody + "' (but it did not)",
            mamQuery.getMessages().stream().anyMatch(msg -> msg.getBody().equals(messageBody)));
    }

    /**
     * Asserts that a MAM query result contains a message with the specified body,
     * with a custom error message prefix.
     *
     * @param message The custom message prefix to prepend to the assertion failure message
     * @param mamQuery The MAM query result to check
     * @param messageBody The expected message body to search for
     */
    private void assertMamResultContains(String message, MamManager.MamQuery mamQuery, String messageBody)
    {
        assertTrue(message + " - Expected MAM result to contain message with body '" + messageBody + "' (but it did not)",
            mamQuery.getMessages().stream().anyMatch(msg -> msg.getBody().equals(messageBody)));
    }

    /**
     * Asserts that a MAM query result <em>does not contain</em> a message with the specified body.
     *
     * @param mamQuery The MAM query result to check
     * @param messageBody The expected message body to search for
     */
    protected void assertMamResultDoesNotContain(MamManager.MamQuery mamQuery, String messageBody)
    {
        assertFalse("Expected MAM query result to NOT contain a message with body '" + messageBody + "' (but it did).",
            mamQuery.getMessages().stream().anyMatch(msg -> messageBody.equals(msg.getBody())));
    }

    /**
     * Asserts that a MAM query result <em>does not contain</em> a message with the specified body,
     * with a custom error message prefix.
     *
     * @param message The custom message prefix to prepend to the assertion failure message
     * @param mamQuery The MAM query result to check
     * @param messageBody The expected message body to search for
     */
    protected void assertMamResultDoesNotContain(String message, MamManager.MamQuery mamQuery, String messageBody)
    {
        assertFalse(message + " - Expected MAM query result to NOT contain a message with body '" + messageBody + "' (but it did).",
            mamQuery.getMessages().stream().anyMatch(msg -> messageBody.equals(msg.getBody())));
    }

    // TODO send and test for subject changes.
    // TODO Add tests where there are filters for a full JID, that are expected to _not_ return messages sent to a _different_ resource of that JID.
    // TODO Worry about the anonymity-status of the room when the message was added to the archive, vs that status when the lookup in the archive was performed.
}
