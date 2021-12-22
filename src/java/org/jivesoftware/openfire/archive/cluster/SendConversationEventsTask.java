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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.ConversationEvent;
import org.jivesoftware.openfire.archive.ConversationManager;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.archive.XmlSerializer;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Task that sends conversation events to the senior cluster member.
 *
 * @author Gaston Dombiak
 */
public class SendConversationEventsTask implements ClusterTask<Void> {
    
    private static final Logger Log = LoggerFactory.getLogger(SendConversationEventsTask.class);
            
    private List<ConversationEvent> events;

    /**
     * Do not use this constructor. It only exists for serialization purposes.
     */
    public SendConversationEventsTask() {
    }

    public SendConversationEventsTask(@Nonnull final List<ConversationEvent> events) {
        this.events = events;
    }

    public Void getResult() {
        return null;
    }

    public void run() {
        Log.debug("Processing {} chat events as received by other nodes.", events.size());
        final Optional<Plugin> plugin = XMPPServer.getInstance().getPluginManager().getPluginByName(MonitoringConstants.PLUGIN_NAME);
        if (!plugin.isPresent()) {
            Log.error("Unable to execute cluster task! The Monitoring plugin does not appear to be loaded on this machine.");
            return;
        }
        final ConversationManager conversationManager = ((MonitoringPlugin)plugin.get()).getConversationManager();
        for (final ConversationEvent event : events) {
            try {
                event.run(conversationManager);
            } catch (Exception e) {
                Log.error("Error while processing chat archiving event", e);
            }
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        // ClassCastExceptions occur when using classes provided by a plugin during serialization (sometimes only after
        // reloading the plugin without restarting Openfire. This is why this implementation marshalls data as XML when
        // serializing. See https://github.com/igniterealtime/openfire-monitoring-plugin/issues/120
        // and https://github.com/igniterealtime/openfire-monitoring-plugin/issues/156
        ExternalizableUtil.getInstance().writeInt(out, events.size());
        for (ConversationEvent event : events) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, XmlSerializer.getInstance().marshall(event));
        }
    }

    public void readExternal(ObjectInput in) throws IOException {
        // ClassCastExceptions occur when using classes provided by a plugin during serialization (sometimes only after
        // reloading the plugin without restarting Openfire. This is why this implementation marshalls data as XML when
        // serializing. See https://github.com/igniterealtime/openfire-monitoring-plugin/issues/120
        // and https://github.com/igniterealtime/openfire-monitoring-plugin/issues/156
        events = new ArrayList<>();
        int eventCount = ExternalizableUtil.getInstance().readInt(in);
        for (int i = 0; i < eventCount; i++) {
            String marshalledConversationEvent = ExternalizableUtil.getInstance().readSafeUTF(in);
            final ConversationEvent unmarshalledEvent = (ConversationEvent)XmlSerializer.getInstance().unmarshall(marshalledConversationEvent);
            events.add(unmarshalledEvent);
        }
    }
}
