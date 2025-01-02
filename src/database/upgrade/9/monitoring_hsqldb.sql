-- Add column that will contain the unique numeric ID of a room.
ALTER TABLE ofConversation ADD COLUMN roomID BIGINT NULL;

-- Populate the new column with the numeric ID of the room from the ofMucRoom table.
UPDATE ofConversation SET roomID = (
    SELECT ofMucRoom.roomID FROM ofMucRoom
    JOIN ofMucService ON ofMucRoom.serviceID = ofMucService.serviceID
    CROSS JOIN ofProperty
    WHERE ofProperty.name = 'xmpp.domain'
    AND ofMucRoom.name || '@' || ofMucService.subdomain || ofProperty.propValue = ofConversation.room
)
WHERE room IS NOT NULL AND room <> '';

ALTER TABLE ofConversation ADD INDEX ofConversation_room_idx (roomID);

-- Update database version
UPDATE ofVersion SET version = 9 WHERE name = 'monitoring';
