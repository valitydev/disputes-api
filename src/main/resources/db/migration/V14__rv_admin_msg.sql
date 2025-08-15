BEGIN;
UPDATE dspt.dispute
SET provider_msg = admin_msg
WHERE admin_msg IS NOT NULL;
ALTER TABLE dspt.dispute
DROP COLUMN admin_msg;
COMMIT;
