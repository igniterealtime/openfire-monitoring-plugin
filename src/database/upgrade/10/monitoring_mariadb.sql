ALTER TABLE ofConParticipant ADD INDEX ofConParticipant_con_nck (conversationID, nickname);
ALTER TABLE ofMessageArchive ADD INDEX ofMessageArchive_pm_dir  (isPMforJID, fromJID, toJID);
ALTER TABLE ofMessageArchive ADD INDEX ofMessageArchive_from_to (fromJID, toJID);

-- Update database version
UPDATE ofVersion SET version = 10 WHERE name = 'monitoring';
