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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Helpers for MAM data-form field discovery and allow-listing.
 * <p>
 * Kept free of Openfire runtime dependencies so the rules can be unit-tested in isolation.
 */
final class MamQueryFormFields
{
    static final String BEFORE_ID = "before-id";
    static final String AFTER_ID = "after-id";
    static final String IDS = "ids";
    static final String EXTENDED_NAMESPACE = "urn:xmpp:mam:2#extended";

    private MamQueryFormFields()
    {
        // Utility class.
    }

    /**
     * Returns field variable names that are allowed in a MAM query data form.
     *
     * @param usesUniqueAndStableIDs whether the handler uses XEP-0359 stable IDs (mam:2).
     * @param fulltextEnabled whether full-text search is enabled.
     * @return never null.
     */
    static List<String> getSupportedFieldVariables(final boolean usesUniqueAndStableIDs, final boolean fulltextEnabled)
    {
        final List<String> results = new ArrayList<>(Arrays.asList("FORM_TYPE", "with", "start", "end"));
        if (usesUniqueAndStableIDs) {
            results.add(BEFORE_ID);
            results.add(AFTER_ID);
            results.add(IDS);
        }
        if (fulltextEnabled) {
            results.add("{urn:xmpp:fulltext:0}fulltext");
            results.add("withtext");
            results.add("search");
        }
        return results;
    }

    /**
     * Returns disco features advertised for a MAM query handler.
     *
     * @param namespace the MAM namespace (mam:0/1/2).
     * @param usesUniqueAndStableIDs whether the handler uses XEP-0359 stable IDs (mam:2).
     * @param fulltextEnabled whether full-text search is enabled.
     * @param extendedImplemented whether mam:2#extended capabilities are implemented and should be advertised.
     * @return never null.
     */
    static List<String> getFeatures(final String namespace,
                                    final boolean usesUniqueAndStableIDs,
                                    final boolean fulltextEnabled,
                                    final boolean extendedImplemented)
    {
        final List<String> result = new ArrayList<>();
        result.add(namespace);
        if (fulltextEnabled) {
            result.add("urn:xmpp:fulltext:0");
        }
        if (usesUniqueAndStableIDs && extendedImplemented) {
            result.add(EXTENDED_NAMESPACE);
        }
        return Collections.unmodifiableList(result);
    }
}
