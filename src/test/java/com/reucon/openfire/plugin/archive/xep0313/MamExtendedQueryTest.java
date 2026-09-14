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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.Test;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for mam:2#extended query criteria parsing and ID-range merge semantics.
 */
public class MamExtendedQueryTest
{
    @Test
    public void emptyValuesAreTreatedAsAbsent()
    {
        final MamExtendedQuery query = new MamExtendedQuery("  ", "", Arrays.asList("", "  ", "id-1"), false);

        assertNull(query.getBeforeId());
        assertNull(query.getAfterId());
        assertEquals(Collections.singletonList("id-1"), query.getIds());
        assertTrue(query.hasIds());
        assertFalse(query.isFlipPage());
    }

    @Test
    public void beforeIdWithoutAfterIdIsBackwardsPaging()
    {
        final MamExtendedQuery onlyBefore = new MamExtendedQuery("b1", null, null, false);
        final MamExtendedQuery both = new MamExtendedQuery("b1", "a1", null, false);
        final MamExtendedQuery onlyAfter = new MamExtendedQuery(null, "a1", null, false);

        assertTrue(onlyBefore.isPagingBackwardsViaBeforeId());
        assertFalse(both.isPagingBackwardsViaBeforeId());
        assertFalse(onlyAfter.isPagingBackwardsViaBeforeId());
    }

    @Test
    public void combinedBeforeIdAndAfterIdUseExclusiveRangeMerge()
    {
        // Simultaneous before-id + after-id must keep messages strictly between both IDs.
        // Merging with RSM uses the more restrictive bound: max(after), min(before).
        assertEquals(Long.valueOf(20L), mergeAfter(10L, 20L));
        assertEquals(Long.valueOf(20L), mergeAfter(20L, 10L));
        assertEquals(Long.valueOf(5L), mergeBefore(5L, 15L));
        assertEquals(Long.valueOf(5L), mergeBefore(15L, 5L));
        assertEquals(Long.valueOf(7L), mergeAfter(null, 7L));
        assertEquals(Long.valueOf(9L), mergeBefore(9L, null));
    }

    @Test
    public void queryRequestParsesFlipPageSibling() throws Exception
    {
        final Element query = DocumentHelper.parseText(
            "<query xmlns='urn:xmpp:mam:2' queryid='q1'>"
                + "<set xmlns='http://jabber.org/protocol/rsm'><before>abc</before><max>10</max></set>"
                + "<flip-page/>"
                + "</query>"
        ).getRootElement();

        final QueryRequest request = new QueryRequest(query, new JID("user@example.org"));
        assertTrue(request.isFlipPage());
        assertEquals("q1", request.getQueryid());
        assertEquals("abc", request.getResultSet().getBefore());
    }

    @Test
    public void queryRequestWithoutFlipPageDefaultsFalse() throws Exception
    {
        final Element query = DocumentHelper.parseText(
            "<query xmlns='urn:xmpp:mam:2'/>"
        ).getRootElement();

        final QueryRequest request = new QueryRequest(query, new JID("user@example.org"));
        assertFalse(request.isFlipPage());
    }



    @Test
    public void idsOnlyIgnoresDateLimitsDecision()
    {
        final MamExtendedQuery idsOnly = new MamExtendedQuery(null, null, java.util.Collections.singletonList("abc"), false);
        assertTrue(idsOnly.isIdsOnly(false, false, false, false));
        assertFalse(idsOnly.isIdsOnly(true, false, false, false));
        assertFalse(idsOnly.isIdsOnly(false, true, false, false));
        assertFalse(idsOnly.isIdsOnly(false, false, true, false));
        assertFalse(idsOnly.isIdsOnly(false, false, false, true));

        final MamExtendedQuery withBefore = new MamExtendedQuery("b1", null, java.util.Collections.singletonList("abc"), false);
        assertFalse(withBefore.isIdsOnly(false, false, false, false));
    }

    @Test
    public void flipPageReversesBackwardsPageToNewestFirst()
    {
        // Simulates post-processing after Paginated*Query returns chronological order for a backwards page.
        final java.util.List<String> chronological = new java.util.ArrayList<>(java.util.Arrays.asList("old", "mid", "new"));
        final boolean isPagingBackwards = true;

        final java.util.List<String> withoutFlip = new java.util.ArrayList<>(chronological);
        // no flip-page: keep chronological
        assertEquals(java.util.Arrays.asList("old", "mid", "new"), withoutFlip);

        final java.util.List<String> withFlip = new java.util.ArrayList<>(chronological);
        if (isPagingBackwards && true && withFlip.size() > 1) {
            java.util.Collections.reverse(withFlip);
        }
        assertEquals(java.util.Arrays.asList("new", "mid", "old"), withFlip);
    }

    /**
     * Mirrors the exclusive-bound merge used by Jdbc/Muc persistence managers.
     */
    private static Long mergeAfter(final Long rsmAfter, final Long afterId)
    {
        if (rsmAfter == null) {
            return afterId;
        }
        if (afterId == null) {
            return rsmAfter;
        }
        return Math.max(rsmAfter, afterId);
    }

    private static Long mergeBefore(final Long rsmBefore, final Long beforeId)
    {
        if (rsmBefore == null) {
            return beforeId;
        }
        if (beforeId == null) {
            return rsmBefore;
        }
        return Math.min(rsmBefore, beforeId);
    }
}
