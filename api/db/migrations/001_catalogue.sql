-- The additive catalogue. Written by `pnpm seed`, read by the API.

create table additives (
  code           text primary key check (code ~ '^E[0-9]{3,4}[a-z]{0,2}$'),
  sort_order     integer not null,
  name           text not null check (char_length(name) between 1 and 200),
  short_name     text not null check (char_length(short_name) between 1 and 200),
  category       text not null check (char_length(category) between 1 and 80),
  descriptor     text not null default '',
  category_icon  text not null default 'science',
  status         text not null check (status in ('safe', 'caution', 'avoid')),
  note           text not null,
  aliases        text[] not null default '{}',
  groups         text[] not null default '{}',
  sensitivities  text[] not null default '{}',
  origin         text not null default 'unknown' check (origin in ('animal', 'either', 'suitable', 'unknown')),
  banned_in      text[] not null default '{}',
  has_detail     boolean not null default false,
  updated_at     timestamptz not null default now()
);
comment on table additives is 'One row per food additive: what the list and search screens need.';
comment on column additives.status is 'General verdict, worked out by rule when the catalogue is seeded. Not a medical rating.';
create index additives_sort_order_idx on additives (sort_order);

create table additive_details (
  code        text primary key references additives (code) on delete cascade,
  regulatory  jsonb not null default '[]' check (jsonb_typeof(regulatory) = 'array'),
  sections    jsonb not null default '[]' check (jsonb_typeof(sections) = 'array'),
  sources     jsonb not null default '[]' check (jsonb_typeof(sources) = 'array'),
  footnote    text not null default ''
);
comment on table additive_details is 'The long-form page for an additive. Kept apart so listing the catalogue stays light.';

create table catalogue_meta (
  id             boolean primary key default true check (id),
  version        text not null,
  updated_label  text not null,
  sources        text not null,
  options        jsonb not null default '{}' check (jsonb_typeof(options) = 'object'),
  seeded_at      timestamptz not null default now()
);
comment on table catalogue_meta is 'Single row describing the catalogue currently loaded. version changes when the list does.';
comment on column catalogue_meta.options is 'The allergens, diets, groups and sensitivities the app offers in its preferences.';
