create type dspt.notification_status as ENUM (
    'pending',
    'delivered',
    'attempts_limit'
);

create table dspt.notification (
    dispute_id uuid not null,
    notification_url character varying not null,
    next_attempt_after timestamp without time zone not null,
    max_attempts int not null,
    status dspt.notification_status not null default 'pending' ::dspt.notification_status,
    constraint notification_pkey primary key (dispute_id)
);
