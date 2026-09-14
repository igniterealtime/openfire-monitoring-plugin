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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Optional mam:2#extended query criteria parsed from a MAM data form and {@code <flip-page/>}.
 */
public final class MamExtendedQuery
{
    private final String beforeId;
    private final String afterId;
    private final List<String> ids;
    private final boolean flipPage;

    public MamExtendedQuery(final String beforeId, final String afterId, final List<String> ids, final boolean flipPage)
    {
        this.beforeId = emptyToNull(beforeId);
        this.afterId = emptyToNull(afterId);
        if (ids == null || ids.isEmpty()) {
            this.ids = Collections.emptyList();
        } else {
            final List<String> cleaned = new ArrayList<>();
            for (final String id : ids) {
                final String value = emptyToNull(id);
                if (value != null) {
                    cleaned.add(value);
                }
            }
            this.ids = Collections.unmodifiableList(cleaned);
        }
        this.flipPage = flipPage;
    }

    public static MamExtendedQuery none()
    {
        return new MamExtendedQuery(null, null, null, false);
    }

    public String getBeforeId()
    {
        return beforeId;
    }

    public String getAfterId()
    {
        return afterId;
    }

    public List<String> getIds()
    {
        return ids;
    }

    public boolean hasIds()
    {
        return !ids.isEmpty();
    }

    public boolean isFlipPage()
    {
        return flipPage;
    }

    public boolean hasIdFilters()
    {
        return beforeId != null || afterId != null || hasIds();
    }

    /**
     * Whether this query is effectively paging backwards via before-id (without after-id).
     */
    public boolean isPagingBackwardsViaBeforeId()
    {
        return beforeId != null && afterId == null;
    }

    /**
     * True when the client requested only specific message IDs (no with/start/end/text/before-id/after-id filters).
     * In that case XEP-0313 requires returning those messages regardless of default date-range limits.
     */
    public boolean isIdsOnly(final boolean hasWith, final boolean hasStart, final boolean hasEnd, final boolean hasText)
    {
        return hasIds()
            && !hasWith
            && !hasStart
            && !hasEnd
            && !hasText
            && beforeId == null
            && afterId == null;
    }

    private static String emptyToNull(final String value)
    {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public String toString()
    {
        return "MamExtendedQuery{" +
            "beforeId='" + beforeId + '\'' +
            ", afterId='" + afterId + '\'' +
            ", ids=" + ids +
            ", flipPage=" + flipPage +
            '}';
    }
}
