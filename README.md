# LabelLens
This is an Android app that uses OCR to identify food additives from ingredient labels and provides clear, evidence-based information about them.

## What's in this repository

| Folder | What it is |
| --- | --- |
| [android/](android/) | The app. Kotlin and Jetpack Compose. |
| [api/](api/) | The API the app talks to. Backed by Supabase (Postgres and Auth). |
| [supabase/](supabase/) | Database migrations: tables, row level security, functions. |
| [design/](design/) | The design handoff the app was built from. |

## Run it

Start the API (needs a Supabase project; see [api/README.md](api/README.md) for the keys):

```sh
cd api
pnpm install
cp .env.example .env    # fill in the Supabase values
pnpm seed               # loads the additive catalogue, first time only
pnpm start
```

Then, with a phone or emulator connected, build and install the app:

```sh
cd android
adb reverse tcp:8787 tcp:8787   # lets the phone reach the API on this computer
./gradlew :app:installDebug
```

Create an account in the app with a real email address: Supabase sends a confirmation link, and the app carries on by itself once you open it.

See [android/README.md](android/README.md) and [api/README.md](api/README.md) for more.
