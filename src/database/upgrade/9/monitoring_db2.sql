CREATE TABLE ofMucRoomStatus (
    roomID                INTEGER      NOT NULL,
    roomJID               VARCHAR(512) NOT NULL,
    roomDestroyed         INTEGER      NOT NULL,
    CONSTRAINT ofMucRoomStatus_pk PRIMARY KEY (roomID)
);

ALTER TABLE ofConversation ADD COLUMN roomID INTEGER DEFAULT -1 NOT NULL;
CREATE INDEX ofConversation_room_idx ON ofConversation (roomID);

ALTER TABLE ofConParticipant ADD COLUMN roomID INTEGER DEFAULT -1 NOT NULL;
DROP INDEX entConPar_con_idx;
CREATE INDEX entConPar_con_idx ON ofConParticipant (roomID, conversationID, bareJID, jidResource, joinedDate);

ALTER TABLE ofMessageArchive ADD COLUMN roomID INTEGER DEFAULT -1 NOT NULL;
CREATE INDEX ofMessageArchive_room_idx ON ofMessageArchive (roomID);

-- Update database version
UPDATE ofVersion SET version = 9 WHERE name = 'monitoring';
