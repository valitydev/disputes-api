ALTER TABLE dspt.dispute
    ADD COLUMN "skip_call_hg_for_create_adjustment" BOOLEAN NOT NULL DEFAULT TRUE;
