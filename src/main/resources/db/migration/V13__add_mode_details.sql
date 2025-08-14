ALTER TABLE dspt.dispute
    ADD COLUMN "mode" CHARACTER VARYING;
ALTER TABLE dspt.dispute
    ADD COLUMN "tech_error_msg" CHARACTER VARYING;
ALTER TABLE dspt.dispute
    ADD COLUMN "provider_msg" CHARACTER VARYING;
ALTER TABLE dspt.provider_callback
    DROP COLUMN "error_reason";
ALTER TABLE dspt.provider_callback
    DROP COLUMN "approve_reason";
