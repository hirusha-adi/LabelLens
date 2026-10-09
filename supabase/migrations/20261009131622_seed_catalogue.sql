-- Upserts the catalogue in the payload, in one transaction. Additives that are
-- no longer in the dataset are removed by the seed script afterwards.
-- Payload: { meta: {...}, additives: [ {...summary fields, regulatory, sections, sources, footnote} ] }
-- Only the secret key (service_role) may call it.
create function public.seed_catalogue(payload jsonb) returns integer
language plpgsql security definer set search_path = '' as $$
declare
  loaded integer;
begin
  if jsonb_typeof(payload -> 'additives') is distinct from 'array' or jsonb_array_length(payload -> 'additives') = 0 then
    raise exception 'Payload has no additives' using errcode = '22023';
  end if;

  create temporary table incoming on commit drop as
  select item, ordinality::integer as sort_order
  from jsonb_array_elements(payload -> 'additives') with ordinality as t (item, ordinality);

  insert into public.additives as a (
    code, sort_order, name, short_name, category, descriptor, category_icon, status, note,
    aliases, groups, sensitivities, origin, banned_in, has_detail, updated_at
  )
  select
    item ->> 'code', sort_order, item ->> 'name', item ->> 'shortName', item ->> 'category',
    coalesce(item ->> 'descriptor', ''), coalesce(item ->> 'categoryIcon', 'science'), item ->> 'status', item ->> 'note',
    array(select jsonb_array_elements_text(coalesce(item -> 'aliases', '[]'))),
    array(select jsonb_array_elements_text(coalesce(item -> 'groups', '[]'))),
    array(select jsonb_array_elements_text(coalesce(item -> 'sensitivities', '[]'))),
    coalesce(item ->> 'origin', 'unknown'),
    array(select jsonb_array_elements_text(coalesce(item -> 'bannedIn', '[]'))),
    coalesce((item ->> 'hasDetail')::boolean, false),
    now()
  from incoming
  on conflict (code) do update set
    sort_order = excluded.sort_order, name = excluded.name, short_name = excluded.short_name,
    category = excluded.category, descriptor = excluded.descriptor, category_icon = excluded.category_icon,
    status = excluded.status, note = excluded.note, aliases = excluded.aliases, groups = excluded.groups,
    sensitivities = excluded.sensitivities, origin = excluded.origin, banned_in = excluded.banned_in,
    has_detail = excluded.has_detail, updated_at = excluded.updated_at;

  insert into public.additive_details (code, regulatory, sections, sources, footnote)
  select
    item ->> 'code', coalesce(item -> 'regulatory', '[]'), coalesce(item -> 'sections', '[]'),
    coalesce(item -> 'sources', '[]'), coalesce(item ->> 'footnote', '')
  from incoming
  on conflict (code) do update set
    regulatory = excluded.regulatory, sections = excluded.sections,
    sources = excluded.sources, footnote = excluded.footnote;

  select count(*) into loaded from public.additives;

  insert into public.catalogue_meta (id, version, total_count, detailed_count, updated_label, sources, seeded_at)
  values (
    true, payload -> 'meta' ->> 'version', loaded,
    (select count(*) from public.additives where has_detail),
    coalesce(payload -> 'meta' ->> 'updatedLabel', ''), coalesce(payload -> 'meta' ->> 'sources', ''), now()
  )
  on conflict (id) do update set
    version = excluded.version, total_count = excluded.total_count, detailed_count = excluded.detailed_count,
    updated_label = excluded.updated_label, sources = excluded.sources, seeded_at = excluded.seeded_at;

  return loaded;
end;
$$;
comment on function public.seed_catalogue(jsonb) is 'Upserts the additive catalogue in one transaction. Callable with the secret key only. Additives no longer in the dataset are removed by the seed script afterwards.';
grant execute on function public.seed_catalogue(jsonb) to service_role;
