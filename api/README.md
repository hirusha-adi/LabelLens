# Label Lens API

Serves the additive dataset in [data/](data/) to the Android app and keeps accounts, preferences, scan history and saved additives.

## Run

```sh
pnpm install
pnpm start        # or `pnpm dev` to restart on changes
pnpm test
```

It listens on port 8787.

**Demo account:** `sam.rivera@gmail.com` / `labellens`. It is created the first time the server starts, on the Family plan, with sample history, saved additives and preferences. Sign-up works with any email and starts on the Free plan.

| Variable | Effect |
| --- | --- |
| `PORT`, `HOST` | Where to listen. Default `0.0.0.0:8787`. |
| `DB_FILE` | Where accounts are kept. Default `var/db.json`. Delete the file to start fresh. |
| `DATA_DIR` | Where the dataset is. Default `data/`. |
| `FAIL_PURCHASE=1` | Every purchase fails with a 402, to see the "payment didn't go through" dialog. |
| `VERIFY_AFTER_SECONDS` | How long after sign-up the email counts as confirmed. Default 6. |

## The data

[data/](data/) is a copy of [is-it-healthy/data](https://github.com/is-it-healthy/data) (MIT). [data/SOURCE.json](data/SOURCE.json) records the commit it was copied from. To update, copy the newer files over it and change that file.

The catalogue is built in memory at start-up by [src/catalogue.js](src/catalogue.js) from three parts:

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
| Catalogue and additive pages from the dataset | Purchases: always succeed, no Google Play Billing |
| Accounts with scrypt-hashed passwords and random session tokens, kept in `var/db.json` | "Continue with Google": signs in the demo account |
| Preferences, history, saved additives, region per account | Email: none sent. Confirmation happens by waiting; password reset does nothing |
| Input validation and consistent `{ error, message }` errors | Prices and plan features in [src/config.js](src/config.js) |
| | Family profiles: fixed sample profiles for the demo account, one profile for others |

The store is a single JSON file written by one process. Replace [src/db.js](src/db.js) with a database before running more than one instance.

## Endpoints

🔒 needs `Authorization: Bearer <token>` from log in or sign-up.

| Method and path | Returns |
| --- | --- |
| `GET /health` | `{ ok, additives, version }` |
| `GET /additives` | `{ meta, additives }`. Sends an `ETag`; answers 304 to a matching `If-None-Match` |
| `GET /additives/:code` | One additive in full. Code in any letter case. 404 if unknown |
| `GET /preferences/options` | Allergens, diets, groups and sensitivities to choose from |
| `GET /plans` | Plans, prices, comparison. Trial end date is worked out from today |
| `GET /regions` | Regions and language settings |
| `POST /auth/signup` | 201 `{ token, user }`. 422 for a bad email or a password under 8 characters, 409 if the email is taken |
| `POST /auth/login` | `{ token, user }`, or 401 |
| `POST /auth/google` | `{ token, user }` for the demo account |
| `POST /auth/forgot` | Always `{ ok: true }` for a valid email |
| `POST /auth/resend-verification` 🔒 | `{ ok: true }` |
| `POST /auth/logout` 🔒 | Ends this session |
| `GET /me` 🔒 | The user, with plan, `verified` and counts |
| `DELETE /me` 🔒 | Deletes the account and all its sessions |
| `GET` / `PUT /me/preferences` 🔒 | Allergies, diet, groups, additives, sensitivities. Unknown values are dropped |
| `GET` / `PUT /me/region` 🔒 | The chosen region |
| `GET /subscription` 🔒 | `{ subscription }`, null on Free |
| `POST /subscription/purchase` 🔒 | `{ planId: "plus" or "family", period: "yearly" or "monthly" }` |
| `POST /subscription/restore` 🔒 | The current subscription |
| `GET` / `POST` / `DELETE /history` 🔒 | Scan history, newest first, with "Today" / "Yesterday" labels |
| `GET /saved`, `PUT` / `DELETE /saved/:code` 🔒 | Saved additives |
| `GET /family`, `PUT /family/selection` 🔒 | Family profiles and who a scan is for |
