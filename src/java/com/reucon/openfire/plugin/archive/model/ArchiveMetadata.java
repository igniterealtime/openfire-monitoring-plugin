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

import javax.annotation.Nullable;
import java.util.Date;

/**
 * Describes the oldest and newest messages in a MAM archive (XEP-0313 metadata query).
 */
public final class ArchiveMetadata
{
    @Nullable
    private final ArchivedMessage start;

    @Nullable
    private final ArchivedMessage end;

    public ArchiveMetadata(@Nullable final ArchivedMessage start, @Nullable final ArchivedMessage end)
    {
        this.start = start;
        this.end = end;
    }

    public static ArchiveMetadata empty()
    {
        return new ArchiveMetadata(null, null);
    }

    public boolean isEmpty()
    {
        return start == null && end == null;
    }

    @Nullable
    public ArchivedMessage getStart()
    {
        return start;
    }

    @Nullable
    public ArchivedMessage getEnd()
    {
        return end;
    }

    @Nullable
    public Date getStartTime()
    {
        return start != null ? start.getTime() : null;
    }

    @Nullable
    public Date getEndTime()
    {
        return end != null ? end.getTime() : null;
    }
}
