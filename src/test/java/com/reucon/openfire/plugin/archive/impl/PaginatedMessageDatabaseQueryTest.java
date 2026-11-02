/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package com.reucon.openfire.plugin.archive.impl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that {@link PaginatedMessageDatabaseQuery} returns each archived private ("whisper") message exactly
 * once when a query filters by occupant nickname, even when the occupant has rejoined the room (and so has more
 * than one row in {@code ofConParticipant} for the same conversation). See
 * <a href="https://github.com/igniterealtime/openfire-monitoring-plugin/issues/472">issue 472</a>.
 *
 * These tests build the SQL and bind its parameters using the same, package-visible methods that
 * {@link PaginatedMessageDatabaseQuery#getPage} and {@link PaginatedMessageDatabaseQuery#getTotalCount} use in
 * production ({@link PaginatedMessageDatabaseQuery#buildQueryForMessages},
 * {@link PaginatedMessageDatabaseQuery#buildQueryForTotalCount},
 * {@link PaginatedMessageDatabaseQuery#bindCommonParameters}). They run that SQL directly against a fresh
 * in-memory HSQLDB schema built here, rather than through {@code DbConnectionManager}, so that they don't need a
 * running Openfire server.
 */
public class PaginatedMessageDatabaseQueryTest
{
    private static final JID OWNER = new JID("owner@example.com");
    // A full occupant JID: the resource ("bob") is the nickname the private message filter matches on.
    private static final JID WITH_NICKNAME = new JID("room@conference.example.com/bob");
    private static final long CONVERSATION_ID = 1L;
    private static final long MESSAGE_ID = 42L;
    private static final long SENT_DATE = 1_000L;

    private Connection connection;

    @Before
    public void setUp() throws SQLException
    {
        // A fresh, uniquely-named in-memory database per test avoids any state leaking between tests.
        connection = DriverManager.getConnection(
            "jdbc:hsqldb:mem:" + getClass().getSimpleName() + System.nanoTime(), "SA", "");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE ofConversation (
                  conversationID BIGINT NOT NULL,
                  roomID         BIGINT NULL,
                  PRIMARY KEY (conversationID)
                )
                """);
            stmt.execute("""
                CREATE TABLE ofConParticipant (
                  conversationID BIGINT       NOT NULL,
                  bareJID        VARCHAR(255) NOT NULL,
                  nickname       VARCHAR(255) NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE ofMessageArchive (
                  messageID       BIGINT        NULL,
                  conversationID  BIGINT        NOT NULL,
                  fromJID         VARCHAR(1024) NOT NULL,
                  fromJIDResource VARCHAR(255)  NULL,
                  toJID           VARCHAR(1024) NOT NULL,
                  toJIDResource   VARCHAR(255)  NULL,
                  sentDate        BIGINT        NOT NULL,
                  stanza          LONGVARCHAR   NULL,
                  body            LONGVARCHAR   NULL,
                  isPMforJID      VARCHAR(1024) NULL
                )
                """);
            // A room conversation, so buildWhereClauseForPrivateMessages's "c.roomID IS NOT NULL" holds.
            stmt.execute("INSERT INTO ofConversation (conversationID, roomID) VALUES (" + CONVERSATION_ID + ", 100)");
            // The occupant ("bob") rejoined the room once during the conversation: two participant rows for the
            // same (conversationID, bareJID), which is exactly what a join on those two columns alone can't
            // distinguish, and exactly what issue 472 reports.
            stmt.execute("INSERT INTO ofConParticipant (conversationID, bareJID, nickname) VALUES ("
                + CONVERSATION_ID + ", 'bob@example.com', 'bob')");
            stmt.execute("INSERT INTO ofConParticipant (conversationID, bareJID, nickname) VALUES ("
                + CONVERSATION_ID + ", 'bob@example.com', 'bob')");
            // One private message, from bob (the occupant with two participant rows) to the archive owner.
            stmt.execute("INSERT INTO ofMessageArchive "
                + "(messageID, conversationID, fromJID, toJID, sentDate, body, isPMforJID) VALUES ("
                + MESSAGE_ID + ", " + CONVERSATION_ID + ", 'bob@example.com', "
                + "'room@conference.example.com', " + SENT_DATE + ", 'hello', 'owner@example.com')");
        }
    }

    @After
    public void tearDown() throws SQLException
    {
        connection.close();
    }

    @Test
    public void testPrivateMessageWithRejoinedOccupantIsReturnedOnceInPage() throws SQLException
    {
        final PaginatedMessageDatabaseQuery query =
            new PaginatedMessageDatabaseQuery(new Date(0), new Date(SENT_DATE + 1), OWNER, WITH_NICKNAME);

        final String sql = query.buildQueryForMessages(null, null, 10, false);
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            query.bindCommonParameters(pstmt);
            try (ResultSet rs = pstmt.executeQuery()) {
                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    assertEquals(MESSAGE_ID, rs.getLong("messageID"));
                }
                assertEquals("A message from an occupant with two participant rows (one rejoin) "
                    + "should be returned once, not once per participant row", 1, rowCount);
            }
        }
    }

    @Test
    public void testPrivateMessageWithRejoinedOccupantCountsAsOne() throws SQLException
    {
        final PaginatedMessageDatabaseQuery query =
            new PaginatedMessageDatabaseQuery(new Date(0), new Date(SENT_DATE + 1), OWNER, WITH_NICKNAME);

        final String sql = query.buildQueryForTotalCount();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            query.bindCommonParameters(pstmt);
            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }
}
