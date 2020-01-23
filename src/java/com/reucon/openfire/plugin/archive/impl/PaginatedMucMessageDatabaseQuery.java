/*
 * Copyright (C) 2019 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.util.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Encapsulates responsibility of creating a database query that retrieves a specific subset (page) of archived messages related to a MUC room.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PaginatedMucMessageDatabaseQuery
{
    private static final Logger Log = LoggerFactory.getLogger(PaginatedMucMessageDatabaseQuery.class );

    private final Date startDate;
    private final Date endDate;
    private final MUCRoom room;
    private final JID with;

    public PaginatedMucMessageDatabaseQuery( Date startDate, Date endDate, MUCRoom room, JID with )
    {
        this.startDate = startDate == null ? new Date( 0L ) : startDate ;
        this.endDate = endDate == null ? new Date() : endDate;
        this.room = room;
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

    public MUCRoom getRoom()
    {
        return room;
    }

    public JID getWith()
    {
        return with;
    }

    @Override
    public String toString()
    {
        return "PaginatedMucMessageQuery{" +
            "startDate=" + startDate +
            ", endDate=" + endDate +
            ", room=" + room +
            ", with='" + with + '\'' +
            '}';
    }

    protected List<ArchivedMessage> getPage( final Long after, final Long before, final int maxResults, final boolean isPagingBackwards )
    {
        final List<ArchivedMessage> msgs = new LinkedList<>();

        // The HSQL driver that is used in Openfire 4.5.0 will disregard a 'limit 0' (instead, returning all rows. A
        // limit on positive numbers does work). We should prevent this from occuring, if only because querying a database
        // for no results does not make much sense in the first place. See https://github.com/igniterealtime/openfire-monitoring-plugin/issues/80
        if ( maxResults <= 0 ) {
            return msgs;
        }

        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            connection = DbConnectionManager.getConnection();
            pstmt = prepareStatement(connection, after, before, maxResults, isPagingBackwards, false );
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String senderJID = rs.getString(1);
                String nickname = rs.getString(2);
                Date sentDate = new Date(Long.parseLong(rs.getString(3).trim()));
                String subject = rs.getString(4);
                String body = rs.getString(5);
                String stanza = rs.getString(6);
                long id = rs.getLong(7);

                msgs.add( MucMamPersistenceManager.asArchivedMessage(room.getJID(), senderJID, nickname, sentDate, subject, body, stanza, id) );
            }
        } catch (SQLException e) {
            Log.error("SQL failure during MAM-MUC: ", e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }

        return msgs;
    }


    protected int getTotalCount( final Long after, final Long before, final int maxResults, final boolean isPagingBackwards )
    {
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        int totalCount = 0;
        try {
            connection = DbConnectionManager.getConnection();
            pstmt = prepareStatement(connection, after, before, maxResults, isPagingBackwards, true);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                totalCount = rs.getInt(1);
            }
        } catch (SQLException e) {
            Log.error("SQL failure while counting messages in MAM-MUC: ", e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }
        return totalCount;
    }

    private String getStatementForMySQL(final Long after, final Long before, final int maxResults, final boolean isPagingBackwards)
    {
        String sql = "SELECT sender, nickname, logTime, subject, body, stanza, messageId ";
        sql += " FROM ( ";
        sql += "   SELECT sender, nickname, logTime, subject, body, stanza, messageId FROM ofMucConversationLog ";
        sql += "   WHERE messageId IS NOT NULL AND logTime > ? AND logTime <= ? AND roomID = ? AND (nickname IS NOT NULL OR subject IS NOT NULL) ";
        if ( with != null ) {
            // XEP-0313 specifies: If (and only if) the supplied JID is a bare JID (i.e. no resource is present), then the server SHOULD return messages if their bare to/from address for a user archive, or from address otherwise, would match it.
            if (with.getResource() == null) {
                sql += " AND sender LIKE ? ";
            } else {
                sql += " AND sender = ? ";
            }
        }
        if ( after != null ) {
            sql += " AND messageId > ? ";
        }
        if ( before != null ) {
            sql += " AND messageId < ? ";
        }

        sql += "ORDER BY logTime " + (isPagingBackwards ? "DESC" : "ASC");
        sql += " LIMIT " + maxResults;
        sql += " ) AS part ";
        sql += " ORDER BY logTime ASC";   
        return sql;
    }

    private String getStatementForSQLServer(final Long after, final Long before, final int maxResults, final boolean isPagingBackwards)
    {
        String sql = "SELECT sender, nickname, logTime, subject, body, stanza, messageId ";
        sql += " FROM ( ";
        sql += "   SELECT TOP("+String.valueOf(maxResults)+") sender, nickname, logTime, subject, body, stanza, messageId FROM ofMucConversationLog ";
        sql += "   WHERE messageId IS NOT NULL AND logTime > ? AND logTime <= ? AND roomID = ? AND (nickname IS NOT NULL OR subject IS NOT NULL) ";
        if ( with != null ) {
            // XEP-0313 specifies: If (and only if) the supplied JID is a bare JID (i.e. no resource is present), then the server
            // SHOULD return messages if their bare to/from address for a user archive, or from address otherwise, would match it.
            // TODO using a 'LIKE' query is unlikely to perform well on large data sets. Fix this with an additional column or index (which could also utilize Lucene, perhaps).
            if (with.getResource() == null) {
                sql += " AND sender LIKE ? ";
            } else {
                sql += " AND sender = ? ";
            }
        }
        if ( after != null ) {
            sql += " AND messageId > ? ";
        }
        if ( before != null ) {
            sql += " AND messageId < ? ";
        }

        sql += "ORDER BY logTime " + (isPagingBackwards ? "DESC" : "ASC");    		        
        sql += " ) AS part ";
        sql += " ORDER BY logTime ASC";   
        return sql;
    }

    private String buildQueryForMessages( final Long after, final Long before, final int maxResults, final boolean isPagingBackwards )
    {
        switch (org.jivesoftware.database.DbConnectionManager.getDatabaseType())
        {
            case mysql:
                return getStatementForMySQL( after, before, maxResults, isPagingBackwards );

            case sqlserver:
                return getStatementForSQLServer( after, before, maxResults, isPagingBackwards );

            default:
                return getStatementForMySQL( after, before, maxResults, isPagingBackwards ); //Standardsyntax like mysql!?
        }
    }

    private String buildQueryForTotalCount( final Long after, final Long before, final int maxResults, final boolean isPagingBackwards )
    {
        String sql = "SELECT count(*) FROM ofMucConversationLog ";
        sql += "WHERE messageId IS NOT NULL AND logTime > ? AND logTime <= ? AND roomID = ? AND (nickname IS NOT NULL OR subject IS NOT NULL) ";
        if ( with != null ) {
            if (with.getResource() == null) {
                sql += " AND sender LIKE ? ";
            } else {
                sql += " AND sender = ? ";
            }
        }
        if ( after != null ) {
            sql += " AND messageId > ? ";
        }
        if ( before != null ) {
            sql += " AND messageId < ? ";
        }
        return sql;
    }

    public PreparedStatement prepareStatement( final Connection connection, final Long after, final Long before, final int maxResults, final boolean isPagingBackwards, final boolean forTotalCount ) throws SQLException
    {
        final String query = forTotalCount ? buildQueryForTotalCount( after, before, maxResults, isPagingBackwards ) : buildQueryForMessages( after, before, maxResults, isPagingBackwards );
        final PreparedStatement pstmt = connection.prepareStatement( query );
        pstmt.setString( 1, StringUtils.dateToMillis( startDate ) );
        pstmt.setString( 2, StringUtils.dateToMillis( endDate ) );
        pstmt.setLong( 3, room.getID() );
        int pos = 3;

        if ( with != null ) {
            if (with.getResource() == null) {
                pstmt.setString( ++pos, with.toString() + "%" );
            } else {
                pstmt.setString( ++pos, with.toString() );
            }
        }

        if ( after != null ) {
            pstmt.setLong( ++pos, after );
        }

        if ( before != null ) {
            pstmt.setLong( ++pos, before );
        }

        return pstmt;
    }
}
