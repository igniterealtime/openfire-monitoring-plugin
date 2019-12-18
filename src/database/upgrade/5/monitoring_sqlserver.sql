INSERT INTO ofID (idType, id) VALUES (604, (SELECT coalesce(max(messageID), 0) + 1 FROM ofMessageArchive) );

-- Update database version
UPDATE ofVersion SET version = 5 WHERE name = 'monitoring';
