/*
 * Copyright (C) 2018 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.archive.cluster;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.ConversationManager;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * A task that retrieves a time estimation on the time it takes for conversations (and metadata) to have been written to persistent storage.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class GetConversationsWriteETATask implements ClusterTask<Duration>
{
    private static final Logger Log = LoggerFactory.getLogger(GetConversationsWriteETATask.class);

    private Instant instant;
    private Duration result;

    public GetConversationsWriteETATask() {}

    public GetConversationsWriteETATask( @Nonnull final Instant instant )
    {
        this.instant = instant;
    }

    @Override
    public void run()
    {
        final Optional<Plugin> plugin = XMPPServer.getInstance().getPluginManager().getPluginByName(MonitoringConstants.PLUGIN_NAME);
        if (!plugin.isPresent()) {
            Log.error("Unable to execute cluster task! The Monitoring plugin does not appear to be loaded on this machine.");
            return;
        }
        final ConversationManager conversationManager = ((MonitoringPlugin)plugin.get()).getConversationManager();
        result = conversationManager.availabilityETAOnLocalNode( instant );
    }

    @Override
    public Duration getResult()
    {
        return result;
    }

    @Override
    public void writeExternal( ObjectOutput out ) throws IOException
    {
        ExternalizableUtil.getInstance().writeSerializable( out, instant );
    }

    @Override
    public void readExternal( ObjectInput in ) throws IOException
    {
        instant = (Instant) ExternalizableUtil.getInstance().readSerializable( in );
    }
}
