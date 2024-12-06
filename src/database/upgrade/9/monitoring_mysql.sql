CREATE TABLE ofMucRoomStatus (
  roomID                BIGINT        NOT NULL,
  roomJID               VARCHAR(255)  NOT NULL,
  roomDestroyed         TINYINT       NOT NULL,
  PRIMARY KEY (roomID)
);

ALTER TABLE ofConversation ADD COLUMN roomID BIGINT NOT NULL DEFAULT -1;
CREATE INDEX ofConversation_room_idx ON ofConversation (roomID);

ALTER TABLE ofConParticipant ADD COLUMN roomID BIGINT NOT NULL DEFAULT -1;
DROP INDEX ofConParticipant_conv_idx ON ofConParticipant;
CREATE INDEX ofConParticipant_conv_idx ON ofConParticipant (roomID, conversationID, bareJID, jidResource, joinedDate);

ALTER TABLE ofMessageArchive ADD COLUMN roomID BIGINT NOT NULL DEFAULT -1;
CREATE INDEX ofMessageArchive_room_idx ON ofMessageArchive (roomID);

-- Update database version
UPDATE ofVersion SET version = 9 WHERE name = 'monitoring';
