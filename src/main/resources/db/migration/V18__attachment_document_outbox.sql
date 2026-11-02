CREATE TYPE dspt.attachment_document_status AS ENUM ('pending', 'succeeded', 'failed');

CREATE TABLE dspt.attachment_document (
    file_id character varying PRIMARY KEY,
    dispute_id UUID NOT NULL,
    invoice_id character varying NOT NULL,
    payment_id character varying NOT NULL,
    sha256 character varying(64) NOT NULL,
    size_bytes bigint NOT NULL CHECK (size_bytes >= 0),
    mime_type character varying NOT NULL,
    file_uploaded_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    status dspt.attachment_document_status NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() at time zone 'utc'),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() at time zone 'utc'),
    next_attempt_after TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (now() at time zone 'utc'),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    last_error character varying,
    registry_document_id character varying
);

CREATE INDEX attachment_document_pending_idx
    ON dspt.attachment_document (next_attempt_after, file_id)
    WHERE status = 'pending';
