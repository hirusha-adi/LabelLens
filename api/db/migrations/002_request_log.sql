-- One row per catalogue request, for usage analytics.
--
-- These four facts are the only things recorded about a request. There is no
-- account, no device identifier, no full user-agent string and no request body.
-- An IP address is personal data in many places: how long rows are kept and what
-- the privacy policy says about them still has to be decided.

create table request_log (
  id           bigint generated always as identity primary key,
  at           timestamptz not null default now(),
  ip           inet,
  device_type  text not null check (char_length(device_type) between 1 and 80),
  -- 'list' for the whole catalogue, 'detail' for one additive.
  resource     text not null check (resource in ('list', 'detail')),
  -- The additive asked for, as the catalogue spells it. Null for 'list', and for
  -- a code the catalogue does not have.
  code         text check (char_length(code) <= 12)
);
comment on table request_log is 'What was asked for, from which address, on what kind of device. Nothing else about a request is stored.';
create index request_log_at_idx on request_log (at);
create index request_log_code_idx on request_log (code) where code is not null;
