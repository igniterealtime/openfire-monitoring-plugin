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
package org.jivesoftware.openfire.archive;

import org.jivesoftware.database.DbConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A database access object for ofMucRoomStatus table.
 *
 * @author Huy Vu, hqv126@gmail.com
 */
public class MUCRoomStatusDAO {
    private static final Logger Log = LoggerFactory.getLogger(MUCRoomStatusDAO.class);

    private static final String INSERT_NEW_ROOM = "INSERT INTO ofMucRoomStatus (roomID, roomJID, roomDestroyed) VALUES (?, ?, 0)";
    private static final String UPDATE_ROOM_DESTROYED_STATUS = "UPDATE ofMucRoomStatus SET roomDestroyed=1 WHERE roomID=?";
    private static final String LOAD_ROOM_INFO = "SELECT roomID, roomJID FROM ofMucRoomStatus WHERE roomDestroyed=0";

    /**
     * Loads the room JID to room ID map from the database.
     *
     * This method retrieves all active (not destroyed) rooms from the `ofMucRoomStatus` table
     * and populates a map with the room JID as the key and the room ID as the value.
     *
     * @return A map where the key is the room JID and the value is the room ID.
     */
    public static Map<JID, Long> loadRoomJIDToIDMap() {
        Map<JID, Long> roomJIDToIDMap = new ConcurrentHashMap<>();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_ROOM_INFO);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                long roomID = rs.getLong("roomID");
                JID roomJID = new JID(rs.getString("roomJID"));
                roomJIDToIDMap.put(roomJID, roomID);
            }
        } catch (SQLException e) {
            Log.error("Error loading room info", e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return roomJIDToIDMap;
    }

    /**
     * Inserts a new room information into the database.
     *
     * This method inserts a new room entry into the `ofMucRoomStatus` table with the given room ID and room JID.
     *
     * @param roomID The ID of the room to be inserted.
     * @param roomJID The JID of the room to be inserted.
     * @return true if the room information was successfully inserted, false otherwise.
     */
    public static boolean hasInsertedRoomInfo(long roomID, JID roomJID) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_NEW_ROOM);
            pstmt.setLong(1, roomID);
            pstmt.setString(2, roomJID.toString());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException sqle) {
            Log.error("An exception occurred while trying to create a new room {} status.", roomJID, sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return false;
    }

    /**
     * Updates the room status to destroyed in the database.
     *
     * This method sets the `roomDestroyed` status to 1 for the specified room ID in the `ofMucRoomStatus` table.
     *
     * @param roomID The ID of the room to be marked as destroyed.
     * @return true if the room status was successfully updated, false otherwise.
     */
    public static boolean hasUpdatedRoomDestroyedStatus(long roomID) {
        // Update the room status to destroyed in the database
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_ROOM_DESTROYED_STATUS);
            pstmt.setLong(1, roomID);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            Log.error("Error updating room destroyed status for room with id: " + roomID, e);
        } finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return false;
    }
}
