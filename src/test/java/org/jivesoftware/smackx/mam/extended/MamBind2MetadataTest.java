package org.jivesoftware.smackx.mam.extended;

import org.igniterealtime.smack.inttest.AbstractSmackSpecificLowLevelIntegrationTest;
import org.igniterealtime.smack.inttest.SmackIntegrationTestEnvironment;
import org.igniterealtime.smack.inttest.TestNotPossibleException;
import org.igniterealtime.smack.inttest.annotations.SmackIntegrationTest;
import org.igniterealtime.smack.inttest.annotations.SpecificationReference;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.bind2.Bind2Module;
import org.jivesoftware.smack.bind2.Bind2ModuleDescriptor;
import org.jivesoftware.smack.bind2.element.Bind2Elements;
import org.jivesoftware.smack.c2s.ModularXmppClientToServerConnection;
import org.jivesoftware.smack.packet.MessageBuilder;
import org.jivesoftware.smack.packet.XmlElement;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.sasl.packet.Sasl2Feature;
import org.jivesoftware.smackx.mam.MamManager;
import org.jivesoftware.smackx.mam.element.MamElements.MamResultExtension;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Localpart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for the mam:2 {@code <metadata/>} element that XEP-0386 (Bind 2) §3.2 says a server SHOULD
 * include in the {@code <bound/>} response of a resource bind, describing the state of the user's <em>personal</em>
 * archive at the time of binding.
 * <p>
 * There is no MUC equivalent of this: Bind 2 binds a resource (and, with it, exposes the state of the personal
 * archive of the account being bound), so this feature is inherently personal-archive-only.
 */
@SpecificationReference(document = "XEP-0386", version = "1.1.0")
public class MamBind2MetadataTest extends AbstractSmackSpecificLowLevelIntegrationTest<ModularXmppClientToServerConnection>
{
    static {
        // Smack does not (yet) know how to parse mam:2 <metadata/> when it appears inline inside a Bind 2 <bound/>
        // response, so this test-only provider is registered to do so directly.
        ProviderManager.addExtensionProvider(MamMetadataElement.ELEMENT, MamMetadataElement.NAMESPACE, new MamMetadataElementProvider());
    }

    @SuppressWarnings("this-escape")
    public MamBind2MetadataTest(final SmackIntegrationTestEnvironment environment) throws Exception
    {
        super(environment, ModularXmppClientToServerConnection.class);
        final ModularXmppClientToServerConnection connection = getSpecificUnconnectedConnection();
        try {
            connection.connect();
            final Sasl2Feature sasl2Feature = connection.getFeature(Sasl2Feature.class);
            if (sasl2Feature == null || !sasl2Feature.hasInlineFeature(Bind2Elements.Bind.class)) {
                throw new TestNotPossibleException("XEP-0386: Bind 2 not supported by service");
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Verifies that binding a resource on an account with an empty personal archive results in a {@code <bound/>}
     * response that includes an empty {@code <metadata/>} element (mirroring XEP-0313 §5's requirement for the
     * standalone {@code <metadata/>} IQ).
     */
    @SmackIntegrationTest(section = "3.2", quote = "The server SHOULD include a <metadata/> element as defined by XEP-0313, describing the state of the user's message archive at the precise time of resource binding.")
    public void testBind2MetadataEmptyArchive() throws Exception
    {
        final ModularXmppClientToServerConnection connection = getSpecificUnconnectedConnection();
        try {
            connection.connect();
            connection.login(connection.getConfiguration().getUsername(), connection.getConfiguration().getPassword(), null);

            final MamMetadataElement mamMetadata = getMamMetadataFromBound(connection);

            assertTrue("Expected the <metadata/> in <bound/> for an account with an empty archive to be empty", mamMetadata.isEmpty());
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Verifies that binding a resource on an account with a non-empty personal archive results in a
     * {@code <bound/>} response whose {@code <metadata/>} 'start' and 'end' boundaries match the actual archive
     * content (as independently confirmed via a regular MAM query performed right after binding).
     */
    @SmackIntegrationTest(section = "3.2", quote = "The server SHOULD include a <metadata/> element as defined by XEP-0313, describing the state of the user's message archive at the precise time of resource binding.")
    public void testBind2MetadataNonEmptyArchive() throws Exception
    {
        final ModularXmppClientToServerConnection connection = getSpecificUnconnectedConnection();
        final AbstractXMPPConnection sender = getConnectedConnection();
        try {
            // Send a message to the not-yet-connected (but already registered) account, before it ever binds.
            final EntityBareJid archiveOwner = JidCreate.entityBareFrom(Localpart.from(connection.getConfiguration().getUsername().toString()), service);
            final String body = "mam-bind2-metadata-" + testRunId;
            sender.sendStanza(MessageBuilder.buildMessage().to(archiveOwner).setBody(body).build());

            // Wait for the message to be archived - without this, CI will frequently fail as the bind below might race the archiving.
            Thread.sleep(500);

            connection.connect();
            connection.login(connection.getConfiguration().getUsername(), connection.getConfiguration().getPassword(), null);

            final MamMetadataElement mamMetadata = getMamMetadataFromBound(connection);
            assertTrue("Expected the <metadata/> in <bound/> for a non-empty archive to contain 'start' and 'end' boundaries", !mamMetadata.isEmpty());
            assertNotNull(mamMetadata.getStart());
            assertNotNull(mamMetadata.getEnd());

            // Independently determine the id of the one archived message, to confirm the bound metadata refers to it.
            final MamManager mamManager = MamManager.getInstanceFor(connection);
            mamManager.getMamNamespace(); // Required to be able to query the archive without explicitly setting preferences.
            final MamManager.MamQuery baseline = mamManager.queryArchive(MamManager.MamQueryArgs.builder().build());
            assertEquals(1, baseline.getMessages().size());
            assertEquals(body, baseline.getMessages().get(0).getBody());
            final String expectedId = baseline.getMamResultExtensions().get(0).getId();

            assertEquals("Expected the 'start' boundary to identify the (only) archived message", expectedId, mamMetadata.getStart().getId());
            assertEquals("Expected the 'end' boundary to identify the (only) archived message", expectedId, mamMetadata.getEnd().getId());
        } finally {
            connection.disconnect();
            recycle(sender);
        }
    }

    private static MamMetadataElement getMamMetadataFromBound(final ModularXmppClientToServerConnection connection)
    {
        final Bind2Module bind2Module = connection.getConnectionModuleFor(Bind2ModuleDescriptor.class);
        assertNotNull("Bind2Module should be present on connection", bind2Module);

        final Bind2Module.Bind2SuccessResult bind2SuccessResult = bind2Module.getBind2SuccessResult();
        assertNotNull("Bind2SuccessResult should not be null", bind2SuccessResult);

        final Bind2Elements.Bound bound = bind2SuccessResult.getBound();
        assertNotNull("Bound element in Bind2SuccessResult should not be null", bound);

        final XmlElement mamMetadata = bound.getMamMetadata();
        assertNotNull("Expected <bound/> to include a mam:2 <metadata/> element", mamMetadata);
        assertTrue("Expected <metadata/> in <bound/> to be parsed as a MamMetadataElement", mamMetadata instanceof MamMetadataElement);
        return (MamMetadataElement) mamMetadata;
    }
}
