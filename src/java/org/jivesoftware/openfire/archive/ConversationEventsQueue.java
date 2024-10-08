/*
 * Copyright (C) 2008 Jive Software, Ignite Realtime Foundation 2024. All rights reserved.
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.cluster.SendConversationEventsTask;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.reporting.util.TaskEngine;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Queue conversation events generated by this JVM and send them to the senior cluster
 * member every 3 seconds. This is an optimization to reduce traffic between the cluster
 * nodes especially when under heavy conversations load.
 *
 * @author Gaston Dombiak
 */
public class ConversationEventsQueue {
    private static final Logger Log = LoggerFactory.getLogger(ConversationEventsQueue.class);
    private final ConversationManager conversationManager;
    /**
     * Chat events that are pending to be sent to the senior cluster member.
     * Key: Conversation Key; Value: List of conversation events.
     */
    private final Map<String, List<ConversationEvent>> chatEvents = new HashMap<String, List<ConversationEvent>>();
    /**
     * Group chat events that are pending to be sent to the senior cluster member.
     * Key: Conversation Key; Value: List of conversation events.
     */
    private final Map<String, List<ConversationEvent>> roomEvents = new HashMap<String, List<ConversationEvent>>();

    public ConversationEventsQueue(ConversationManager conversationManager, TaskEngine taskEngine) {
        this.conversationManager = conversationManager;

        // Schedule a task to do conversation archiving.
        TimerTask sendTask = new TimerTask() {
            @Override
            public void run() {
                // Move queued events to a temp place
                List<ConversationEvent> eventsToSend = new ArrayList<>();
                synchronized (chatEvents) {
                    for (List<ConversationEvent> list : chatEvents.values()) {
                        // Just send the first and last event if we are not archiving messages
                        if (!ConversationEventsQueue.this.conversationManager.isMessageArchivingEnabled() &&
                                list.size() > 2) {
                            eventsToSend.add(list.get(0));
                            eventsToSend.add(list.get(list.size() - 1));
                        }
                        else {
                            // Send all events
                            eventsToSend.addAll(list);
                        }
                    }
                    // We can empty the queue now
                    chatEvents.clear();
                }
                synchronized (roomEvents) {
                    for (List<ConversationEvent> list : roomEvents.values()) {
                        eventsToSend.addAll(list);
                    }
                    // We can empty the queue now
                    roomEvents.clear();
                }

                // Send the queued events (from the temp place) to the senior cluster member
                try {
                    if (!eventsToSend.isEmpty()) {
                        CacheFactory.doClusterTask(new SendConversationEventsTask(eventsToSend),
                            ClusterManager.getSeniorClusterMember().toByteArray());
                    }
                } catch (Throwable t) {
                    Log.error("A problem occurred while trying to send events to the senior cluster node.", t);
                }
            }
        };
        taskEngine.scheduleAtFixedRate(sendTask, Duration.ofSeconds(3), Duration.ofSeconds(3));
    }

    /**
     * Queues the one-to-one chat event to be later sent to the senior cluster member.
     *
     * @param conversationKey unique key that identifies the conversation.
     * @param event conversation event.
     */
    public void addChatEvent(String conversationKey, ConversationEvent event) {
        Log.trace("Add chat event for key {}", conversationKey);
        synchronized (chatEvents) {
            List<ConversationEvent> events = chatEvents.get(conversationKey);
            if (events == null) {
                events = new ArrayList<ConversationEvent>();
                chatEvents.put(conversationKey, events);
            }
            events.add(event);
        }
    }

    /**
     * Queues the group chat event to be later sent to the senior cluster member.
     *
     * @param conversationKey unique key that identifies the conversation.
     * @param event conversation event.
     */
    public void addGroupChatEvent(String conversationKey, ConversationEvent event) {
        Log.trace("Add group chat event for key {}", conversationKey);
        synchronized (roomEvents) {
            List<ConversationEvent> events = roomEvents.get(conversationKey);
            if (events == null) {
                events = new ArrayList<ConversationEvent>();
                roomEvents.put(conversationKey, events);
            }
            events.add(event);
        }
    }
}
