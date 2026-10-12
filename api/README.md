# Label Lens API

Serves the additive catalogue to the Android app from PostgreSQL, and holds the dataset the catalogue is built from.

## Run

From the repository root, `scripts/dev.sh --api-only` does all of this. By hand:

```sh
docker compose up -d --wait   # PostgreSQL, from ../docker-compose.yml
cd api
pnpm install
cp .env.example .env          # already matches the development database
pnpm db:migrate               # create the tables
pnpm seed                     # load the catalogue from data/
pnpm start                    # or `pnpm dev` to restart on changes
```

It listens on port 8787.

| Variable | Effect |
| --- | --- |
| `DATABASE_URL` | Where PostgreSQL is. Required. |
| `PORT`, `HOST` | Where to listen. Default `0.0.0.0:8787`. |
| `LOG_REQUESTS=1` | Print a line per request. For development; `scripts/dev.sh` turns it on. |
| `ALLOWED_ORIGINS` | Websites allowed to read the API from a browser, comma-separated. Needed when the site in `../www` is served from a different address than the API. |
| `CLIENT_IP_HEADER` | With `TRUST_PROXY`: a header a CDN puts the visitor's address in, such as `CF-Connecting-IP`. Checked before `X-Forwarded-For`. |
| `TRUST_PROXY=1` | Take the caller's address from `X-Forwarded-For`. Only behind your own proxy, otherwise callers can fake their address. |

### Docker

Everything Docker creates is named `labellens-*`:

| Thing | Name |
| --- | --- |
| Compose project | `labellens` |
| Container | `labellens-postgres` (PostgreSQL 17) |
| Volume | `labellens-postgres-data` |
| Network | `labellens-net` |

The database listens on `127.0.0.1:5440` only. Set `LABELLENS_DB_PORT` to move it, and change `DATABASE_URL` to match. The password in the compose file is for development; use a real one anywhere else.

```sh
docker compose down        # stop, keep the data
docker compose down -v     # stop and delete the data
docker exec -it labellens-postgres psql -U labellens labellens
```

## Endpoints

| Path | Returns |
| --- | --- |
| `GET /additives` | `{ meta, options, additives }`: every additive's list fields, plus the allergens, diets, groups and sensitivities the app offers |
| `GET /additives/:code` | One additive in full: list fields plus `regulatory`, `sections`, `sources`, `footnote`. Code in any letter case. 404 if unknown |
| `GET /health` | `{ ok, additives, version }` |

Both catalogue routes send an `ETag` and answer 304 to a matching `If-None-Match`. Responses are compressed with Brotli or gzip when the client accepts it. Errors are `{ error, message }`. Only `GET` and `HEAD` are accepted.

## Why it is fast

The catalogue changes only when someone runs `pnpm seed`, so nothing is worked out per request that can be worked out per version.

| Where | What is done |
| --- | --- |
| Start-up, and after each seed | The whole catalogue is read from PostgreSQL in three queries. Every response body (the list and all 522 pages) is serialised once and compressed once, with Brotli and gzip at their highest settings. |
| Each request | A map lookup and a write of bytes that already exist. No database query, no JSON encoding, no compression. |
| Unchanged data | `ETag` and 304 on the list and on every page, so a client that already has the data downloads nothing. |
| Request log | Rows collect in memory and are written together about four times a second: one `INSERT` for hundreds of requests. A request never waits for the database. |
| Log table | Partitioned by month with one small BRIN index, so inserts stay cheap as it grows and a month is deleted by dropping its partition. |
| New data | `pnpm seed` announces the new version over PostgreSQL `NOTIFY`. A running API is listening on one connection and reloads at once; no polling on the request path. |
| HTTP | Plain `node:http`. With three routes and ready-made bodies a framework would only add work. The one dependency is the PostgreSQL driver. |

Measured on a laptop, 50 connections, before and after this design (the "before" queried PostgreSQL per page and wrote one log row per request, uncompressed):

| | Before | After |
| --- | --- | --- |
| List, requests a second | 1,500 | 6,500 |
| List, bytes on the wire (gzip) | 161,614 | 12,053 (9,949 with Brotli) |
| One page, requests a second | 7,400 | 15,800 |
| One page, bytes on the wire (gzip) | 3,105 | 1,359 |

The load generator ran on the same machine and was itself a limit, so read the "after" column as a floor. Every one of the 200,908 requests in the run was logged, and the log's index took 24 kB.

If the database goes away, the API keeps serving from memory. Log rows wait (up to 50,000, oldest dropped beyond that) and are written when it comes back.

To go further, put a CDN in front: responses are marked `public` and revalidate by `ETag`, so the CDN can answer most requests itself. The cost is that requests it answers never reach the API, so they are not in `request_log`.

