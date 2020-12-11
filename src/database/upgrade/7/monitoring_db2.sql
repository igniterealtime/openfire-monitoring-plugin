ALTER TABLE ofMessageArchive ADD COLUMN isPMforJID VARCHAR(1024) NULL;
CREATE INDEX ofMessageArchive_pm_idx ON ofMessageArchive (isPMforJID);

-- Update database version
UPDATE ofVersion SET version = 7 WHERE name = 'monitoring';
