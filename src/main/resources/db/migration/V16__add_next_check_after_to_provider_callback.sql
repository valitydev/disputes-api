ALTER TABLE dspt.provider_callback
    ADD COLUMN "next_check_after" TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() at time zone 'utc');

CREATE INDEX provider_callback_status_next_check_after_idx
    ON dspt.provider_callback USING btree (status, next_check_after);
