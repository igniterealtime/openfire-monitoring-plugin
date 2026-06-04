CREATE INDEX ofMessageArchive_sent_idx ON ofMessageArchive (sentDate);

-- Update database version
UPDATE ofVersion SET version = 6 WHERE name = 'monitoring';
