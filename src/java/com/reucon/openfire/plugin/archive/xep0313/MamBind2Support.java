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
package com.reucon.openfire.plugin.archive.xep0313;

import com.reucon.openfire.plugin.archive.PersistenceManager;
import com.reucon.openfire.plugin.archive.model.ArchiveMetadata;
import org.dom4j.Element;
import org.jivesoftware.openfire.net.Bind2InlineHandler;
import org.jivesoftware.openfire.net.Bind2Request;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Integrates mam:2 archive metadata with Openfire's Bind 2 (XEP-0386) inline feature negotiation.
 * <p>
 * XEP-0313 §3.2 (as referenced from XEP-0386 §3.2) states that the server SHOULD include a
 * {@code <metadata/>} element, describing the state of the user's archive, in the {@code <bound/>}
 * response of a resource bind, to help the client determine what queries it may need to perform to
 * synchronise messages.
 * <p>
 * This relies on the {@code Bind2InlineHandler} SPI added in Openfire 5.2.0.
 */
public final class MamBind2Support implements Bind2InlineHandler
{
    private static final Logger Log = LoggerFactory.getLogger(MamBind2Support.class);

    static final String NAMESPACE = "urn:xmpp:mam:2";

    private static final MamBind2Support INSTANCE = new MamBind2Support();

    private static volatile boolean registered;

    private MamBind2Support()
    {
    }

    /**
     * Registers a Bind 2 inline handler for mam:2 archive metadata. Does nothing if a handler is
     * already registered.
     */
    public static synchronized void register()
    {
        if (registered) {
            return;
        }
        Bind2Request.registerElementHandler(INSTANCE);
        registered = true;
        Log.info("Registered a Bind 2 (XEP-0386) inline handler that provides mam:2 archive metadata during resource binding.");
    }

    /**
     * Unregisters the Bind 2 inline handler that was registered by {@link #register()}, if any.
     */
    public static synchronized void unregister()
    {
        if (!registered) {
            return;
        }
        Bind2Request.unregisterElementHandler(INSTANCE);
        registered = false;
    }

    @Override
    public String getNamespace()
    {
        return NAMESPACE;
    }

    /**
     * Adds a {@code <metadata/>} element - as described in XEP-0313 §5 - to the {@code <bound/>}
     * response.
     *
     * @param session the session that is being bound.
     * @param bound the {@code <bound/>} element to add the {@code <metadata/>} element to.
     * @param element the inline request element (unused, mam:2 has no request-specific content).
     * @return true if the metadata element was added successfully.
     */
    @Override
    public boolean handleElement(final LocalClientSession session, final Element bound, final Element element)
    {
        try {
            final JID archiveOwner = session.getAddress().asBareJID();
            final PersistenceManager persistenceManager = MonitoringPlugin.getInstance().getPersistenceManager(archiveOwner);
            final ArchiveMetadata metadata = persistenceManager.getArchiveMetadata(archiveOwner);

            final Element metadataElement = bound.addElement("metadata", NAMESPACE);
            ArchiveMetadataElement.populate(metadataElement, metadata, archiveOwner);
            return true;
        } catch (final Exception e) {
            Log.warn("Failed to add mam:2 archive metadata to the Bind 2 response for session {}", session.getAddress(), e);
            return false;
        }
    }
}
