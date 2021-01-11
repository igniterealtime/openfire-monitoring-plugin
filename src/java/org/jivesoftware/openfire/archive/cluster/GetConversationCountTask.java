/*
 * Copyright (C) 2008 Jive Software. All rights reserved.
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

import org.jivesoftware.openfire.archive.ConversationManager;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.util.cache.ClusterTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Optional;

/**
 * Task that will return the number of current conversations taking place in the senior cluster member.
 * All conversations in the cluster are kept in the senior cluster member.
 *
 * @author Gaston Dombiak
 */
public class GetConversationCountTask implements ClusterTask<Integer>
{
    private static final Logger Log = LoggerFactory.getLogger(GetConversationTask.class);

    private int conversationCount;

    @Nonnull
    public Integer getResult() {
        return conversationCount;
    }

    public void run() {
        final Optional<Plugin> plugin = XMPPServer.getInstance().getPluginManager().getPluginByName(MonitoringConstants.PLUGIN_NAME);
        if (!plugin.isPresent()) {
            Log.error("Unable to execute cluster task! The Monitoring plugin does not appear to be loaded on this machine.");
            return;
        }
        final ConversationManager conversationManager = ((MonitoringPlugin)plugin.get()).getConversationManager();
        conversationCount = conversationManager.getConversationCount();
    }

    public void writeExternal(ObjectOutput out) {
        // Do nothing
    }

    public void readExternal(ObjectInput in) {
        // Do nothing
    }
}
