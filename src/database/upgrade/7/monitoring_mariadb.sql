ALTER TABLE ofMessageArchive ADD COLUMN isPMforJID VARCHAR(255) NULL;
ALTER TABLE ofMessageArchive ADD INDEX ofMessageArchive_pm_idx (isPMforJID);

-- Update database version
UPDATE ofVersion SET version = 7 WHERE name = 'monitoring';
