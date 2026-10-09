# Label Lens API

The API the Android app talks to. It is backed by Supabase: Postgres holds the additive catalogue and each account's data, and Supabase Auth handles sign-up, log-in and email.

## Run

```sh
pnpm install
cp .env.example .env      # then fill in the three Supabase values
pnpm seed                 # first time, and whenever data/ changes
pnpm start                # or `pnpm dev` to restart on changes
```

It listens on port 8787. `.env.prod` is read first if it exists, then `.env`. Both are gitignored.

| Variable | Effect |
| --- | --- |
| `SUPABASE_URL` | The project URL. Required. |
| `SUPABASE_PUBLISHABLE_KEY` | Used for every request made for a user. Required. |
| `SUPABASE_SECRET_KEY` | Bypasses row level security. Needed for `pnpm seed`, purchases, logging out and deleting accounts. Server only. |
| `AUTH_REDIRECT_URL` | Where the link in the confirmation email lands, for example `https://your-api/auth/confirmed`. Must be allow-listed in the Supabase dashboard. |
| `PORT`, `HOST` | Where to listen. Default `0.0.0.0:8787`. |
| `TRUST_PROXY=1` | Take the caller's address from `X-Forwarded-For`. Only behind your own proxy. |
| `FAIL_PURCHASE=1` | Every purchase fails with a 402, to see the app's "payment didn't go through" dialog. |

## Tests

```sh
pnpm test         # no network: catalogue rules, input checks, rate limit, error shapes
pnpm test:live    # against the Supabase project in the env file
```

`test:live` creates two throwaway accounts with the secret key, checks the API end to end and that one account cannot read or change another's rows even when talking to the database directly, then deletes both.

## How it is put together

| File | Does |
| --- | --- |
| [src/supabase.js](src/supabase.js) | The three clients: anonymous, acting-as-the-caller, and admin |
| [src/app.js](src/app.js) | Routes, input checks, error mapping |
| [src/catalogue-store.js](src/catalogue-store.js) | Reads the catalogue from Postgres, holds it in memory, reloads when its version changes |
| [src/catalogue.js](src/catalogue.js) | Builds the catalogue from the files in `data/`. Used by the seed script and tests only |
| [src/config.js](src/config.js) | Plans, prices, preference options, regions |
| [scripts/seed.js](scripts/seed.js) | Loads the catalogue into Postgres |
| [../supabase/migrations/](../supabase/migrations/) | The database schema, as applied |

A request for account data goes: the app sends its Supabase access token, the API checks the token against the project's signing keys, then queries Postgres **as that user** with the publishable key. Row level security decides what comes back. The API cannot return another account's rows even if a route has a bug, because the database will not hand them over.

## Database

| Table | Holds | Who can read | Who can write |
| --- | --- | --- | --- |
| `additives` | One row per additive: list and search fields | Anyone | Seed function only |
| `additive_details` | Long-form page: regulatory lines, sections, sources | Anyone | Seed function only |
| `catalogue_meta` | One row: version, counts, data date | Anyone | Seed function only |
| `profiles` | Name, plan, billing period, region | Owner | Owner: name and region only. Plan: server only |
| `preferences` | Allergies, diet, groups, additives, sensitivities | Owner | Owner |
| `saved_additives` | Saved additive codes | Owner | Owner, on a paid plan |
| `scans` | Scan history: codes and verdict, never the photo or text | Owner | Owner, on a paid plan. Newest 500 kept |
| `family_profiles` | Family members | Owner | Owner: `selected` only |

Security measures in place:

- **Row level security on every table**, with policies per operation and per role. Tables start with no privileges for `anon` and `authenticated`; each grant is explicit, down to column level on `profiles` and `family_profiles`.
- **Paid features enforced in the database**, not just the app: the insert policies on `scans` and `saved_additives` require a paid plan.
- **No function is callable unless granted.** Postgres lets everyone execute new functions by default; that default is revoked. `seed_catalogue` and `grant_plan` are for the secret key only. Helpers live in a `private` schema that the Data API does not expose.
- **Security-definer functions pin `search_path`** to empty and qualify every name.
- **A trigger creates the profile and preferences** when Supabase Auth creates a user, so there is no window where an account has no row.
- **Deleting the auth user removes everything**, through `on delete cascade`.
- **Check constraints** bound every free-text and list column.
- **The secret key is used for three things only**, each for the caller whose token was just verified: ending the session, deleting the account, changing its plan.
- **In the API:** auth routes are rate limited per address (20 a minute), bodies are capped at 64 KB, security headers are set, errors never pass a database or auth message through, and logs record the path only.
- **Sign-up and password-reset answers do not reveal** whether an email has an account.

Supabase's security advisor reports no findings for this schema.

### Still to do in the Supabase dashboard

These are project settings, not SQL, so they could not be set from here:

1. **Authentication > URL Configuration:** set the Site URL, and add your `AUTH_REDIRECT_URL` to Redirect URLs. Until then the confirmation link works but lands on `localhost:3000`.
2. **Authentication > Sign In / Providers > Email:** raise the minimum password length to 8 or more and turn on leaked-password protection (Pro plan).
3. **Authentication > Emails:** set up your own SMTP sender. The built-in one is limited to a few emails an hour and is for testing only.
4. **Authentication > Rate Limits:** every log-in reaches Supabase from this API's address, so the per-address limits there apply to all users together. Raise them, or deploy the API where it can forward client addresses.
5. Drop the retired function when convenient: `drop function public.purchase_plan(text, text);`. Nobody can execute it, but it could not be dropped from here.
6. Before launch, turn on point-in-time recovery or check the backup schedule.

