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

import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.util.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;

import org.xmpp.packet.JID;

/**
 * Encapsulates responsibility of creating a database query that retrieves a specific subset (page) of archived messages related to a MUC room.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PaginatedMucMessageDatabaseQuery
{
    private final Date startDate;
    private final Date endDate;
    private final MUCRoom room;
    private final JID with;
    private final Long after;
    private final Long before;
    private final int maxResults;
    private final boolean isPagingBackwards;

    public PaginatedMucMessageDatabaseQuery( Date startDate, Date endDate, MUCRoom room, JID with, Long after, Long before, int maxResults, boolean isPagingBackwards )
    {
        this.startDate = startDate == null ? new Date( 0L ) : startDate ;
        this.endDate = endDate == null ? new Date() : endDate;
        this.room = room;
        this.with = with;
        this.after = after;
        this.before = before;
        this.maxResults = maxResults;
        this.isPagingBackwards = isPagingBackwards;
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

    public Long getAfter()
    {
        return after;
    }

    public Long getBefore()
    {
        return before;
    }

    public int getMaxResults()
    {
        return maxResults;
    }

    public boolean isPagingBackwards()
    {
        return isPagingBackwards;
    }

    @Override
    public String toString()
    {
        return "PaginatedMucMessageQuery{" +
            "startDate=" + startDate +
            ", endDate=" + endDate +
            ", room=" + room +
            ", with='" + with + '\'' +
            ", after=" + after +
            ", before=" + before +
            ", maxResults=" + maxResults +
            ", isPagingBackwards=" + isPagingBackwards +
            '}';
    }

    private String getStatementForMySQL()
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

    private String getStatementForSQLServer()
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

    private String buildQueryForMessages()
    {
        switch (org.jivesoftware.database.DbConnectionManager.getDatabaseType())
        {
            case mysql:
                return getStatementForMySQL();

            case sqlserver:
                return getStatementForSQLServer();

            default:
                return getStatementForMySQL(); //Standardsyntax like mysql!?
        }
    }

    private String buildQueryForTotalCount()
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

    public PreparedStatement prepareStatement( Connection connection, boolean forTotalCount ) throws SQLException
    {
        final String query = forTotalCount ? buildQueryForTotalCount() : buildQueryForMessages();
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
