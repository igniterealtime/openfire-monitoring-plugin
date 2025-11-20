/*
 * Copyright (C) 2020 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.muc.MUCRoom;
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
 * from a chat room (the archive that's queried is considered to be a 'MUC archive').
 *
 * Note that, per XEP-0313, the 'private messages' that are exchanged in a MUC room are not included in the MUC archive,
 * which implies that they're included in the personal archive.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PaginatedMucMessageDatabaseQuery extends AbstractPaginatedMamMucQuery
{
    private static final Logger Log = LoggerFactory.getLogger(PaginatedMucMessageDatabaseQuery.class);

    /**
     * Creates a query for messages from a message archive of a multi-user chat room.
     *
     * @param startDate Start (inclusive) of period for which to return messages. EPOCH will be used if no value is provided.
     * @param endDate End (inclusive) of period for which to return messages. 'now' will be used if no value is provided.
     * @param archiveOwner The message archive owner (the JID of the chat room).
     * @param with An optional message author.
     */
    public PaginatedMucMessageDatabaseQuery(@Nullable final Date startDate, @Nullable final Date endDate, @Nonnull final MUCRoom archiveOwner, @Nullable final JID with)
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
            pstmt.setLong( 1, dateToMillis( startDate ) );
            pstmt.setLong( 2, dateToMillis( endDate ) );

            pstmt.setString( 3, archiveOwner.toBareJID() );
            int pos = 3;

            if ( with != null ) {
                pstmt.setString( ++pos, with.toBareJID() );
                if (with.getResource() != null) {
                    pstmt.setString( ++pos, with.getResource() );
                }
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
            Log.error("SQL failure during MUC MAM for room {}", this.archiveOwner, e);
            if (!IQQueryHandler.IGNORE_RETRIEVAL_EXCEPTIONS.getValue()) {
                throw new DataRetrievalException(e);
            }
        } catch (DocumentException e) {
            Log.error("Unable to parse 'stanza' value as valid XMPP for room {}", this.archiveOwner, e);
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
            pstmt.setLong( 1, dateToMillis( startDate ) );
            pstmt.setLong( 2, dateToMillis( endDate ) );
            pstmt.setString( 3, archiveOwner.toBareJID() );
            int pos = 3;

            if ( with != null ) {
                pstmt.setString( ++pos, with.toBareJID() );
                if (with.getResource() != null) {
                    pstmt.setString( ++pos, with.getResource() );
                }
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
       // What SQL keyword should be used to limit the result set: TOP() or LIMIT ?
       final boolean useTopClause = DbConnectionManager.getDatabaseType().equals(DbConnectionManager.DatabaseType.sqlserver);
       final boolean useFetchFirstClause = DbConnectionManager.getDatabaseType().equals(DbConnectionManager.DatabaseType.oracle);
       final boolean useLimitClause = !useTopClause && !useFetchFirstClause;

       String sql = "SELECT";
       if (useTopClause) {
          sql += " TOP(" + maxResults + ")";
       }
       sql += " fromJID, fromJIDResource, toJID, toJIDResource, sentDate, body, stanza, messageID"
                   + " FROM ofMessageArchive"
                   + " WHERE (stanza IS NOT NULL OR body IS NOT NULL)";

       // Ignore legacy messages
       sql += " AND messageID IS NOT NULL";

       sql += " AND sentDate >= ?";
       sql += " AND sentDate <= ?";

       /*
        *   Scenario       | fromJID      | fromJIDResource | toJID          | toJIDResource            | isPMforJID
        * -------------------------------------------------------------------------------------------------------------
        * A sends B a 1:1  | A's bare JID | A's resource    | B's bare JID   | B (if 'to' was full JID) | null
        * B sends A a 1:1  | B's bare JID | B's resource    | A's bare JID   | B (if 'to' was full JID) | null
        * A sends MUC msg  | A's bare JID | A's resource    | MUC's bare jid | A's nickname in MUC      | null
        * B sends MUC msg  | B's bare JID | B's resource    | MUC's bare jid | B's nickname in MUC      | null
        * A sends B a PM   | A's bare JID | A's resource    | MUC's bare jid | A's nickname in MUC      | B's bare JID
        * B sends A a PM   | B's bare JID | B's resource    | MUC's bare jid | B's nickname in MUC      | A's bare JID
        *
        * If A wants MUC archive (the OWNER is MUC, the REQUESTOR is A):
        * - toJID = OWNER - to limit the returned messages to those shared in a chatroom
        * - isPMforJID = NULL (per XEP-0313 private messages are not included in the MUC archive responses)
        */

       // Query for a MUC archive.
       sql += " AND toJID = ? AND isPMforJID IS NULL";

       if (this.with != null) {
           // XEP-0313 specifies in 4.1.1: "If a 'with' field is present in the form, it contains a JID against which to
           // match messages. The server MUST only return messages if they match the supplied JID. [...] An item in a
           // MUC archive matches if the publisher of the item matches the JID; note that this should only be available
           // to entities that would already have been allowed to know the publisher of the events (e.g. this could not
           // be used by a visitor to a semi-anonymous MUC). [...] If (and only if) the supplied JID is a bare JID (i.e.
           // no resource is present), then the server SHOULD return messages if their bare to/from address for a user
           // archive, or from address otherwise, would match it."
           //
           // From this, it can be concluded that the 'with' value is to be evaluated against the 'real' (as opposed to
           // 'occupant') JID of the message author (auth checks must be done, but elsewhere).
           sql += " AND fromJID = ? ";
           if (this.with.getResource() != null) {
               sql += " AND fromJIDResource = ?";
           }
           // TODO Consider allowing the 'with' value to be used with an occupant JID (or only a nickname). XEP-0313 only specifies that the 'real' JID is to be used, but possibly, both can be allowed. This would allow for greater flexibility.
       }

       if (after != null) {
          sql += " AND messageID > ?";
       }
       if (before != null) {
          sql += " AND messageID < ?";
       }

       sql += " ORDER BY sentDate " + (isPagingBackwards ? "DESC" : "ASC");

       if (useLimitClause) {
          sql += " LIMIT " + maxResults;
       } else if(useFetchFirstClause) {
          sql += " FETCH FIRST " + maxResults + " ROWS ONLY ";          
       }
       return sql;
    }

    private String buildQueryForTotalCount()
    {
        String sql = "SELECT COUNT(DISTINCT messageID) "
            + "FROM ofMessageArchive "
            + "WHERE (stanza IS NOT NULL OR body IS NOT NULL) "
            + "AND messageID IS NOT NULL "
            + "AND sentDate >= ? "
            + "AND sentDate <= ? "
            + "AND toJID = ? AND isPMforJID IS NULL ";

        if (with != null) {
            sql += " AND fromJID = ? ";
            if (this.with.getResource() != null) {
                sql += " AND fromJIDResource = ?";
            }
        }

        return sql;
    }
}
