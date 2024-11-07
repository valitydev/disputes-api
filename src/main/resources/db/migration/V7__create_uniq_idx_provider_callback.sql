CREATE UNIQUE INDEX provider_callback_invoice_payment_idx ON dspt.provider_callback USING btree (invoice_id, payment_id);

ALTER TABLE dspt.provider_callback
    ADD COLUMN "error_reason" CHARACTER VARYING;

ALTER TABLE dspt.provider_callback
    ADD COLUMN "approve_reason" CHARACTER VARYING;

ALTER TABLE dspt.provider_callback DROP COLUMN status;

CREATE TYPE dspt.provider_payments_status AS ENUM ('succeeded', 'failed','cancelled', 'create_adjustment');

ALTER TABLE dspt.provider_callback
    ADD COLUMN "status" dspt.provider_payments_status NOT NULL DEFAULT 'create_adjustment' :: dspt.provider_payments_status;
