ALTER TABLE ofMessageArchive ADD INDEX ofMessageArchive_sent_idx (sentDate);

-- Update database version
UPDATE ofVersion SET version = 6 WHERE name = 'monitoring';
