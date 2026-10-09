-- Label Lens schema.
--
-- Two kinds of data live here:
--   the additive catalogue, readable by anyone, written only by the seed function
--   per-account data, readable and writable only by the account that owns it
--
-- Every table has row level security on. Table privileges are granted explicitly
-- and as narrowly as the API needs, so a missing policy fails closed.

-- Helpers that must not be callable through the Data API live in their own schema.
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
grant usage on schema private to authenticated, service_role;

alter default privileges in schema public revoke execute on functions from public, anon, authenticated;
alter default privileges in schema private revoke execute on functions from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Catalogue
-- ---------------------------------------------------------------------------

create table public.additives (
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
comment on table public.additives is 'One row per food additive: what the list and search screens need.';
comment on column public.additives.status is 'General verdict, worked out by rule when the catalogue is seeded. Not a medical rating.';

create index additives_sort_order_idx on public.additives (sort_order);

create table public.additive_details (
  code        text primary key references public.additives (code) on delete cascade,
  regulatory  jsonb not null default '[]' check (jsonb_typeof(regulatory) = 'array'),
  sections    jsonb not null default '[]' check (jsonb_typeof(sections) = 'array'),
  sources     jsonb not null default '[]' check (jsonb_typeof(sources) = 'array'),
  footnote    text not null default ''
);
comment on table public.additive_details is 'The long-form page for an additive. Kept apart so listing the catalogue stays light.';

create table public.catalogue_meta (
  id              boolean primary key default true check (id),
  version         text not null,
  total_count     integer not null,
  detailed_count  integer not null,
  updated_label   text not null,
  sources         text not null,
  seeded_at       timestamptz not null default now()
);
comment on table public.catalogue_meta is 'Single row describing the catalogue currently loaded. version changes when the list does.';

-- ---------------------------------------------------------------------------
-- Accounts
-- ---------------------------------------------------------------------------

create table public.profiles (
  id              uuid primary key references auth.users (id) on delete cascade,
  name            text not null default '' check (char_length(name) <= 80),
  plan            text not null default 'free' check (plan in ('free', 'plus', 'family')),
  billing_period  text not null default 'yearly' check (billing_period in ('yearly', 'monthly')),
  subscribed_at   timestamptz,
  region          text check (region ~ '^[a-z]{2,3}$'),
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
comment on table public.profiles is 'One row per account, created by a trigger when the auth user is. plan is changed only by purchase_plan().';

create table public.preferences (
  user_id        uuid primary key default auth.uid() references public.profiles (id) on delete cascade,
  allergies      jsonb not null default '[]' check (jsonb_typeof(allergies) = 'array' and pg_column_size(allergies) < 8192),
  diet           text not null default 'none' check (char_length(diet) <= 30),
  diet_level     text not null default 'avoid' check (diet_level in ('avoid', 'warn')),
  groups         jsonb not null default '{}' check (jsonb_typeof(groups) = 'object' and pg_column_size(groups) < 2048),
  additives      text[] not null default '{}' check (cardinality(additives) <= 100),
  sensitivities  text[] not null default '{}' check (cardinality(sensitivities) <= 10),
  updated_at     timestamptz not null default now()
);
comment on table public.preferences is 'Allergies, diet and things to avoid. Personal checks run on the phone against this.';

create table public.saved_additives (
  user_id     uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  code        text not null references public.additives (code) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (user_id, code)
);
create index saved_additives_code_idx on public.saved_additives (code);

create table public.scans (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  name        text not null default 'Scanned label' check (char_length(name) between 1 and 60),
  verdict     text not null check (verdict in ('safe', 'caution', 'avoid')),
  codes       text[] not null default '{}' check (cardinality(codes) <= 60),
  scanned_at  timestamptz not null default now()
);
comment on table public.scans is 'Scan history. Holds the additive codes found, never the photo or the label text.';
create index scans_user_scanned_at_idx on public.scans (user_id, scanned_at desc);

create table public.family_profiles (
  id          uuid primary key default gen_random_uuid(),
  owner_id    uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  position    smallint not null default 0,
  name        text not null check (char_length(name) between 1 and 40),
  role        text check (char_length(role) <= 20),
  initials    text check (char_length(initials) <= 3),
  icon        text check (char_length(icon) <= 40),
  tone        text not null default 'primary' check (tone in ('primary', 'secondary', 'tertiary')),
  short       text not null default '' check (char_length(short) <= 60),
  summary     text not null default '' check (char_length(summary) <= 120),
  selected    boolean not null default true,
  created_at  timestamptz not null default now()
);
create index family_profiles_owner_idx on public.family_profiles (owner_id, position);

-- ---------------------------------------------------------------------------
-- Helper functions and triggers
-- ---------------------------------------------------------------------------

create function private.set_updated_at() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger profiles_set_updated_at before update on public.profiles
  for each row execute function private.set_updated_at();
create trigger preferences_set_updated_at before update on public.preferences
  for each row execute function private.set_updated_at();

-- True when the caller is on a paid plan. Security definer so policies on other
-- tables can ask without being given read access to anyone else's profile.
create function private.has_plus() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.profiles
    where id = (select auth.uid()) and plan in ('plus', 'family')
  );
$$;
grant execute on function private.has_plus() to authenticated;

-- Gives every new auth user a profile and an empty set of preferences.
create function private.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, name)
  values (
    new.id,
    left(coalesce(nullif(btrim(new.raw_user_meta_data ->> 'name'), ''), split_part(coalesce(new.email, ''), '@', 1)), 80)
  );
  insert into public.preferences (user_id) values (new.id);
  return new;
