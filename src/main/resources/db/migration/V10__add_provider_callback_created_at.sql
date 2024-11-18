ALTER TABLE dspt.provider_callback
    ADD COLUMN "created_at" TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() at time zone 'utc');
