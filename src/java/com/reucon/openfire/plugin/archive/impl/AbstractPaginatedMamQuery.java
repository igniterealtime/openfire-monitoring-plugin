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

import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Date;
import java.util.List;

/**
 * Representation of a MAM query.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public abstract class AbstractPaginatedMamQuery
{
    /**
     * Start (inclusive) of period for which to return messages.
     */
    @Nonnull
    protected final Date startDate;

    /**
     * End (inclusive) of period for which to return messages.
     */
    @Nonnull
    protected final Date endDate;

    /**
     * The message archive owner.
     */
    @Nonnull
    protected final JID archiveOwner;

    /**
     * An optional conversation partner.
     */
    @Nullable
    protected final JID with;

    /**
     * A search filter to be used for text-based search.
     */
    @Nullable
    protected final String query;

    /**
     * Creates a query for messages from a message archive.
     *
     * @param startDate Start (inclusive) of period for which to return messages. EPOCH will be used if no value is provided.
     * @param endDate End (inclusive) of period for which to return messages. 'now' will be used if no value is provided.
     * @param archiveOwner The message archive owner.
     * @param with An optional conversation partner
     */
    public AbstractPaginatedMamQuery(@Nullable final Date startDate, @Nullable final Date endDate, @Nonnull final JID archiveOwner, @Nullable final JID with)
    {
        this.startDate = startDate == null ? new Date( 0L ) : startDate ;
        this.endDate = endDate == null ? new Date() : endDate;
        this.archiveOwner = archiveOwner;
        this.with = with;
        this.query = null;
    }

    /**
     * Creates a query for messages from a message archive.
     *
     * @param startDate Start (inclusive) of period for which to return messages. EPOCH will be used if no value is provided.
     * @param endDate End (inclusive) of period for which to return messages. 'now' will be used if no value is provided.
     * @param archiveOwner The message archive owner.
     * @param with An optional conversation partner
     * @param query A search string to be used for text-based search.
     */
    public AbstractPaginatedMamQuery(@Nullable final Date startDate, @Nullable final Date endDate, @Nonnull final JID archiveOwner, @Nullable final JID with, @Nonnull final String query)
    {
        this.startDate = startDate == null ? new Date( 0L ) : startDate ;
        this.endDate = endDate == null ? new Date() : endDate;
        this.archiveOwner = archiveOwner;
        this.with = with;
        this.query = query;
    }

    /**
     * Get a page of a potentially larger list of archived messages that are the result of this query.
     *
     * @param after an optional message identifier that acts as a starting point (exclusive) of the messages to be returned.
     * @param before an optional message identifier that acts as an end point (exclusive) of the messages to be returned.
     * @param maxResults The maximum number of archived messages to return
     * @param isPagingBackwards true if the order of the messages is from new to old, otherwise false.
     * @return A list of archived messages
     * @throws DataRetrievalException On any problem that occurs while retrieving the page of archived messages.
     */
    abstract protected List<ArchivedMessage> getPage(@Nullable final Long after, @Nullable final Long before, final int maxResults, final boolean isPagingBackwards) throws DataRetrievalException;

    /**
     * Returns the amount of messages that are in the entire, unlimited/unpaged, result set.
     * <p>
     * The returned number is allowed to be an approximation.
     *
     * @return A message count, or -1 if unavailable.
     */
    abstract protected int getTotalCount();

    @Nonnull
    public Date getStartDate()
    {
        return startDate;
    }

    @Nonnull
    public Date getEndDate()
    {
        return endDate;
    }

    @Nonnull
    public JID getArchiveOwner()
    {
        return archiveOwner;
    }

    @Nullable
    public JID getWith()
    {
        return with;
    }

    @Nullable
    public String getQuery() {
        return query;
    }

    @Override
    public String toString()
    {
        return "AbstractPaginatedMamQuery{" +
            "startDate=" + startDate +
            ", endDate=" + endDate +
            ", archiveOwner=" + archiveOwner +
            ", with=" + with +
            ", query='" + query + '\'' +
            '}';
    }
}
