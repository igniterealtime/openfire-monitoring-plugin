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

/**
 * Verifies that {@link MamBind2Support} degrades gracefully when the running Openfire server does not
 * provide the {@code Bind2InlineHandler} SPI (added in Openfire 5.2.0), which is the case in this test
 * environment (built against an older xmppserver artifact that lacks these classes).
 */
public class MamBind2SupportTest
{
    @Test
    public void registerDoesNotThrowWhenSpiIsAbsent()
    {
        // Must not throw NoClassDefFoundError/ClassNotFoundException, and must not require the Bind2
        // SPI classes to be present on the classpath.
        MamBind2Support.register();
    }

    @Test
    public void unregisterIsSafeWithoutPriorRegistration()
    {
        MamBind2Support.unregister();
    }

    @Test
    public void registerThenUnregisterIsIdempotentAndSafe()
    {
        MamBind2Support.register();
        MamBind2Support.register();
        MamBind2Support.unregister();
        MamBind2Support.unregister();
    }
}
