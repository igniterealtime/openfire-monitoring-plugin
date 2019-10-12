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
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.DbConnectionManager.DatabaseType;

/**
 * Encapsulates responsibility of creating a database query that retrieves a specific subset (page) of archived messages related to a MUC room.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PaginatedMucMessageQuery
{
    private final Date startDate;
    private final Date endDate;
    private final MUCRoom room;
    private final String with;
    private final Long after;
    private final Long before;
    private final int maxResults;
    private final boolean isPagingBackwards;

    public PaginatedMucMessageQuery( Date startDate, Date endDate, MUCRoom room, String with, Long after, Long before, int maxResults, boolean isPagingBackwards )
    {
        this.startDate = startDate == null ? new Date( 0L ) : startDate ;
        this.endDate = endDate == null ? new Date() : endDate;
        this.room = room;
        this.with = null; // TODO: Suppress this, since we don't yet have requestor information for access control.
        this.after = after;
        this.before = before;
        this.maxResults = maxResults;
        this.isPagingBackwards = isPagingBackwards;
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
            sql += " AND sender = ? ";
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
            sql += " AND sender = ? ";
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
        String sql = null;
        switch (org.jivesoftware.database.DbConnectionManager.getDatabaseType())
        {
            case org.jivesoftware.database.DbConnectionManager.DatabaseType.mysql:
                sql=getStatementForMySQL();
            break;
                
            case org.jivesoftware.database.DbConnectionManager.DatabaseType.sqlserver:
                sql=getStatementForSQLServer();                
            break;                
                //TODO: Insert Syntax for other DB Types...
                
            default:
                 sql=getStatementForMySQL(); //Standardsyntax like mysql!?
            break;
        }
    		
        return sql;
    }

    private String buildQueryForTotalCount()
    {
        String sql = "SELECT count(*) FROM ofMucConversationLog ";
        sql += "WHERE messageId IS NOT NULL AND logTime > ? AND logTime <= ? AND roomID = ? AND (nickname IS NOT NULL OR subject IS NOT NULL) ";
        if ( with != null ) {
            sql += " AND sender = ? ";
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
            pstmt.setString( ++pos, with );
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
