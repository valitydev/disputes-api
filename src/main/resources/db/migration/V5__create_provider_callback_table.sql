CREATE TABLE dspt.provider_callback
(
    id                                 UUID                        NOT NULL DEFAULT gen_random_uuid(),
    invoice_id                         CHARACTER VARYING           NOT NULL,
    payment_id                         CHARACTER VARYING           NOT NULL,
    status                             dspt.dispute_status         NOT NULL DEFAULT 'create_adjustment' :: dspt.dispute_status,
    changed_amount                     BIGINT,
    skip_call_hg_for_create_adjustment BOOLEAN                     NOT NULL DEFAULT TRUE,
    CONSTRAINT provider_callback_pkey PRIMARY KEY (id)
);
