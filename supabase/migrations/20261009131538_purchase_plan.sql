-- STAND-IN FOR BILLING. Moves the caller onto a paid plan without taking payment.
-- Replace with a server-side check of the Google Play purchase token, run with
-- the secret key, and revoke this grant when that exists.
create function public.purchase_plan(p_plan text, p_period text default 'yearly') returns public.profiles
language plpgsql security definer set search_path = '' as $$
declare
  uid uuid := (select auth.uid());
  result public.profiles;
begin
  if uid is null then
    raise exception 'Not signed in' using errcode = '28000';
  end if;
  if p_plan not in ('plus', 'family') then
    raise exception 'Unknown plan' using errcode = '22023';
  end if;
  update public.profiles
  set plan = p_plan,
      billing_period = case when p_period = 'monthly' then 'monthly' else 'yearly' end,
      subscribed_at = case when plan = 'free' or subscribed_at is null then now() else subscribed_at end
  where id = uid
  returning * into result;
  return result;
end;
$$;
comment on function public.purchase_plan(text, text) is 'Billing stand-in: grants a paid plan with no payment. Remove before charging money.';
grant execute on function public.purchase_plan(text, text) to authenticated;
