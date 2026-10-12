# Label Lens for Android

Kotlin, Jetpack Compose and Material 3. Built from the design handoff in [../design/](../design/).

There is no account and no subscription: the app is meant to be sold once on Google Play and then simply work. Everything a person sets up or collects stays on their phone. The one thing the app goes online for is additive information, which it reads from the API in [../api/](../api/).

## Build and run

You need JDK 17 and the Android SDK (API 37).

`../scripts/dev.sh` does everything: database, API, build, install, open. By hand, with the API already running:

```sh
adb reverse tcp:8787 tcp:8787   # lets the phone reach the API on this computer
./gradlew :app:installDebug
```

A release build needs the real server's address, which must be HTTPS:

```sh
./gradlew :app:assembleRelease -PapiBaseUrl=https://api.example.com
```

Tests:

```sh
./gradlew :app:testDebugUnitTest          # on this computer
./gradlew :app:connectedDebugAndroidTest  # on a connected phone; removes the app's data on it
```

The phone tests run with no server, so they exercise what a first launch with no connection gets: that a label can be read, that every additive page in the bundled data loads, and that a backup restores.

## How it fits together

| Path under `app/src/main/` | Contents |
| --- | --- |
| `assets/catalogue.json`, `assets/details.json` | A copy of the additive data, used when the server can't be reached. Generated, see below. |
| `kotlin/.../data/Api.kt` | Fetches the list and additive pages from the server. |
| `kotlin/.../data/Store.kt` | App state and every action that changes it. One instance for the whole process. |
| `kotlin/.../data/Models.kt` | The types in the bundled data and in the saved document. |
| `kotlin/.../data/Matcher.kt` | Finds E-numbers and additive names in the text read from a label. |
| `kotlin/.../data/Verdicts.kt` | Checks an additive against the person's allergies, diet and avoids. |
| `kotlin/.../data/Navigation.kt` | The list of screens and a plain back stack. |
| `kotlin/.../ui/theme/` | Colours, type and shapes from the design tokens. |
| `kotlin/.../ui/Components.kt` | Shared pieces: buttons, segmented list rows, status pills, icons. |
| `kotlin/.../ui/screens/` | One file per area: home, search, scan, results, detail, onboarding, preferences, history and saved, profile, settings. |

### The additive data

The app reads additive information from the API:

- **The list** (`GET /additives`) is fetched at launch and kept on the phone. Later launches ask only whether it has changed.
- **An additive's page** (`GET /additives/:code`) is fetched when it is opened.

With no connection, the app uses the list it last fetched and the pages that shipped with it, so scanning, search and every page still work. The shipped copy is the two files in `assets/`, generated from [../api/data/](../api/data/):

```sh
cd ../api && pnpm export:app
```

Run that before a release so a fresh install with no connection starts from current data.

Each request tells the server the phone's make and model. The server records that, the IP address and which additive was asked for; see [../api/README.md](../api/README.md). Nothing the person has set up or scanned is sent.

### What the app stores, and how it survives

Everything a person sets up or collects is one document: their list, preferences, saved additives, scan history and theme. It lives in the app's private storage.

| Event | What happens |
| --- | --- |
| App update | Nothing: updates never touch app data. |
| Reinstall, or a new phone | Android's own backup brings it back, if backup is on for the device. The manifest opts in and [backup_rules.xml](app/src/main/res/xml/backup_rules.xml) and [data_extraction_rules.xml](app/src/main/res/xml/data_extraction_rules.xml) limit it to that one file. |
| Backup is off, or the person wants their own copy | Settings has "Back up to a file" and "Restore from a file". |

Two limits worth knowing: Android backs up roughly once a day while the phone is idle, charging and on Wi-Fi, so the last few hours can be missing after a reinstall; and a person who has turned Android backup off gets nothing back unless they made a file.

Label photos are read on the phone with ML Kit text recognition and are never stored or sent. Note for the privacy policy: now that the app has internet access, the ML Kit library can send Google its own usage statistics (not the photos or text).

### Selling it

The app has no in-app purchases and no licence check. Set a price for it in Play Console; Play handles the purchase before download.

## Not built

- **Links out of the app** (help, terms, privacy, licence) point at placeholder pages in this repository.
- **Play licence verification.** A paid app can be copied off a phone and installed elsewhere. Play's licensing library can check for a real purchase.
- **Privacy policy and terms.** Not written yet. The Settings screen states what is collected.

## Fonts

Gabarito and Hanken Grotesk (SIL Open Font Licence) and a subset of Material Symbols Rounded (Apache 2.0) are bundled in `app/src/main/res/font/`. Icons are drawn from the Material Symbols font by name. To use an icon that isn't in the subset yet, add its name to [tools/generate_symbols.py](tools/generate_symbols.py) and run it. The script's header explains what it needs.
