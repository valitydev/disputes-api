alter type dspt.dispute_status add value 'cancelled';
alter type dspt.dispute_status add value 'manual_created';
alter type dspt.dispute_status add value 'manual_pending';
alter type dspt.dispute_status add value 'create_adjustment';
alter table dspt.dispute
    add column skip_call_hg_for_create_adjustment boolean not null default true;
