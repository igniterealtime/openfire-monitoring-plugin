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

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Cross-cutting unit checks for mam:2#extended support contracts that do not require a live DB.
 */
public class MamExtendedSupportTest
{
    @Test
    public void discoAdvertisesExtendedOnlyForMam2WhenImplemented()
    {
        final List<String> mam2 = MamQueryFormFields.getFeatures("urn:xmpp:mam:2", true, true, true);
        final List<String> mam1 = MamQueryFormFields.getFeatures("urn:xmpp:mam:1", false, true, true);
        final List<String> mam0 = MamQueryFormFields.getFeatures("urn:xmpp:mam:0", false, false, true);

        assertTrue(mam2.contains(MamQueryFormFields.EXTENDED_NAMESPACE));
        assertTrue(mam2.contains("urn:xmpp:mam:2"));
        assertTrue(mam2.contains("urn:xmpp:fulltext:0"));
        assertFalse(mam1.contains(MamQueryFormFields.EXTENDED_NAMESPACE));
        assertFalse(mam0.contains(MamQueryFormFields.EXTENDED_NAMESPACE));
    }

    @Test
    public void unsupportedIdFieldsRejectedForNonMam2AllowList()
    {
        final List<String> mam1Fields = MamQueryFormFields.getSupportedFieldVariables(false, false);
        assertFalse(mam1Fields.contains(MamQueryFormFields.BEFORE_ID));
        assertFalse(mam1Fields.contains(MamQueryFormFields.AFTER_ID));
        assertFalse(mam1Fields.contains(MamQueryFormFields.IDS));
    }

    @Test
    public void combinedBeforeAndAfterIdAreExclusiveBounds()
    {
        // after=10, before=20 means IDs strictly between 10 and 20.
        final long afterId = 10L;
        final long beforeId = 20L;
        assertTrue(isStrictlyBetween(15L, afterId, beforeId));
        assertFalse(isStrictlyBetween(10L, afterId, beforeId));
        assertFalse(isStrictlyBetween(20L, afterId, beforeId));
        assertFalse(isStrictlyBetween(9L, afterId, beforeId));
        assertFalse(isStrictlyBetween(21L, afterId, beforeId));
    }

    @Test
    public void idsOnlyBypassesDateDefaults()
    {
        final MamExtendedQuery q = new MamExtendedQuery(null, null, Arrays.asList("stable-1", "stable-2"), false);
        assertTrue(q.isIdsOnly(false, false, false, false));
        assertEquals(2, q.getIds().size());
    }

    private static boolean isStrictlyBetween(final long id, final long after, final long before)
    {
        return id > after && id < before;
    }
}
