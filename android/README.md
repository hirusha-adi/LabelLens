# Label Lens for Android

Kotlin, Jetpack Compose and Material 3. Built from the design handoff in [../design/](../design/).

## Build and run

You need JDK 17 and the Android SDK (API 37). Start the sample API in [../api/](../api/) first.

```sh
adb reverse tcp:8787 tcp:8787
./gradlew :app:installDebug
```

`adb reverse` makes `127.0.0.1:8787` on the phone point at the API on your computer. To reach the API another way, pass its address at build time:

```sh
./gradlew :app:installDebug -PapiBaseUrl=http://192.168.1.20:8787
```

Run the unit tests with `./gradlew :app:testDebugUnitTest`.

## How it fits together

| Path under `app/src/main/kotlin/app/labellens/` | Contents |
| --- | --- |
| `data/Store.kt` | App state and every action that changes it. One instance for the whole process. |
| `data/Api.kt`, `data/Models.kt` | JSON client for the sample API and the types it returns. |
| `data/Matcher.kt` | Finds E-numbers and additive names in the text read from a label. |
| `data/Verdicts.kt` | Checks an additive against the user's allergies, diet and avoids. |
| `data/Navigation.kt` | The list of screens and a plain back stack. |
| `ui/theme/` | Colours, type and shapes from the design tokens. |
| `ui/Components.kt` | Shared pieces: buttons, segmented list rows, status pills, icons. |
| `ui/screens/` | One file per area: home, search, scan, results, detail, account, preferences, plans, history and saved, profile, settings. |

Label photos are read on the phone with ML Kit text recognition and are never sent anywhere. The additive list is saved on the phone after the first connection, so scanning and search work offline. Additive pages, history, saved additives and purchases need the API.

## What is a placeholder

- **Billing.** "Start free trial" calls the sample API. Google Play Billing is not wired in.
- **Google sign-in.** "Continue with Google" logs in the demo account.
- **Email.** No email is sent. The confirmation screen moves on by itself after a few seconds.
- **Some settings rows.** Change email, change password, notifications, download my data and adding a family profile show a "not available in this preview build" message.
- **Links out of the app** (help, terms, privacy, licence) point at placeholder pages in this repository.

## Fonts

Gabarito and Hanken Grotesk (SIL Open Font Licence) and a subset of Material Symbols Rounded (Apache 2.0) are bundled in `app/src/main/res/font/`. Icons are drawn from the Material Symbols font by name. To use an icon that isn't in the subset yet, add its name to [tools/generate_symbols.py](tools/generate_symbols.py) and run it. The script's header explains what it needs.
