ALTER TABLE ofMessageArchive ADD messageID BIGINT NULL;
ALTER TABLE ofMessageArchive ADD stanza BLOB SUB_TYPE TEXT CHARACTER SET UTF8 NULL;

-- Update database version
UPDATE ofVersion SET version = 3 WHERE name = 'monitoring';
