# Dictionary Data

This folder is for local dictionary processing.

- `raw/`: original downloads from kaikki.org. These files are intentionally ignored by Git.
- `processed/`: optional intermediate outputs. Also ignored by Git.

The Android app should use the smaller generated asset in `app/src/main/assets/words.json`.

To regenerate it:

```sh
python3 scripts/build_word_asset.py
```
