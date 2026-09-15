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
 * Verifies that {@link MamBind2Support} registers and unregisters its {@code Bind2InlineHandler}
 * (added to Openfire in 5.2.0) idempotently and without error.
 */
public class MamBind2SupportTest
{
    @Test
    public void registerDoesNotThrow()
    {
        MamBind2Support.register();
        MamBind2Support.unregister();
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
