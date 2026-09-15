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
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.Test;
import org.xmpp.packet.JID;

import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link ArchiveMetadataElement}, shared between the mam:2 metadata IQ and the mam:2
 * Bind 2 (XEP-0386) inline handler.
 */
public class ArchiveMetadataElementTest
{
    private static final JID OWNER = new JID("juliet@capulet.lit");

    @Test
    public void emptyArchiveAddsNoBoundaries() throws Exception
    {
        final Element metadataElement = DocumentHelper.createElement("metadata");

        ArchiveMetadataElement.populate(metadataElement, ArchiveMetadata.empty(), OWNER);

        assertNull(metadataElement.element("start"));
        assertNull(metadataElement.element("end"));
    }

    @Test
    public void nullMetadataAddsNoBoundaries() throws Exception
    {
        final Element metadataElement = DocumentHelper.createElement("metadata");

        ArchiveMetadataElement.populate(metadataElement, null, OWNER);

        assertNull(metadataElement.element("start"));
        assertNull(metadataElement.element("end"));
    }
}
