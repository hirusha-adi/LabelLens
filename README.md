# LabelLens
This is an Android app that uses OCR to identify food additives from ingredient labels and provides clear, evidence-based information about them.

## What's in this repository

| Path | What it is |
| --- | --- |
| [android/](android/) | The app. Kotlin and Jetpack Compose. No account: everything a person sets up stays on their phone. |
| [api/](api/) | The API the app gets additive information from, backed by PostgreSQL, and the dataset behind it. |
| [www/](www/) | The website: landing page and a web version of the app. |
| [docker-compose.yml](docker-compose.yml) | PostgreSQL for development. Everything it creates is named `labellens-*`. |
| [scripts/dev.sh](scripts/dev.sh) | Starts the database and API, then builds, installs and opens the app. |
| [design/](design/) | The design handoff the app was built from. |

## Run it

With Docker running and a phone or emulator connected:

```sh
scripts/dev.sh
```

That starts PostgreSQL, creates and fills its tables, starts the API, builds the debug app, installs it and opens it. The API stays in the terminal until Ctrl-C. `--api-only` and `--app-only` do one half, and `--web` runs the website at http://localhost:5173 instead of the app.

See [android/README.md](android/README.md), [api/README.md](api/README.md) and [www/README.md](www/README.md) for each part.
