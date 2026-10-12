-- Makes request_log cheap to write and cheap to prune.
--
-- * Partitioned by month. Deleting old data becomes dropping a partition, which
--   is instant and leaves no bloat, instead of a slow DELETE.
-- * One BRIN index on the time column instead of B-trees. Rows arrive in time
--   order, so BRIN stays a few kilobytes and costs almost nothing per insert.
--   Analytics queries filter by time first and then scan one or two partitions.
-- * No primary key: nothing refers to a log row, and a unique index would be
--   the most expensive thing about every insert.

alter table request_log rename to request_log_unpartitioned;

create table request_log (
  id           bigint generated always as identity,
  at           timestamptz not null default now(),
  ip           inet,
  device_type  text not null check (char_length(device_type) between 1 and 80),
  resource     text not null check (resource in ('list', 'detail')),
  code         text check (char_length(code) <= 12)
) partition by range (at);

comment on table request_log is 'What was asked for, from which address, on what kind of device. Nothing else about a request is stored. Partitioned by month: drop a partition to delete that month.';

create index request_log_at_brin on request_log using brin (at);

-- Catches anything outside the monthly partitions, so an insert can never fail
-- for want of one.
create table request_log_default partition of request_log default;

-- Creates the partitions for this month and the next few. Safe to call often.
create function ensure_request_log_partitions(months_ahead integer default 2) returns void
language plpgsql as $$
declare
  month_start date;
  partition_name text;
begin
  for i in 0..months_ahead loop
    month_start := (date_trunc('month', now()) + make_interval(months => i))::date;
    partition_name := 'request_log_' || to_char(month_start, 'YYYY_MM');
    if to_regclass(partition_name) is null then
      execute format(
        'create table %I partition of request_log for values from (%L) to (%L)',
        partition_name, month_start, (month_start + interval '1 month')::date
      );
    end if;
  end loop;
end;
$$;

select ensure_request_log_partitions();

insert into request_log (at, ip, device_type, resource, code)
select at, ip, device_type, resource, code from request_log_unpartitioned order by at;

drop table request_log_unpartitioned;
