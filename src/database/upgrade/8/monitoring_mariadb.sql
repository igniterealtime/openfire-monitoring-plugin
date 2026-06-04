ALTER TABLE ofMessageArchive MODIFY COLUMN isPMforJID VARCHAR(255) NULL;

-- Update database version
UPDATE ofVersion SET version = 8 WHERE name = 'monitoring';
