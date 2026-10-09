-- Postgres lets every role execute a new function unless told otherwise, and that
-- default can only be changed globally, not per schema.
alter default privileges for role postgres revoke execute on functions from public;
alter default privileges for role postgres in schema public revoke execute on functions from anon, authenticated;
alter default privileges for role postgres in schema private revoke execute on functions from anon, authenticated;

revoke execute on all functions in schema public from public, anon, authenticated;
revoke execute on all functions in schema private from public, anon, authenticated;

grant execute on function private.has_plus() to authenticated;
grant execute on function public.purchase_plan(text, text) to authenticated;
grant execute on function public.seed_catalogue(jsonb) to service_role;
