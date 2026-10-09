-- Plans may only be changed by the server. purchase_plan() let any signed-in user
-- call it straight through the Data API and upgrade themselves, so nobody may
-- execute it any more. grant_plan() replaces it and is for the secret key only.
revoke execute on function public.purchase_plan(text, text) from public, anon, authenticated, service_role;
comment on function public.purchase_plan(text, text) is 'Retired: replaced by grant_plan(). Nobody may execute it. Safe to drop.';

-- STAND-IN FOR BILLING on the API side: the API calls this without taking payment.
-- When Google Play Billing is wired in, the API verifies the purchase token first.
create function public.grant_plan(p_user uuid, p_plan text, p_period text default 'yearly') returns public.profiles
language plpgsql security definer set search_path = '' as $$
declare
  result public.profiles;
begin
  if p_plan not in ('plus', 'family') then
    raise exception 'Unknown plan' using errcode = '22023';
  end if;
  update public.profiles
  set plan = p_plan,
      billing_period = case when p_period = 'monthly' then 'monthly' else 'yearly' end,
      subscribed_at = case when plan = 'free' or subscribed_at is null then now() else subscribed_at end
  where id = p_user
  returning * into result;
  if result.id is null then
    raise exception 'No such account' using errcode = 'P0002';
  end if;
  return result;
end;
$$;
comment on function public.grant_plan(uuid, text, text) is 'Puts an account on a paid plan. Secret key only: the API calls it once a purchase is accepted.';
revoke execute on function public.grant_plan(uuid, text, text) from public, anon, authenticated;
grant execute on function public.grant_plan(uuid, text, text) to service_role;
