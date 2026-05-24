# Finnish Word of the Day

Offline Android app that shows one Finnish word per day with an English definition.

Package name:

```text
com.valkotassu.finnishwordoftheday
```

## Features

- Daily deterministic word selection based on the device date.
- English definitions from a bundled offline asset.
- Optional Finnish example sentence when available.
- Favorites stored locally on the device.
- Native Android share sheet for the daily word.
- No account, network connection, ads, analytics, or tracking.

## Data

The app bundles a generated asset at `app/src/main/assets/words.json`.

The asset is derived from Finnish Wiktionary data extracted by Kaikki.org. The original source data is not stored in Git because it is several GB.

To regenerate the asset, place the Kaikki Finnish JSONL dump at:

```text
data/raw/kaikki.org-dictionary-Finnish.jsonl
```

Then run:

```sh
python3 scripts/build_word_asset.py
```

## Attribution

Dictionary content is derived from Wiktionary contributors via Kaikki.org/Wiktextract. See `NOTICE.md`.

## Release

Release signing and Google Play upload notes are in `docs/RELEASE.md`.

## Web App

The repository root contains a small Progressive Web App for GitHub Pages. It reuses the same bundled dictionary asset as the Android app.

GitHub Pages URL:

```text
https://roccobb.github.io/6-July/
```
