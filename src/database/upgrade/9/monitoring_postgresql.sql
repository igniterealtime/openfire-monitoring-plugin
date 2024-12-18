CREATE TEMPORARY TABLE ofTmpStatus (
    roomID          SERIAL        PRIMARY KEY,
    roomJID         VARCHAR(1024) NOT NULL,
    roomDestroyed   SMALLINT      DEFAULT 0
);

INSERT INTO ofTmpStatus (roomJID)
SELECT DISTINCT room
FROM ofConversation;

UPDATE ofTmpStatus
SET roomDestroyed = 1
WHERE roomJID NOT IN (SELECT ofMucRoom.name || '@' || ofMucService.subdomain || '.' || ofProperty.propValue
                      FROM ofMucRoom
                               JOIN ofMucService ON ofMucRoom.serviceID = ofMucService.serviceID
                               CROSS JOIN ofProperty
                      WHERE ofProperty.name = 'xmpp.domain');

CREATE TABLE ofMucRoomStatus
(
  roomID        INTEGER         PRIMARY KEY,
  roomJID       VARCHAR(1024)   NOT NULL,
  roomDestroyed SMALLINT        DEFAULT 0
);

INSERT INTO ofMucRoomStatus (SELECT * FROM ofTmpStatus);
INSERT INTO ofID (idType, id)
SELECT 655, MAX(roomID) + 1
FROM ofMucRoomStatus;
DROP TABLE ofTmpStatus;

ALTER TABLE ofConversation ADD COLUMN roomID INTEGER NOT NULL DEFAULT -1;
CREATE INDEX ofConversation_room_idx ON ofConversation (roomID);

UPDATE ofConversation
SET roomID = s.roomID
    FROM ofMucRoomStatus s
WHERE ofConversation.room = s.roomJID;

-- Update database version
UPDATE ofVersion SET version = 9 WHERE name = 'monitoring';
