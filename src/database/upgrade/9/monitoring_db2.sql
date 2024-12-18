DECLARE GLOBAL TEMPORARY TABLE SESSION.ofTmpStatus (
    roomID          INTEGER       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    roomJID         VARCHAR(1024) NOT NULL,
    roomDestroyed   SMALLINT      DEFAULT 0
) ON COMMIT PRESERVE ROWS;

INSERT INTO SESSION.ofTmpStatus (roomJID)
SELECT DISTINCT room
FROM ofConversation;

UPDATE SESSION.ofTmpStatus
SET roomDestroyed = 1
WHERE roomJID NOT IN
      (SELECT ofMucRoom.name CONCAT '@' CONCAT ofMucService.subdomain CONCAT '.' CONCAT ofProperty.propValue
       FROM ofMucRoom
                JOIN ofMucService ON ofMucRoom.serviceID = ofMucService.serviceID
                CROSS JOIN ofProperty
       WHERE ofProperty.name = 'xmpp.domain');

CREATE TABLE ofMucRoomStatus
(
    roomID        INTEGER       PRIMARY KEY,
    roomJID       VARCHAR(1024) NOT NULL,
    roomDestroyed SMALLINT DEFAULT 0
);

INSERT INTO ofMucRoomStatus (SELECT * FROM SESSION.ofTmpStatus);
INSERT INTO ofID (idType, id)
SELECT 655, MAX(roomID) + 1
FROM ofMucRoomStatus;
DROP TABLE SESSION.ofTmpStatus;

ALTER TABLE ofConversation ADD COLUMN roomID BIGINT NOT NULL WITH DEFAULT -1;
CREATE INDEX ofConversation_room_idx ON ofConversation (roomID);

UPDATE ofConversation c
SET roomID = (SELECT s.roomID
              FROM ofMucRoomStatus s
              WHERE c.room = s.roomJID);

-- Update database version
UPDATE ofVersion SET version = 9 WHERE name = 'monitoring';
