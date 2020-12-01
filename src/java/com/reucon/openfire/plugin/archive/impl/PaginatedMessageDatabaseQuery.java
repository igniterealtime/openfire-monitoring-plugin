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
import org.jivesoftware.database.DbConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Encapsulates responsibility of creating a database query that retrieves a specific subset (page) of archived messages
 * from a specific owner
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PaginatedMessageDatabaseQuery
{
    private static final Logger Log = LoggerFactory.getLogger(PaginatedMessageDatabaseQuery.class );

    private final Date startDate;
    private final Date endDate;
    private final JID owner;
    private final JID with;

    public PaginatedMessageDatabaseQuery( Date startDate, Date endDate, JID owner, JID with )
    {
        this.startDate = startDate == null ? new Date( 0L ) : startDate ;
        this.endDate = endDate == null ? new Date() : endDate;
        this.owner = owner;
        this.with = with;
    }

    public Date getStartDate()
    {
        return startDate;
    }

    public Date getEndDate()
    {
        return endDate;
    }

    public JID getOwner()
    {
        return owner;
    }

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

    protected List<ArchivedMessage> getPage( final Long after, final Long before, final int maxResults, final boolean isPagingBackwards )
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
            pstmt.setString( 3, owner.toBareJID() );
            int pos = 3;

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

            if ( after != null ) {
                pstmt.setLong( ++pos, after );
            }

            if ( before != null ) {
                pstmt.setLong( ++pos, before );
            }

            Log.trace( "Constructed query: {}", query );
            rs = pstmt.executeQuery();
            while (rs.next()) {
                final ArchivedMessage archivedMessage = JdbcPersistenceManager.extractMessage(rs);
                archivedMessages.add(archivedMessage);
            }
        } catch (SQLException e) {
            Log.error("SQL failure during MAM: ", e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }

        return archivedMessages;
    }

    private Date millisToDate(Long millis) {
        return millis == null ? null : new Date(millis);
    }

    private Long dateToMillis(Date date) {
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
            int pos = 3;

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

    private String getStatement(final Long after, final Long before, final int maxResults, final boolean isPagingBackwards)
    {
        String sql = "SELECT fromJID, fromJIDResource, toJID, toJIDResource, sentDate, body, stanza, messageID, bareJID FROM ("
            + "SELECT DISTINCT ofMessageArchive.fromJID, ofMessageArchive.fromJIDResource, ofMessageArchive.toJID, ofMessageArchive.toJIDResource, ofMessageArchive.sentDate, ofMessageArchive.body, ofMessageArchive.stanza, ofMessageArchive.messageID, ofConParticipant.bareJID "
            + "FROM ofMessageArchive "
            + "INNER JOIN ofConParticipant ON ofMessageArchive.conversationID = ofConParticipant.conversationID "
            + "WHERE (ofMessageArchive.stanza IS NOT NULL OR ofMessageArchive.body IS NOT NULL) ";

        // Ignore legacy messages
        sql += " AND ofMessageArchive.messageID IS NOT NULL ";
        sql += " AND ofMessageArchive.sentDate >= ?";
        sql += " AND ofMessageArchive.sentDate <= ?";
        sql += " AND ofConParticipant.bareJID = ?";

        if( with != null )
        {
            // XEP-0313 specifies: If (and only if) the supplied JID is a bare JID (i.e. no resource is present), then the server SHOULD return messages if their bare to/from address for a user archive, or from address otherwise, would match it.
            if (with.getResource() == null) {
                sql += " AND ( ofMessageArchive.toJID = ? OR ofMessageArchive.fromJID = ? )";
            } else {
                sql += " AND ( (ofMessageArchive.toJID = ? AND ofMessageArchive.toJIDResource = ? ) OR (ofMessageArchive.fromJID = ? AND ofMessageArchive.fromJIDResource = ? ) )";
            }
        }

        if ( after != null ) {
            sql += " AND ofMessageArchive.messageID > ? ";
        }
        if ( before != null ) {
            sql += " AND ofMessageArchive.messageID < ? ";
        }

        sql += " ORDER BY ofMessageArchive.sentDate " + (isPagingBackwards ? "DESC" : "ASC");
        sql += " LIMIT " + maxResults;
        sql += " ) AS part ";
        sql += " ORDER BY sentDate";

        return sql;
    }

    private String getStatementForSqlServer(final Long after, final Long before, final int maxResults, final boolean isPagingBackwards)
    {
        String sql = "SELECT fromJID, fromJIDResource, toJID, toJIDResource, sentDate, body, stanza, messageID, bareJID FROM ("
            + "SELECT DISTINCT TOP("+maxResults+") ofMessageArchive.fromJID, ofMessageArchive.fromJIDResource, ofMessageArchive.toJID, ofMessageArchive.toJIDResource, ofMessageArchive.sentDate, ofMessageArchive.body, ofMessageArchive.stanza, ofMessageArchive.messageID, ofConParticipant.bareJID "
            + "FROM ofMessageArchive "
            + "INNER JOIN ofConParticipant ON ofMessageArchive.conversationID = ofConParticipant.conversationID "
            + "WHERE (ofMessageArchive.stanza IS NOT NULL OR ofMessageArchive.body IS NOT NULL) ";

        // Ignore legacy messages
        sql += " AND ofMessageArchive.messageID IS NOT NULL ";
        sql += " AND ofMessageArchive.sentDate >= ?";
        sql += " AND ofMessageArchive.sentDate <= ?";
        sql += " AND ofConParticipant.bareJID = ?";

        if( with != null )
        {
            // XEP-0313 specifies: If (and only if) the supplied JID is a bare JID (i.e. no resource is present), then the server SHOULD return messages if their bare to/from address for a user archive, or from address otherwise, would match it.
            if (with.getResource() == null) {
                sql += " AND ( ofMessageArchive.toJID = ? OR ofMessageArchive.fromJID = ? )";
            } else {
                sql += " AND ( (ofMessageArchive.toJID = ? AND ofMessageArchive.toJIDResource = ? ) OR (ofMessageArchive.fromJID = ? AND ofMessageArchive.fromJIDResource = ? ) )";
            }
        }

        if ( after != null ) {
            sql += " AND ofMessageArchive.messageID > ? ";
        }
        if ( before != null ) {
            sql += " AND ofMessageArchive.messageID < ? ";
        }

        sql += " ORDER BY ofMessageArchive.sentDate " + (isPagingBackwards ? "DESC" : "ASC");
        sql += " ) AS part ";

        sql += " ORDER BY sentDate";

        return sql;
    }

    private String buildQueryForMessages( final Long after, final Long before, final int maxResults, final boolean isPagingBackwards )
    {
        switch ( DbConnectionManager.getDatabaseType())
        {
            case sqlserver:
                return getStatementForSqlServer( after, before, maxResults, isPagingBackwards );

            default:
                return getStatement( after, before, maxResults, isPagingBackwards );
        }
    }

    private String buildQueryForTotalCount()
    {
        String sql = "SELECT COUNT(DISTINCT ofMessageArchive.messageID) "
            + "FROM ofMessageArchive "
            + "INNER JOIN ofConParticipant ON ofMessageArchive.conversationID = ofConParticipant.conversationID "
            + "WHERE (ofMessageArchive.stanza IS NOT NULL OR ofMessageArchive.body IS NOT NULL) "
            + "AND ofMessageArchive.messageID IS NOT NULL "
            + "AND ofMessageArchive.sentDate >= ? "
            + "AND ofMessageArchive.sentDate <= ? "
            + "AND ofConParticipant.bareJID = ? ";

        if (with != null) {
            // XEP-0313 specifies: If (and only if) the supplied JID is a bare JID (i.e. no resource is present), then the server SHOULD return messages if their bare to/from address for a user archive, or from address otherwise, would match it.
            if (with.getResource() == null) {
                sql += " AND ( ofMessageArchive.toJID = ? OR ofMessageArchive.fromJID = ? )";
            } else {
                sql += " AND ( (ofMessageArchive.toJID = ? AND ofMessageArchive.toJIDResource = ? ) OR (ofMessageArchive.fromJID = ? AND ofMessageArchive.fromJIDResource = ? ) )";
            }
        }

        return sql;
    }
}