## What is recorded about a request

Each request to the two `/additives` routes adds one row to `request_log`, for analytics later. These are the only things kept:

| Column | Holds |
| --- | --- |
| `at` | When |
| `ip` | The address the request came from |
| `device_type` | The phone's make and model as the app sends it in `X-Device-Type` ("Google Pixel 9"). For anything else, a broad class worked out from the user-agent: `android browser`, `ios browser`, `desktop browser`, `script`, `other` or `unknown`. The user-agent string itself is not stored |
| `resource` | `list` or `detail` |
| `code` | Which additive, for `detail`. Null for the list, and for a code that does not exist |

Nothing else is stored: no account, no device identifier, no headers, no cookies. `/health` is not recorded. Writing rows never delays or fails a request; a crash can lose the last fraction of a second.

Still to decide, with the privacy policy: how long rows are kept. An IP address counts as personal data in the EU and elsewhere, so a retention limit (or storing a shortened address) will likely be needed. Nothing deletes old rows at the moment. The table is partitioned by month, so removing one is `drop table request_log_2026_10;`.

```sql
-- Most looked-up additives
select code, count(*) from request_log where resource = 'detail' and code is not null group by code order by 2 desc limit 20;
```

## Tests

```sh
pnpm test       # no database: catalogue rules, routes, what gets recorded
pnpm test:db    # against PostgreSQL, in a throwaway schema that is dropped afterwards
```

## How it is put together

| File | Does |
| --- | --- |
| [src/app.js](src/app.js) | The HTTP handler: routing, encoding choice, 304s |
| [src/catalogue-cache.js](src/catalogue-cache.js) | Loads the catalogue into memory, prepares every response body, reloads on a new seed |
| [src/request-log.js](src/request-log.js) | Decides what is recorded about a request, and writes it in batches |
| [src/catalogue.js](src/catalogue.js) | Builds the catalogue from the files in `data/`. Used by the seed and export scripts and by tests |
| [src/config.js](src/config.js) | The preference choices the app offers |
| [src/db.js](src/db.js) | The connection pool |
| [db/migrations/](db/migrations/) | The schema, as numbered SQL files |
| [scripts/migrate.js](scripts/migrate.js) | Applies migrations not yet applied |
| [scripts/seed.js](scripts/seed.js) | Loads the catalogue into PostgreSQL, in one transaction |
| [scripts/export-app-data.js](scripts/export-app-data.js) | Writes the copy of the catalogue that ships inside the app |

## The data

[data/](data/) holds the additive dataset: 522 additives, one researched entry each in `dist/single/<code>.json`, with the master list in `development/ins-summary-original.json`. It began as a copy of [is-it-healthy/data](https://github.com/is-it-healthy/data) (MIT) and was then completed in place; [data/development/REPORT.md](data/development/REPORT.md) describes how, and `development/sources/<code>.json` records where each fact came from. `python3 scripts/validate.py` (run inside `data/`) checks the files.

`pnpm seed` builds the catalogue from those files with [src/catalogue.js](src/catalogue.js) and loads it into PostgreSQL. Run it again whenever the data changes: a running API picks up the new version at once, and apps download the list again on their next start. `pnpm export:app` writes the same catalogue into the Android app, as the copy it falls back on with no connection.

### How the verdict is decided

Each entry records approval flags for the EU and US, a list of places that ban the additive, and an alert icon where a regulator has raised a concern. The app's verdict is read from those in `deriveVerdict`:

| Verdict | When | Count today |
| --- | --- | --- |
| Avoid | The entry names places that ban it | 14 |
| Caution | The entry carries the alert icon, or no EU or US approval could be confirmed | 243 |
| Safe | Approved somewhere, nothing flagged | 265 |

The short note beside the verdict ("Banned in the EU", "Intake can exceed the limit") is picked from the entry's own text. It summarises the entry; it is not a medical rating. "No EU or US approval confirmed" covers 104 additives and means what it says: the research could not confirm an approval, not that the additive is banned.

Still worked out from the text, each by a small function in the same file:

- **Name and other names**, split out of strings like `Sunset Yellow FCF (Orange Yellow S, FD&C Yellow 6)`.
- **Category and descriptor**, from `type`.
- **Groups** people can avoid (artificial colours, sweeteners, preservatives, flavour enhancers, palm oil), from category and description.
- **Sensitivities** (children, pregnancy, asthma), from keywords in the regulatory and side-effect text.
- **Origin** (animal, either, suitable for vegans, unknown), from the dietary-restrictions text. Only 70 entries have that text, so origin is unknown for most, and diet checks in the app only fire for those 70.

[data/overrides.json](data/overrides.json) can correct an entry without editing the dataset. It is empty at the moment.
