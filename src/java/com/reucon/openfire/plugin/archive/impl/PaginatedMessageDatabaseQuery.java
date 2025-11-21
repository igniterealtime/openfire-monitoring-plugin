/*
 * Copyright (C) 2020-2024 Ignite Realtime Foundation. All rights reserved.
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
import com.reucon.openfire.plugin.archive.xep0313.IQQueryHandler;
import org.dom4j.DocumentException;
import org.jivesoftware.database.DbConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Encapsulates responsibility of creating a database query that retrieves a specific subset (page) of archived messages
 * from a specific end-user entity owner (the archive that's queried is considered to be a 'personal archive').
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PaginatedMessageDatabaseQuery extends AbstractPaginatedMamQuery
{
    private static final Logger Log = LoggerFactory.getLogger(PaginatedMessageDatabaseQuery.class);

    /**
     * Creates a query for messages from a message archive.
     *
     * @param startDate     Start (inclusive) of period for which to return messages. EPOCH will be used if no value is provided.
     * @param endDate       End (inclusive) of period for which to return messages. 'now' will be used if no value is provided.
     * @param archiveOwner  The message archive owner.
     * @param with          An optional conversation partner
     */
    public PaginatedMessageDatabaseQuery(@Nullable final Date startDate, @Nullable final Date endDate, @Nonnull final JID archiveOwner, @Nullable final JID with)
    {
        super(startDate, endDate, archiveOwner, with);
    }

    @Override
    public String toString()
    {
        return "PaginatedMessageQuery{" +
            "startDate=" + startDate +
            ", endDate=" + endDate +
            ", archiveOwner=" + archiveOwner +
            ", with='" + with + '\'' +
            '}';
    }

    @Override
    protected List<ArchivedMessage> getPage(@Nullable final Long after, @Nullable final Long before, final int maxResults, final boolean isPagingBackwards) throws DataRetrievalException {
        Log.trace( "Getting page of archived messages. After: {}, Before: {}, Max results: {}, Paging backwards: {}", after, before, maxResults, isPagingBackwards );

        final List<ArchivedMessage> archivedMessages = new ArrayList<>();

        // The HSQL driver that is used in Openfire 4.5.0 will disregard a 'limit 0' (instead, returning all rows. A
        // limit on positive numbers does work). We should prevent this from occurring, if only because querying a database
        // for no results does not make much sense in the first place. See https://github.com/igniterealtime/openfire-monitoring-plugin/issues/80
        if ( maxResults <= 0 ) {
            return archivedMessages;
        }

        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            connection = DbConnectionManager.getConnection();
            final String query = buildQueryForMessages(after, before, maxResults, isPagingBackwards);
            pstmt = connection.prepareStatement( query );

            int pos = 0;
            pstmt.setLong( ++pos, dateToMillis( startDate ) );
            pstmt.setLong( ++pos, dateToMillis( endDate ) );

            if (with == null) {
                pstmt.setString( ++pos, archiveOwner.toBareJID() );
                pstmt.setString( ++pos, archiveOwner.toBareJID() );
            } else if (with.getResource() == null) {
                pstmt.setString( ++pos, archiveOwner.toBareJID() );
                pstmt.setString( ++pos, with.toBareJID() );
                pstmt.setString( ++pos, with.toBareJID() );
                pstmt.setString( ++pos, archiveOwner.toBareJID() );
            } else {
                pstmt.setString( ++pos, archiveOwner.toBareJID() );
                pstmt.setString( ++pos, with.toBareJID() );
                pstmt.setString( ++pos, with.getResource() );
                pstmt.setString( ++pos, with.toBareJID() );
                pstmt.setString( ++pos, with.getResource() );
                pstmt.setString( ++pos, archiveOwner.toBareJID() );
            }

            if ( after != null ) {
                pstmt.setLong( ++pos, after );
            }

            if ( before != null ) {
                pstmt.setLong( ++pos, before );
            }

            Log.trace( "Constructed query: {}", query );
            rs = pstmt.executeQuery();
            while (rs.next()) {
                final ArchivedMessage archivedMessage = JdbcPersistenceManager.extractMessage(archiveOwner, rs);
                archivedMessages.add(archivedMessage);
            }
            if(isPagingBackwards){
                Collections.reverse(archivedMessages);
            }
        } catch (SQLException e) {
            Log.error("SQL failure during MAM for owner: {}", this.archiveOwner, e);
            if (!IQQueryHandler.IGNORE_RETRIEVAL_EXCEPTIONS.getValue()) {
                throw new DataRetrievalException(e);
            }
        } catch (DocumentException e) {
            Log.error("Unable to parse 'stanza' value as valid XMPP for owner {}", this.archiveOwner, e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }

        return archivedMessages;
    }

    private Long dateToMillis(@Nullable final Date date) {
        return date == null ? null : date.getTime();
    }

    @Override
    protected int getTotalCount()
    {
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        int totalCount = 0;
        try {
            connection = DbConnectionManager.getConnection();
            pstmt = connection.prepareStatement( buildQueryForTotalCount() );

            int pos = 0;
            pstmt.setLong( ++pos, dateToMillis( startDate ) );
            pstmt.setLong( ++pos, dateToMillis( endDate ) );

            if (with == null) {
                pstmt.setString( ++pos, archiveOwner.toBareJID() );
                pstmt.setString( ++pos, archiveOwner.toBareJID() );
            } else if (with.getResource() == null) {
                pstmt.setString( ++pos, archiveOwner.toBareJID() );
                pstmt.setString( ++pos, with.toBareJID() );
                pstmt.setString( ++pos, with.toBareJID() );
                pstmt.setString( ++pos, archiveOwner.toBareJID() );
            } else {
                pstmt.setString( ++pos, archiveOwner.toBareJID() );
                pstmt.setString( ++pos, with.toBareJID() );
                pstmt.setString( ++pos, with.getResource() );
                pstmt.setString( ++pos, with.toBareJID() );
                pstmt.setString( ++pos, with.getResource() );
                pstmt.setString( ++pos, archiveOwner.toBareJID() );
            }

            Log.trace( "Constructed query: {}", pstmt );
            rs = pstmt.executeQuery();
            if (rs.next()) {
                totalCount = rs.getInt(1);
            }
        } catch (SQLException e) {
            Log.error("SQL failure while counting messages in MAM: ", e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }
        return totalCount;
    }

    private String buildQueryForMessages( @Nullable final Long after, @Nullable final Long before, final int maxResults, final boolean isPagingBackwards )
    {
        /* Database table 'ofMessageArchive' content examples:
         *
         *   Scenario       | fromJID      | fromJIDResource | toJID          | toJIDResource            | isPMforJID
         * -------------------------------------------------------------------------------------------------------------
         * A sends B a 1:1  | A's bare JID | A's resource    | B's bare JID   | B (if 'to' was full JID) | null
         * B sends A a 1:1  | B's bare JID | B's resource    | A's bare JID   | B (if 'to' was full JID) | null
         * A sends MUC msg  | A's bare JID | A's resource    | MUC's bare jid | A's nickname in MUC      | null
         * B sends MUC msg  | B's bare JID | B's resource    | MUC's bare jid | B's nickname in MUC      | null
         * A sends B a PM   | A's bare JID | A's resource    | MUC's bare jid | A's nickname in MUC      | B's bare JID
         * B sends A a PM   | B's bare JID | B's resource    | MUC's bare jid | B's nickname in MUC      | A's bare JID
         *
         * To get messages from the personal archive of 'A':
         * - fromJID = OWNER OR toJID = OWNER (to get all 1:1 messages)
         * - fromJID = OWNER (to get all messages A sent to (local?) MUCs - including PMs that A sent)
         * - isPMForJID = OWNER (to get all PMs (in local MUCs) that A received).
         */

        // What SQL keyword should be used to limit the result set: TOP() or LIMIT or ROWNUM ?
        final boolean useTopClause = DbConnectionManager.getDatabaseType().equals(DbConnectionManager.DatabaseType.sqlserver);
        final boolean useFetchFirstClause = DbConnectionManager.getDatabaseType().equals(DbConnectionManager.DatabaseType.oracle);
        final boolean useLimitClause = !useTopClause && !useFetchFirstClause;

        String sql = "SELECT";

        if (useTopClause) {
            sql += " TOP(" + maxResults + ")";
        }

        sql += " a.fromJID, a.fromJIDResource, a.toJID, a.toJIDResource, a.sentDate, a.body, a.stanza, a.messageID ";
        sql += """
            FROM ofMessageArchive a
            JOIN ofConversation c USING (conversationID)
            """;

        // Query for a personal archive.
        sql += """
            WHERE c.roomID IS NULL
            """;

        // Ignoring 'messageID IS NULL' as they are legacy messages.
        sql += """
              AND (a.stanza IS NOT NULL OR a.body IS NOT NULL)
              AND a.messageID IS NOT NULL
            """;

        // Apply the date filters.
        sql += """    
              AND a.sentDate >= ?
              AND a.sentDate <= ?
            """;

        // Apply the 'with' filter.
        if (with == null) {
            // No 'with' filter value was supplied.
            sql += """
              AND (
                   a.fromJID = ?
                OR a.toJID   = ?
              )
            """;
        } else if (with.getResource() == null) {
            // A 'with' filter value was supplied (bare JID).
            sql += """
              AND (
                   (a.fromJID = ? AND a.toJID = ?)
                OR (a.fromJID = ? AND a.toJID = ?)
              )
            """;
        } else {
            // A 'with' filter value was supplied (full JID).
            sql += """
              AND (
                   (a.fromJID = ? AND a.toJID = ? AND toJIDResource = ?)
                OR (a.fromJID = ? AND fromJIDResource = ? AND a.toJID = ?)
              )
            """;
        }

        // Apply navigation instructions.
        if (after != null) {
            sql += """
                AND a.messageID > ?
              """;
        }
        if (before != null) {
            sql += """
                AND a.messageID < ?
              """;
        }

        sql += "ORDER BY a.sentDate " + (isPagingBackwards ? "DESC" : "ASC");

        if (useLimitClause) {
            sql += " LIMIT " + maxResults;
        } else if(useFetchFirstClause) {
            sql += " FETCH FIRST " + maxResults + " ROWS ONLY";
        }

        /* TODO DO NOT match any 'real jid' in the 'with' form against private messages (only use the 'occupant jid' for that.
         * XEP-0313 defines that a filter value is to be applied to the stanza to/from attribute value. But, even
         * besides that: In some configurations (eg: a non-anonymous room) the real JID of occupants is visible, which would,
         * strictly speaking, allow for the private messages to be found by the query that's tested here. However, that
         * opens the door for very confusing UX - in some configurations, certain messages would be returned, while in other
         * configurations, similar messages would _not_ be returned. For consistency, private messages (exchanged in a MUC)
         * should only be returned when filtering by the room JID.
         */

        return sql;
    }


    private String buildQueryForTotalCount()
    {
        String sql = """
            SELECT COUNT(DISTINCT a.messageID)
            FROM ofMessageArchive a
            JOIN ofConversation c USING (conversationID)
            WHERE c.roomID IS NULL
              AND (a.stanza IS NOT NULL OR a.body IS NOT NULL)
              AND a.messageID IS NOT NULL
              AND a.sentDate >= ?
              AND a.sentDate <= ?
            """;

        if (with == null) {
            // No 'with' filter value was supplied.
            sql += """
              AND (
                   a.fromJID = ?
                OR a.toJID   = ?
              )
            """;
        } else if (with.getResource() == null) {
            // A 'with' filter value was supplied (bare JID).
            sql += """
              AND (
                   (a.fromJID = ? AND a.toJID = ?)
                OR (a.fromJID = ? AND a.toJID = ?)
              )
            """;
        } else {
            // A 'with' filter value was supplied (full JID).
            sql += """
              AND (
                   (a.fromJID = ? AND a.toJID = ? AND toJIDResource = ?)
                OR (a.fromJID = ? AND fromJIDResource = ? AND a.toJID = ?)
              )
            """;
        }

        return sql;
    }
}
