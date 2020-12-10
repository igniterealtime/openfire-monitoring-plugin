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
import java.util.Date;
import java.util.List;

/**
 * Encapsulates responsibility of creating a database query that retrieves a specific subset (page) of archived messages
 * from a specific owner.
 *
 * Note that an 'owner' can be an end-user entity (when the archive that's queried is considered to be a
 * 'personal archive') or a chatroom (in which case the archive that's queried is considered to be a group chat archive).
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PaginatedMessageDatabaseQuery
{
    private static final Logger Log = LoggerFactory.getLogger(PaginatedMessageDatabaseQuery.class );

    @Nonnull
    private final Date startDate;

    @Nonnull
    private final Date endDate;

    @Nonnull
    private final JID owner;

    @Nullable
    private final JID with;

    /**
     * Creates a query for messages from a message archive.
     *
     * The semantics of the 'with' arguments are slightly different, depending on the nature of the owner of the
     * archive. When the archive owner is an end-user ('personal archive'), then all messages sent 'to' or 'from' the
     * 'with' entity are returned. When the archive is a group chat archive and the 'with' argument is provided, then
     * the results will be limited to messages sent by the 'with' entity.
     *
     * @param startDate Start (inclusive) of period for which to return messages. EPOCH will be used if no value is provided.
     * @param endDate End (inclusive) of period for which to return messages. 'now' will be used if no value is provided.
     * @param owner The message archive owner.
     * @param with An optional converstation partner (or message author, in case of MUC).
     */
    public PaginatedMessageDatabaseQuery(@Nullable final Date startDate, @Nullable final Date endDate, @Nonnull final JID owner, @Nullable final JID with)
    {
        this.startDate = startDate == null ? new Date( 0L ) : startDate ;
        this.endDate = endDate == null ? new Date() : endDate;
        this.owner = owner;
        this.with = with;
    }

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
    public JID getOwner()
    {
        return owner;
    }

    @Nullable
    public JID getWith()
    {
        return with;
    }

    @Override
    public String toString()
    {
        return "PaginatedMessageQuery{" +
            "startDate=" + startDate +
            ", endDate=" + endDate +
            ", owner=" + owner +
            ", with='" + with + '\'' +
            '}';
    }

    protected List<ArchivedMessage> getPage( @Nullable final Long after, @Nullable final Long before, final int maxResults, final boolean isPagingBackwards )
    {
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

            // TODO Optimize this for the MUC use-case. Unlike personal archives, the owner of the MUC archive is guaranteed to be in the 'tojid' column. For MUC, there's no need to look in both columns.
            pstmt.setString( 3, owner.toBareJID() );
            pstmt.setString( 4, owner.toBareJID() );
            int pos = 4;

            if ( with != null ) {
                // TODO Optimize this for the MUC use-case. Unlike personal archives, the relevant 'with' value (sender of the message) of the MUC archive is guaranteed to be in the 'fromJid' columns. For MUC, there's no need to look in both columns.
                if (with.getResource() == null) {
                    pstmt.setString( ++pos, with.toString() );
                    pstmt.setString( ++pos, with.toString() );
                } else {
                    pstmt.setString( ++pos, with.toBareJID() );
                    pstmt.setString( ++pos, with.getResource() );
                    pstmt.setString( ++pos, with.toBareJID() );
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
                final ArchivedMessage archivedMessage = JdbcPersistenceManager.extractMessage(owner, rs);
                archivedMessages.add(archivedMessage);
            }
        } catch (SQLException e) {
            Log.error("SQL failure during MAM for owner: {}", this.owner, e);
        } catch (DocumentException e) {
            Log.error("Unable to parse 'stanza' value as valid XMMP for owner {}", this.owner, e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }

        return archivedMessages;
    }

    private Long dateToMillis(@Nullable final Date date) {
        return date == null ? null : date.getTime();
    }

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
            pstmt.setString( 3, owner.toBareJID() );
            pstmt.setString( 4, owner.toBareJID() );
            int pos = 4;

            if ( with != null ) {
                if (with.getResource() == null) {
                    pstmt.setString( ++pos, with.toString() );
                    pstmt.setString( ++pos, with.toString() );
                } else {
                    pstmt.setString( ++pos, with.toBareJID() );
                    pstmt.setString( ++pos, with.getResource() );
                    pstmt.setString( ++pos, with.toBareJID() );
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
        final boolean useTopNotLimit = DbConnectionManager.getDatabaseType().equals(DbConnectionManager.DatabaseType.sqlserver);

        String sql = "SELECT";
        if (useTopNotLimit) {
            sql += " TOP(" + maxResults + ")";
        }
        sql += " fromJID, fromJIDResource, toJID, toJIDResource, sentDate, body, stanza, messageID"
            + " FROM ofMessageArchive"
            + " WHERE (stanza IS NOT NULL OR body IS NOT NULL)";

        // Ignore legacy messages
        sql += " AND messageID IS NOT NULL";

        sql += " AND sentDate >= ?";
        sql += " AND sentDate <= ?";
        sql += " AND ( toJID = ? OR fromJID = ? )";

        if( with != null )
        {
            // XEP-0313 specifies: If (and only if) the supplied JID is a bare JID (i.e. no resource is present), then the server SHOULD return messages if their bare to/from address for a user archive, or from address otherwise, would match it.
            if (with.getResource() == null) {
                sql += " AND ( toJID = ? OR fromJID = ? )";
            } else {
                sql += " AND ( ( toJID = ? AND toJIDResource = ? ) OR ( fromJID = ? AND fromJIDResource = ? ) )";
            }
        }

        if ( after != null ) {
            sql += " AND messageID > ?";
        }
        if ( before != null ) {
            sql += " AND messageID < ?";
        }

        sql += " ORDER BY sentDate " + (isPagingBackwards ? "DESC" : "ASC");

        if (!useTopNotLimit) {
            sql += " LIMIT " + maxResults;
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
            + "AND ( toJID = ? OR fromJID = ? ) ";

        if (with != null) {
            // XEP-0313 specifies: If (and only if) the supplied JID is a bare JID (i.e. no resource is present), then the server SHOULD return messages if their bare to/from address for a user archive, or from address otherwise, would match it.
            if (with.getResource() == null) {
                sql += " AND ( toJID = ? OR fromJID = ? )";
            } else {
                sql += " AND ( (toJID = ? AND toJIDResource = ? ) OR (fromJID = ? AND fromJIDResource = ? ) )";
            }
        }

        return sql;
    }
}
