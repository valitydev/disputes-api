CREATE SCHEMA IF NOT EXISTS dspt;

CREATE TYPE dspt.dispute_status AS ENUM ('created','pending', 'succeeded', 'failed');

CREATE TABLE dspt.dispute
(
    id                     BIGSERIAL                   NOT NULL,
    created_at             TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    invoice_id             CHARACTER VARYING           NOT NULL,
    payment_id             CHARACTER VARYING           NOT NULL,
    status                 dspt.dispute_status         NOT NULL DEFAULT 'created' :: dspt.dispute_status,
    provider_id            INT                         NOT NULL,
    terminal_id            INT                         NOT NULL,
    provider_trx_id        CHARACTER VARYING           NOT NULL,
    amount                 BIGINT,
    currency_name          CHARACTER VARYING,
    currency_symbolic_code CHARACTER VARYING,
    currency_numeric_code  INT,
    currency_exponent      INT,
    reason                 CHARACTER VARYING,
    error_message          CHARACTER VARYING,
    changed_amount         BIGINT,
    CONSTRAINT dispute_pkey PRIMARY KEY (id)
);

CREATE TABLE dspt.file_meta
(
    file_id    CHARACTER VARYING NOT NULL,
    dispute_id BIGINT            NOT NULL,
    mime_type CHARACTER VARYING NOT NULL,
    CONSTRAINT file_meta_pkey PRIMARY KEY (file_id)
);

CREATE TABLE dspt.provider_dispute
(
    provider_dispute_id CHARACTER VARYING NOT NULL,
    dispute_id          BIGINT            NOT NULL,
    CONSTRAINT provider_dispute_meta_pkey PRIMARY KEY (dispute_id)
);
