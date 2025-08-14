ALTER TABLE dspt.dispute
    ADD COLUMN "mode" CHARACTER VARYING;
ALTER TABLE dspt.dispute
    ADD COLUMN "tech_error_msg" CHARACTER VARYING;
ALTER TABLE dspt.dispute
    ADD COLUMN "provider_msg" CHARACTER VARYING;
ALTER TABLE dspt.dispute
    ADD COLUMN "admin_msg" CHARACTER VARYING;

BEGIN;
UPDATE dspt.dispute
SET "tech_error_msg" = "error_message"
WHERE error_message IS NOT NULL;
ALTER TABLE dspt.dispute
DROP COLUMN "error_message";
COMMIT;

ALTER TABLE dspt.provider_callback
    DROP COLUMN "error_reason";
ALTER TABLE dspt.provider_callback
    DROP COLUMN "approve_reason";
