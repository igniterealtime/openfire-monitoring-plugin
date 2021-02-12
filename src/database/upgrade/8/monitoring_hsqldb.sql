-- This is a MySQL-specific update.

-- Update database version
UPDATE ofVersion SET version = 8 WHERE name = 'monitoring';
