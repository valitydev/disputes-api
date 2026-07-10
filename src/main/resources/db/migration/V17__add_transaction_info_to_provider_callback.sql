ALTER TABLE dspt.provider_callback
    ADD COLUMN "transaction_info" BYTEA,
    ADD COLUMN "payment_status_success" BOOLEAN NOT NULL DEFAULT TRUE;
