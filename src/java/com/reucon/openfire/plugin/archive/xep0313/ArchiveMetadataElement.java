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

import com.reucon.openfire.plugin.archive.model.ArchiveMetadata;
import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import org.dom4j.Element;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.xmpp.packet.JID;

/**
 * Populates a {@code <metadata xmlns='urn:xmpp:mam:2'/>} element with {@code <start/>} and {@code <end/>}
 * boundaries, as defined by XEP-0313 §5 "Archive metadata".
 * <p>
 * Shared between {@link IQMetadataHandler} (the dedicated metadata IQ) and the mam:2 Bind 2 (XEP-0386)
 * inline handler, both of which describe the same archive state using the same element shape.
 */
final class ArchiveMetadataElement
{
    private ArchiveMetadataElement()
    {
        // Utility class.
    }

    /**
     * Adds {@code <start/>} and {@code <end/>} boundary children to the given {@code metadataElement},
     * based on the first/last message of the archive. Does nothing if the archive is empty.
     *
     * @param metadataElement the {@code <metadata/>} element to populate.
     * @param metadata the archive metadata to describe. May be null or empty.
     * @param archiveOwner the owner of the archive, used to resolve stable and unique stanza IDs.
     */
    static void populate(final Element metadataElement, final ArchiveMetadata metadata, final JID archiveOwner)
    {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        addBoundary(metadataElement, "start", metadata.getStart(), archiveOwner);
        addBoundary(metadataElement, "end", metadata.getEnd(), archiveOwner);
    }

    private static void addBoundary(final Element metadataElement, final String name, final ArchivedMessage message, final JID archiveOwner)
    {
        if (message == null) {
            return;
        }
        final Element boundary = metadataElement.addElement(name);
        String id = message.getStableId(archiveOwner);
        if (id == null || id.isEmpty()) {
            if (message.getId() != null) {
                id = String.valueOf(message.getId());
            }
        }
        if (id != null) {
            boundary.addAttribute("id", id);
        }
        if (message.getTime() != null) {
            boundary.addAttribute("timestamp", XMPPDateTimeFormat.format(message.getTime()));
        }
    }
}