## The data

[data/](data/) is a copy of [is-it-healthy/data](https://github.com/is-it-healthy/data) (MIT). [data/SOURCE.json](data/SOURCE.json) records the commit it was copied from. To update, copy the newer files over it and change that file.

`pnpm seed` builds the catalogue with [src/catalogue.js](src/catalogue.js) from three parts and loads it into Postgres:

| File | Used for |
| --- | --- |
| `development/ins-summary-original.json` | The full list: code, names, type and approval flags for 511 additives |
| `dist/single/<code>.json` | Details for 350 of them. Two layouts exist and both are read. 11 of these are not in the list above and are added. |
| `overrides.json` | Corrections on top of the dataset. Added here, not part of the source. |

That gives 522 additives, 336 with a written entry. The rest have a name, category and approval flag only, and the app says so on their page.

### What is worked out, not read

**The dataset has no safety rating.** The app's Safe / Caution / Avoid verdict comes from these rules in `deriveVerdict`:

| Verdict | When |
| --- | --- |
| Avoid | The entry (or `overrides.json`) names places that ban it. Today: E102 and E171. |
| Caution | The side-effects text reports hyperactivity, asthma or a cancer link; or the text says it is restricted; or there is no written entry and no EU approval flag ("Limited information", 123 additives). |
| Safe | Everything else. |

Treat this as a starting point, not a medical judgement. A real rating needs someone qualified to review each entry.

Also derived, each by a small function in the same file:

- **Name and other names**, split out of strings like `Sunset Yellow FCF (Orange Yellow S, FD&C Yellow 6)`.
- **Category and descriptor**, from `type` or, failing that, `function`.
- **Groups** people can avoid (artificial colours, sweeteners, preservatives, flavour enhancers, palm oil), from category and origin text.
- **Sensitivities** (children, pregnancy, asthma), from keywords in the side-effects text.
- **Origin** (animal, either, suitable for vegans, unknown), from the dietary-restrictions text.
- **Text clean-up**: stray control characters, and sentences that pointed at links lost when the source was scraped.

Known gaps in the source: the US approval flag is present for only 25 additives, so its absence is not reported. A missing EU flag is shown on the additive's page as "no EU approval on record" but does not change the verdict, because the flag is missing for many additives that are approved.

`overrides.json` has one entry: E171 (titanium dioxide), which the EU banned in food in 2022, after the dataset was compiled.

## What is real and what stands in

| Real | Stand-in |
| --- | --- |
| Catalogue and additive pages, from Postgres | Purchases: the plan is granted without payment. Google Play Billing is not wired in |
| Accounts, sessions, email confirmation, password reset and Google sign-in, through Supabase Auth | |
| Preferences, history, saved additives, region per account, under row level security | Prices and plan features in [src/config.js](src/config.js) |
| Input validation and consistent `{ error, message }` errors | Family profiles: a family plan shows its owner only; adding members is not built |

There is no demo account. Create one in the app, or with the dashboard.

## Endpoints

🔒 needs `Authorization: Bearer <token>`, the access token from log in or refresh.

| Method and path | Returns |
| --- | --- |
| `GET /health` | `{ ok, additives, version }` |
| `GET /additives` | `{ meta, additives }`. Sends an `ETag`; answers 304 to a matching `If-None-Match` |
| `GET /additives/:code` | One additive in full. Code in any letter case. 404 if unknown |
| `GET /preferences/options` | Allergens, diets, groups and sensitivities to choose from |
| `GET /plans` | Plans, prices, comparison. Trial end date is worked out from today |
| `GET /regions` | Regions and language settings |
| `POST /auth/signup` | 202 `{ needsVerification: true, token: null, user }` while email confirmation is on; 201 with a session if it is off. 422 for a bad email or a password under 8 characters |
| `POST /auth/login` | `{ token, refreshToken, expiresAt, user }`. 401 for wrong details, 403 `email_not_confirmed` |
| `POST /auth/refresh` | `{ refreshToken }` in, a new session out. Access tokens last an hour |
| `POST /auth/google` | `{ idToken, nonce }` from Google's account picker on the phone in, a session out. Supabase checks the token. 401 `invalid_token` if it is refused |
| `POST /auth/forgot` | Sends the reset email. Always `{ ok: true }` for a valid email |
| `POST /auth/resend-verification` | `{ email }` in. Sends the confirmation email again |
| `GET /auth/confirmed` | A small page for the confirmation link to land on |
| `POST /auth/logout` 🔒 | Ends this session, including its refresh token |
| `GET /me` 🔒 | The user, with plan, `verified` and counts |
| `DELETE /me` 🔒 | Deletes the account and all its sessions |
| `GET` / `PUT /me/preferences` 🔒 | Allergies, diet, groups, additives, sensitivities. Unknown values are dropped |
| `GET` / `PUT /me/region` 🔒 | The chosen region |
| `GET /subscription` 🔒 | `{ subscription }`, null on Free |
| `POST /subscription/purchase` 🔒 | `{ planId: "plus" or "family", period: "yearly" or "monthly" }` |
| `POST /subscription/restore` 🔒 | The current subscription |
| `GET` / `POST` / `DELETE /history` 🔒 | Scan history, newest first, with "Today" / "Yesterday" labels. `POST` is 403 `plus_required` on Free |
| `GET /saved`, `PUT` / `DELETE /saved/:code` 🔒 | Saved additives. `PUT` is 403 `plus_required` on Free |
| `GET /family`, `PUT /family/selection` 🔒 | Family profiles and who a scan is for |
