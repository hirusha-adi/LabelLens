# LabelLens
This is an Android app that uses OCR to identify food additives from ingredient labels and provides clear, evidence-based information about them.

## What's in this repository

| Folder | What it is |
| --- | --- |
| [android/](android/) | The app. Kotlin and Jetpack Compose. |
| [api/](api/) | A small sample API the app talks to. Every value in it is a hardcoded placeholder. |
| [design/](design/) | The design handoff the app was built from. |

## Run it

Start the API:

```sh
cd api
pnpm install
pnpm start
```

Then, with a phone or emulator connected, build and install the app:

```sh
cd android
adb reverse tcp:8787 tcp:8787   # lets the phone reach the API on this computer
./gradlew :app:installDebug
```

Log in with the demo account, `sam.rivera@gmail.com` / `labellens`, to see a Family plan with history, saved additives and preferences already filled in. Or create an account with any email to start on the Free plan.

See [android/README.md](android/README.md) and [api/README.md](api/README.md) for more.
