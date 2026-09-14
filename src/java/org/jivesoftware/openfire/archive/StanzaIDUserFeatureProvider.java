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
package org.jivesoftware.openfire.archive;

import org.jivesoftware.openfire.disco.UserFeaturesProvider;

import java.util.Collections;
import java.util.Iterator;

/**
 * Advertises support for XEP-0359 'Unique and Stable Stanza IDs' in the service discovery information of the accounts
 * that are registered with this server.
 *
 * XEP-0359 defines that the entity that assigns an identifier to a one-to-one message is the account of which the
 * archive the message is stored in. That entity should announce the corresponding namespace, which allows a client to
 * verify that the business rules of the specification are enforced before it uses an identifier for deduplication or
 * for catching up with an archive.
 *
 * The feature is advertised only while this implementation actually generates identifiers.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0359.html">XEP-0359</a>
 */
public class StanzaIDUserFeatureProvider implements UserFeaturesProvider
{
    @Override
    public Iterator<String> getFeatures()
    {
        if (!ArchiveStanzaIDUtil.isEnabled()) {
            return Collections.emptyIterator();
        }
        return Collections.singleton("urn:xmpp:sid:0").iterator();
    }
}