end;
$$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function private.handle_new_user();

-- Keeps each account's history to its newest 500 scans.
create function private.trim_scans() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  delete from public.scans
  where user_id = new.user_id
    and id in (
      select id from public.scans
      where user_id = new.user_id
      order by scanned_at desc
      offset 500
    );
  return null;
end;
$$;

create trigger scans_trim after insert on public.scans
  for each row execute function private.trim_scans();

-- ---------------------------------------------------------------------------
-- Privileges: start from nothing, grant what the API uses
-- ---------------------------------------------------------------------------

revoke all on all tables in schema public from anon, authenticated;

grant select on public.additives, public.additive_details, public.catalogue_meta to anon, authenticated;

grant select on public.profiles to authenticated;
grant update (name, region) on public.profiles to authenticated;

grant select, insert, update on public.preferences to authenticated;
grant select, insert, delete on public.saved_additives to authenticated;
grant select, insert, delete on public.scans to authenticated;
grant select on public.family_profiles to authenticated;
grant update (selected) on public.family_profiles to authenticated;

-- ---------------------------------------------------------------------------
-- Row level security
-- ---------------------------------------------------------------------------

alter table public.additives        enable row level security;
alter table public.additive_details enable row level security;
alter table public.catalogue_meta   enable row level security;
alter table public.profiles         enable row level security;
alter table public.preferences      enable row level security;
alter table public.saved_additives  enable row level security;
alter table public.scans            enable row level security;
alter table public.family_profiles  enable row level security;

-- Catalogue: anyone may read. No write policy exists, so nobody can write through the API.
create policy "Anyone can read additives" on public.additives
  for select to anon, authenticated using (true);
create policy "Anyone can read additive details" on public.additive_details
  for select to anon, authenticated using (true);
create policy "Anyone can read catalogue meta" on public.catalogue_meta
  for select to anon, authenticated using (true);

-- Profiles: read and edit your own. Column grants above limit edits to name and region.
create policy "Read own profile" on public.profiles
  for select to authenticated using ((select auth.uid()) = id);
create policy "Update own profile" on public.profiles
  for update to authenticated using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

create policy "Read own preferences" on public.preferences
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "Create own preferences" on public.preferences
  for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "Update own preferences" on public.preferences
  for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- Saved additives and scan history are paid features: reading and removing your
-- own is always allowed, adding needs a paid plan.
create policy "Read own saved additives" on public.saved_additives
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "Save additives on a paid plan" on public.saved_additives
  for insert to authenticated with check ((select auth.uid()) = user_id and (select private.has_plus()));
create policy "Remove own saved additives" on public.saved_additives
  for delete to authenticated using ((select auth.uid()) = user_id);

create policy "Read own scans" on public.scans
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "Record scans on a paid plan" on public.scans
  for insert to authenticated with check ((select auth.uid()) = user_id and (select private.has_plus()));
create policy "Delete own scans" on public.scans
  for delete to authenticated using ((select auth.uid()) = user_id);

create policy "Read own family profiles" on public.family_profiles
  for select to authenticated using ((select auth.uid()) = owner_id);
create policy "Choose who a scan is for" on public.family_profiles
  for update to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
