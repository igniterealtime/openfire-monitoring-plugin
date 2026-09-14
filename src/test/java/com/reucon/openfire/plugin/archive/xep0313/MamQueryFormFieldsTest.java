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

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies MAM data-form field allow-lists and disco feature advertisement rules for mam:2#extended.
 */
public class MamQueryFormFieldsTest
{
    @Test
    public void mam2SupportsExtendedIdFields()
    {
        final List<String> fields = MamQueryFormFields.getSupportedFieldVariables(true, false);

        assertTrue(fields.contains(MamQueryFormFields.BEFORE_ID));
        assertTrue(fields.contains(MamQueryFormFields.AFTER_ID));
        assertTrue(fields.contains(MamQueryFormFields.IDS));
        assertTrue(fields.contains("with"));
        assertTrue(fields.contains("start"));
        assertTrue(fields.contains("end"));
        assertTrue(fields.contains("FORM_TYPE"));
    }

    @Test
    public void mam0AndMam1DoNotSupportExtendedIdFields()
    {
        final List<String> mam0 = MamQueryFormFields.getSupportedFieldVariables(false, false);
        final List<String> mam1 = MamQueryFormFields.getSupportedFieldVariables(false, true);

        assertFalse(mam0.contains(MamQueryFormFields.BEFORE_ID));
        assertFalse(mam0.contains(MamQueryFormFields.AFTER_ID));
        assertFalse(mam0.contains(MamQueryFormFields.IDS));

        assertFalse(mam1.contains(MamQueryFormFields.BEFORE_ID));
        assertFalse(mam1.contains(MamQueryFormFields.AFTER_ID));
        assertFalse(mam1.contains(MamQueryFormFields.IDS));
        assertTrue(mam1.contains("{urn:xmpp:fulltext:0}fulltext"));
    }

    @Test
    public void extendedFeatureOnlyWhenImplementedForMam2()
    {
        final List<String> mam2Extended = MamQueryFormFields.getFeatures("urn:xmpp:mam:2", true, false, true);
        final List<String> mam2NotYet = MamQueryFormFields.getFeatures("urn:xmpp:mam:2", true, false, false);
        final List<String> mam1 = MamQueryFormFields.getFeatures("urn:xmpp:mam:1", false, false, true);

        assertTrue(mam2Extended.contains(MamQueryFormFields.EXTENDED_NAMESPACE));
        assertFalse(mam2NotYet.contains(MamQueryFormFields.EXTENDED_NAMESPACE));
        assertFalse(mam1.contains(MamQueryFormFields.EXTENDED_NAMESPACE));
        assertTrue(mam2Extended.contains("urn:xmpp:mam:2"));
    }
}
