# Label Lens on the web

The landing page and the web app, built from the design in [../documents/claude-design-website.zip](../documents/claude-design-website.zip). Vite, React and TypeScript.

The web app does what the Android app does, in a browser: upload a photo of a label, crop it, have it read **on the device**, confirm the additives found, and read about each one. It gets additive information from the API in [../api/](../api/).

## Run

From the repository root, `scripts/dev.sh --web` starts the database, the API and the site. By hand, with the API already running on port 8787:

```sh
pnpm install
pnpm dev          # http://localhost:5173
```

```sh
pnpm test         # matcher and verdict logic
pnpm typecheck
pnpm build        # static site in dist/
```

In development the dev server forwards `/api` to the API, so no cross-origin setup is needed.

## Pages

| Address | What |
| --- | --- |
| `/` | Landing page |
| `/app` | Home: your list, and where a scan starts |
| `/app/scan` | Crop the photo and read it |
| `/app/results` | What a scan found, with the verdict |
| `/app/search?q=` | Search the additives |
| `/app/additive/E102` | One additive. Can be linked to and shared |
| `/app/history`, `/app/saved` | Past scans and bookmarked additives |
| `/app/profile`, `/app/preferences` | Appearance, backup, and allergies, diet and things to avoid |

## How it fits together

| Path under `src/` | Contents |
| --- | --- |
| `landing/Landing.tsx` | The landing page |
| `app/` | The app shell and one file per page |
| `ui.tsx` | Shared pieces: icons, the logo, status pills, verdict marks |
| `styles.css` | All styling. Colour, type and shape tokens are the same as the Android app's |
| `lib/store.ts` | App state and every action that changes it |
| `lib/api.ts` | The two requests the site makes: the list, and one additive's page |
| `lib/matcher.ts`, `lib/verdicts.ts` | Finding additives in label text, and checking them against the person's profile. Ports of the Android app's code, held in step by the same test cases |
| `lib/ocr.ts` | Reads the text in a photo with tesseract.js, in a worker in the browser |
| `lib/router.ts` | A small router on the History API |
| `lib/icons.ts`, `lib/shapes.ts` | Generated: the icon map, and the Expressive shape outlines |

## What stays in the browser, and what does not

- **Photos and label text never leave the browser.** The text-recognition engine and its English language data are served by this site itself (copied into `public/ocr/` at build time by `vite.config.ts`), not downloaded from a CDN.
- **Fonts are served by this site too**, so opening it contacts no font service. The site makes no request to any third party.
- **The list, saved additives, scan history and preferences** are one document in the browser's `localStorage`. It has the same format as the Android app's backup, so "Back up to a file" on either restores on the other.
- **Looking up additives contacts the API**, which records the IP address, the kind of device and which additive was asked for. The Profile page and the landing page say so.

Browser storage is less durable than the phone app's: clearing site data removes it. The Profile page's backup file is the way to keep a copy.

## Fonts and icons

`public/fonts/` holds subsets of Gabarito, Hanken Grotesk, JetBrains Mono and Material Symbols Rounded. Icons are drawn by codepoint from `lib/icons.ts`. To use an icon that is not in the subset, add its name in [scripts/build-fonts.py](scripts/build-fonts.py) and run it; its header says what it needs.

## Deploying

`pnpm build` writes a static site to `dist/`. Three things the host must do:

1. **Serve `index.html` for any path under `/app`** (single-page app fallback). With Caddy: `try_files {path} /index.html`.
2. **Point the site at the API.** Build with `VITE_API_URL=https://api.example.com`, and start the API with `ALLOWED_ORIGINS=https://www.example.com` so browsers may read it. Or serve the API under `/api` on the same address and skip both.
3. **Serve `/ocr/*.wasm.js` and the fonts with long cache lifetimes.** The engine is about 4 MB and the language data 3 MB, downloaded once on a visitor's first scan.

`VITE_PLAY_URL` sets where "Get it on Google Play" goes.

## Before this goes public

The landing page follows the design's wording, and some of it is not true yet:

- **"Open source. The full code is public."** The repository is private. Make it public or remove that block.
- **"$9.99 one-time"** is the design's placeholder price.
- **"Get it on Google Play"** links to the Play Store front page until the app is listed.
- **Help, Licence, Legal and Contributing** point at files in the repository that do not exist yet.
- **No privacy policy or terms.**

Where the design said "no tracking, nothing uploaded", the page now says what is actually recorded.
