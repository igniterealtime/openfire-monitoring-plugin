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
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Integrates mam:2 archive metadata with Openfire's Bind 2 (XEP-0386) inline feature negotiation.
 * <p>
 * XEP-0313 §3.2 (as referenced from XEP-0386 §3.2) states that the server SHOULD include a
 * {@code <metadata/>} element, describing the state of the user's archive, in the {@code <bound/>}
 * response of a resource bind, to help the client determine what queries it may need to perform to
 * synchronise messages.
 * <p>
 * Openfire only exposes the {@code Bind2InlineHandler} SPI needed for this starting with version 5.2.0.
 * To avoid forcing that as a hard minimum server version, this class talks to that SPI exclusively
 * through reflection: on older servers where the SPI classes do not exist, registration is silently
 * skipped (logged at debug level) and the rest of the plugin continues to work normally, simply without
 * offering mam:2 metadata at bind time.
 */
public final class MamBind2Support
{
    private static final Logger Log = LoggerFactory.getLogger(MamBind2Support.class);

    private static final String BIND2_INLINE_HANDLER_CLASS = "org.jivesoftware.openfire.net.Bind2InlineHandler";
    private static final String BIND2_REQUEST_CLASS = "org.jivesoftware.openfire.net.Bind2Request";
    static final String NAMESPACE = "urn:xmpp:mam:2";

    private static volatile Object registeredHandler;

    private MamBind2Support()
    {
        // Utility class.
    }

    /**
     * Registers a Bind 2 inline handler for mam:2 archive metadata, if (and only if) the running
     * Openfire server provides the {@code Bind2InlineHandler} SPI (added in Openfire 5.2.0). Does
     * nothing if a handler is already registered, or if the SPI is not available.
     */
    public static synchronized void register()
    {
        if (registeredHandler != null) {
            return;
        }
        try {
            final Class<?> handlerInterface = Class.forName(BIND2_INLINE_HANDLER_CLASS);
            final Class<?> requestClass = Class.forName(BIND2_REQUEST_CLASS);

            final Object proxy = Proxy.newProxyInstance(
                MamBind2Support.class.getClassLoader(),
                new Class<?>[] { handlerInterface },
                new InlineHandlerInvocationHandler()
            );

            requestClass.getMethod("registerElementHandler", handlerInterface).invoke(null, proxy);
            registeredHandler = proxy;
            Log.info("Registered a Bind 2 (XEP-0386) inline handler that provides mam:2 archive metadata during resource binding.");
        } catch (final ClassNotFoundException e) {
            Log.debug("This Openfire server does not provide the Bind2InlineHandler SPI (added in Openfire 5.2.0). " +
                "mam:2 archive metadata will not be offered as part of Bind 2 resource binding.");
        } catch (final ReflectiveOperationException e) {
            Log.warn("Failed to register a Bind 2 inline handler for mam:2 archive metadata, despite the Bind2 SPI being present.", e);
        }
    }

    /**
     * Unregisters the Bind 2 inline handler that was registered by {@link #register()}, if any. Safe
     * to call even when {@link #register()} was a no-op (eg: because the SPI was not available).
     */
    public static synchronized void unregister()
    {
        if (registeredHandler == null) {
            return;
        }
        try {
            final Class<?> requestClass = Class.forName(BIND2_REQUEST_CLASS);
            requestClass.getMethod("unregisterElementHandler", String.class).invoke(null, NAMESPACE);
        } catch (final ReflectiveOperationException e) {
            Log.warn("Failed to unregister the Bind 2 inline handler for mam:2 archive metadata.", e);
        } finally {
            registeredHandler = null;
        }
    }

    /**
     * Handles a Bind 2 inline element in the {@code urn:xmpp:mam:2} namespace, by adding a
     * {@code <metadata/>} element - as described in XEP-0313 §5 - to the {@code <bound/>} response.
     *
     * @param session the session that is being bound.
     * @param bound the {@code <bound/>} element to add the {@code <metadata/>} element to.
     * @return true if the metadata element was added successfully.
     */
    static boolean handleElement(final LocalClientSession session, final Element bound)
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

    /**
     * Dispatches {@code Bind2InlineHandler} interface method invocations to this class, without
     * requiring a compile-time dependency on that interface.
     */
    private static final class InlineHandlerInvocationHandler implements InvocationHandler
    {
        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args)
        {
            switch (method.getName()) {
                case "getNamespace":
                    return NAMESPACE;
                case "handleElement":
                    return handleElement((LocalClientSession) args[0], (Element) args[1]);
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                default:
                    return "MamBind2InlineHandler{namespace=" + NAMESPACE + "}";
            }
        }
    }
}
