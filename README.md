# LabelLens
This is an Android app that uses OCR to identify food additives from ingredient labels and provides clear, evidence-based information about them.

## What's in this repository

| Path | What it is |
| --- | --- |
| [android/](android/) | The app. Kotlin and Jetpack Compose. No account: everything a person sets up stays on their phone. |
| [api/](api/) | The API the app gets additive information from, backed by PostgreSQL, and the dataset behind it. |
| [www/](www/) | The website: landing page and a web version of the app. |
| [docker-compose.yml](docker-compose.yml) | PostgreSQL for development. Everything it creates is named `labellens-*`. |
| [docker-compose.prod.yml](docker-compose.prod.yml) | The database, API and website for the server. See Deploy below. |
| [scripts/dev.sh](scripts/dev.sh) | Starts the database and API, then builds, installs and opens the app. |
| [design/](design/) | The design handoff the app was built from. |

## Run it

With Docker running and a phone or emulator connected:

```sh
scripts/dev.sh
```

That starts PostgreSQL, creates and fills its tables, starts the API, builds the debug app, installs it and opens it. The API stays in the terminal until Ctrl-C. `--api-only` and `--app-only` do one half, and `--web` runs the website at http://localhost:5173 instead of the app.

## Deploy

[docker-compose.prod.yml](docker-compose.prod.yml) runs PostgreSQL, the API and the website on a server that already has Caddy in front, following the conventions in [documents/server.md](documents/server.md).

On the server, in `/srv/hirusha-labellens/`:

```sh
git pull
cp .env.prod.example .env     # first time only: set POSTGRES_PASSWORD and the two domains
docker compose -f docker-compose.prod.yml up -d --build
```

Each `up` creates or updates the database tables and reloads the additive catalogue before the API starts, so deploying new data is the same command.

Then tell the server's Caddy about the two addresses, in `/etc/caddy/Caddyfile`, and reload it with `caddyr`:

```
label-lens.cc {
    reverse_proxy hirusha-labellens-web:3000
}

api.label-lens.cc {
    reverse_proxy hirusha-labellens-api:8787
}
```

| Container | What | Networks |
| --- | --- | --- |
| `hirusha-labellens-postgres` | PostgreSQL 17. Files in `./data/postgres` | `hirusha-labellens` only |
| `hirusha-labellens-setup` | Runs once per `up`: migrations, then seed. Exits | `hirusha-labellens` only |
| `hirusha-labellens-api` | The API, port 8787 | `intranet_1`, `hirusha-labellens` |
| `hirusha-labellens-web` | The website as static files, port 3000 | `intranet_1` |

No port is published on the host: the database is reachable only by the API, and the API and website only through Caddy.

The website's API address is fixed when its image is built. After changing `API_DOMAIN` in `.env`, rebuild with `--build`.

Back up `./data/postgres`, or better `docker exec hirusha-labellens-postgres pg_dump -U labellens labellens`. The catalogue can always be rebuilt from the repository; the request log cannot.

For the Android app to use the server, build it with `-PapiBaseUrl=https://api.label-lens.cc`.

See [android/README.md](android/README.md), [api/README.md](api/README.md) and [www/README.md](www/README.md) for each part.

## Licence

[Label Lens Non-Commercial License](LICENSE). In short:

- **Free to use, copy, change and share for anything that is not commercial**, as long as the licence text stays with it and anything built on it keeps the same terms.
- **Any money-making or business use needs written permission from Hirusha Adikari first.** Ask at hello@hirusha.xyz.
- **No warranty and no liability.** The additive information is general information, not medical or dietary advice.

That summary is only a guide; the [LICENSE](LICENSE) file is what applies.

Fonts, icons, the text-recognition engine and other third-party parts keep their own licences. The dataset in `api/data/` carries the MIT licence it was published under.

Because commercial use is restricted, this is a source-available project, not "open source" in the usual sense of that term.
