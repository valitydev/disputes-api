CREATE TABLE dspt.retry_provider_payment_check_status
(
    id                                 UUID                        NOT NULL DEFAULT gen_random_uuid(),
    invoice_id                         CHARACTER VARYING           NOT NULL,
    payment_id                         CHARACTER VARYING           NOT NULL,
    CONSTRAINT retry_provider_payment_check_status_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX retry_provider_payment_check_status_invoice_payment_idx ON dspt.retry_provider_payment_check_status USING btree (invoice_id, payment_id);
