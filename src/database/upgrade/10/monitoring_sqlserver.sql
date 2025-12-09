CREATE INDEX ofConParticipant_con_nck ON ofConParticipant (conversationID, nickname);
CREATE INDEX ofMessageArchive_pm_dir ON ofMessageArchive (isPMforJID, fromJID, toJID);
CREATE INDEX ofMessageArchive_from_to ON ofMessageArchive (fromJID, toJID);

-- Update database version
UPDATE ofVersion SET version = 10 WHERE name = 'monitoring';
