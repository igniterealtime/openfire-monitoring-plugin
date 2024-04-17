/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
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
package com.reucon.openfire.plugin.archive.impl;

import org.jivesoftware.openfire.muc.MUCRoom;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Date;

/**
 * Representation of a MAM query that is querying a multi-user chat-based archive.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public abstract class AbstractPaginatedMamMucQuery extends AbstractPaginatedMamQuery
{
    /**
     * The MUC room instance that owns the message archive.
     */
    @Nonnull
    protected final MUCRoom room;

    /**
     * To be able to return not only the public messages exchanged in the room, but also the private messages that are
     * relevant for the user that performs this query, an additional JID is provided as the 'messageOwner' argument.
     * This identifies the user for which to retrieve the (private) messages.
     */
    @Nonnull
    protected final JID messageOwner;

    /**
     * Creates a query for messages from a message archive.
     * <p>
     * To be able to return not only the public messages exchanged in the room, but also the private messages that are
     * relevant for the user that performs this query, an additional JID is provided as the 'messageOwner' argument.
     * This identifies the user for which to retrieve the (private) messages.
     *
     * @param startDate Start (inclusive) of period for which to return messages. EPOCH will be used if no value is provided.
     * @param endDate End (inclusive) of period for which to return messages. 'now' will be used if no value is provided.
     * @param room The multi-user chat room that is the message archive owner.
     * @param messageOwner The entity for which to return messages (typically the JID of the entity making the request).
     * @param with An optional conversation partner
     */
    public AbstractPaginatedMamMucQuery(@Nullable final Date startDate, @Nullable final Date endDate, @Nonnull final MUCRoom room, @Nonnull final JID messageOwner, @Nullable final JID with)
    {
        super(startDate, endDate, room.getJID(), with);
        this.messageOwner = messageOwner;
        this.room = room;
    }

    /**
     * Creates a query for messages from a message archive.
     * <p>
     * To be able to return not only the public messages exchanged in the room, but also the private messages that are
     * relevant for the user that performs this query, an additional JID is provided as the 'messageOwner' argument.
     * This identifies the user for which to retrieve the (private) messages.
     *
     * @param startDate Start (inclusive) of period for which to return messages. EPOCH will be used if no value is provided.
     * @param endDate End (inclusive) of period for which to return messages. 'now' will be used if no value is provided.
     * @param room The multi-user chat room that is the message archive owner.
     * @param messageOwner The entity for which to return messages (typically the JID of the entity making the request).
     * @param with An optional conversation partner
     * @param query A search string to be used for text-based search.
     */
    public AbstractPaginatedMamMucQuery(@Nullable final Date startDate, @Nullable final Date endDate, @Nonnull final MUCRoom room, @Nonnull final JID messageOwner, @Nullable final JID with, @Nonnull final String query)
    {
        super(startDate, endDate, room.getJID(), with, query);
        this.messageOwner = messageOwner;
        this.room = room;
    }

    @Nonnull
    public MUCRoom getRoom()
    {
        return room;
    }

    @Nonnull
    public JID getMessageOwner()
    {
        return messageOwner;
    }

    @Override
    public String toString()
    {
        return "AbstractPaginatedMamMucQuery{" +
            "messageOwner=" + messageOwner +
            ", startDate=" + startDate +
            ", endDate=" + endDate +
            ", archiveOwner=" + archiveOwner +
            ", with=" + with +
            ", query='" + query + '\'' +
            '}';
    }
}
