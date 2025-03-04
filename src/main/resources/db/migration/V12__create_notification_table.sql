CREATE TABLE dspt.notification (
    dispute_id uuid NOT NULL,
    notification_url bytea NOT NULL,
    next_check_after timestamp WITHOUT time zone NOT NULL,
    retry int NOT NULL DEFAULT 0,
    CONSTRAINT notification_pkey PRIMARY KEY (dispute_id)
);
