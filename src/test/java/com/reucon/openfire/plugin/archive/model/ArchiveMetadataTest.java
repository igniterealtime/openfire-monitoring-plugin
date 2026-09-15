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
package com.reucon.openfire.plugin.archive.model;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for archive metadata value object used by mam:2 metadata queries.
 */
public class ArchiveMetadataTest
{
    @Test
    public void emptyArchiveHasNoBoundaries()
    {
        final ArchiveMetadata empty = ArchiveMetadata.empty();
        assertTrue(empty.isEmpty());
        assertNull(empty.getStart());
        assertNull(empty.getEnd());
        assertNull(empty.getStartTime());
        assertNull(empty.getEndTime());
    }

    @Test
    public void nullBoundariesAreTreatedAsEmpty()
    {
        final ArchiveMetadata metadata = new ArchiveMetadata(null, null);
        assertTrue(metadata.isEmpty());
        assertFalse(metadata.getStart() != null);
        assertFalse(metadata.getEnd() != null);
    }
}
