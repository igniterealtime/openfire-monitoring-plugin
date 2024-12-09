CREATE TABLE ofMucRoomStatus (
  roomID                INTEGER       NOT NULL,
  roomJID               VARCHAR(1024) NOT NULL,
  roomDestroyed         SMALLINT      NOT NULL,
  CONSTRAINT ofMucRoomStatus_pk PRIMARY KEY (roomID)
);

ALTER TABLE ofConversation ADD COLUMN roomID BIGINT DEFAULT -1 NOT NULL;
CREATE INDEX ofConversation_room_idx ON ofConversation (roomID);

ALTER TABLE ofConParticipant ADD COLUMN roomID BIGINT DEFAULT -1 NOT NULL;
DROP INDEX ofConParticipant_conv_idx;
CREATE INDEX ofConParticipant_conv_idx ON ofConParticipant (roomID, conversationID, bareJID, jidResource, joinedDate);

ALTER TABLE ofMessageArchive ADD COLUMN roomID BIGINT DEFAULT -1 NOT NULL;
CREATE INDEX ofMessageArchive_room_idx ON ofMessageArchive (roomID);

-- Update database version
UPDATE ofVersion SET version = 9 WHERE name = 'monitoring';
