CREATE TABLE dspt.test_adjustment_from_callback
(
    id                                 UUID                        NOT NULL DEFAULT gen_random_uuid(),
    invoice_id                         CHARACTER VARYING           NOT NULL,
    payment_id                         CHARACTER VARYING           NOT NULL,
    status                             dspt.dispute_status         NOT NULL DEFAULT 'create_adjustment' :: dspt.dispute_status,
    changed_amount                     BIGINT,
    skip_call_hg_for_create_adjustment BOOLEAN                     NOT NULL DEFAULT TRUE,
    CONSTRAINT test_adjusment_from_callback_pkey PRIMARY KEY (id)
);
