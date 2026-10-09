# Label Lens sample API

A stand-in backend so the Android app has something to talk to. All data is hardcoded in [src/data.js](src/data.js). Accounts, history and saved additives are kept in memory and reset when the server restarts. Nothing here is real: there is no database, no email, no Google sign-in and no billing.

## Run

```sh
pnpm install
pnpm start        # or `pnpm dev` to restart on changes
```

It listens on port 8787. Set `PORT` to change that.

**Demo account:** `sam.rivera@gmail.com` / `labellens` (Family plan, with sample history, saved additives and preferences). Any other email and password is rejected at log in. Sign-up accepts any valid email and starts on the Free plan.

## Options

| Variable | Effect |
| --- | --- |
| `PORT` | Port to listen on. Default 8787. |
| `FAIL_PURCHASE=1` | Every purchase fails with a 402, to see the "payment didn't go through" dialog. |
| `VERIFY_AFTER_SECONDS` | How long after sign-up the email counts as confirmed. Default 6. |

## Endpoints

Requests marked 🔒 need `Authorization: Bearer <token>` from log in or sign-up.

| Method and path | Returns |
| --- | --- |
| `GET /health` | `{ ok: true }` |
| `GET /additives` | The catalogue: summary of every additive, plus counts and dates |
| `GET /additives/:code` | One additive in full: bans, approvals, sections, sources |
| `GET /preferences/options` | Allergens, diets, additive groups and sensitivity groups to choose from |
| `GET /plans` | Plans, prices and the feature comparison |
| `GET /regions` | Regions and language settings |
| `POST /auth/login` | `{ token, user }`, or 401 |
| `POST /auth/google` | `{ token, user }` for the demo account |
| `POST /auth/signup` | `{ token, user }` for a new Free account |
| `POST /auth/forgot` | Always succeeds |
| `POST /auth/resend-verification` 🔒 | Always succeeds |
| `POST /auth/logout` 🔒 | Ends the session |
| `GET /me` 🔒 | The user. `verified` turns true a few seconds after sign-up |
| `DELETE /me` 🔒 | Deletes the session |
| `GET` / `PUT /me/preferences` 🔒 | The user's allergies, diet and avoids |
| `PUT /me/region` 🔒 | Sets the region |
| `GET /subscription` 🔒 | The current subscription, or null on Free |
| `POST /subscription/purchase` 🔒 | Moves the user to `plus` or `family` |
| `POST /subscription/restore` 🔒 | Returns the current subscription |
| `GET` / `POST` / `DELETE /history` 🔒 | Scan history |
| `GET /saved`, `PUT` / `DELETE /saved/:code` 🔒 | Saved additives |
| `GET /family`, `PUT /family/selection` 🔒 | Family profiles and who a scan is for |

Errors come back as `{ error, message }` with a matching HTTP status.
