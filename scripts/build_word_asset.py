#!/usr/bin/env python3
"""Build a small Android word-of-the-day asset from a Kaikki Finnish JSONL dump."""

from __future__ import annotations

import argparse
import json
import random
import re
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT = ROOT / "data/raw/kaikki.org-dictionary-Finnish.jsonl"
DEFAULT_OUTPUT = ROOT / "app/src/main/assets/words.json"

ALLOWED_POS = {"noun", "verb", "adj", "adv"}
BAD_SENSE_TAGS = {
    "abbreviation",
    "alt-of",
    "archaic",
    "dated",
    "form-of",
    "letter",
    "misspelling",
    "morpheme",
    "nonstandard",
    "obsolete",
    "proscribed",
    "rare",
    "symbol",
}
BAD_WORD_RE = re.compile(r"[^A-Za-zÅÄÖåäö]")
WHITESPACE_RE = re.compile(r"\s+")
MAX_EXAMPLE_LENGTH = 180


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create app/src/main/assets/words.json from a Kaikki Finnish JSONL file."
    )
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--max-entries",
        type=int,
        default=5000,
        help="Maximum number of words to keep. Use 0 to keep every matching entry.",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=20260524,
        help="Seed used for deterministic sampling when --max-entries is set.",
    )
    parser.add_argument(
        "--allow-compounds-with-hyphen",
        action="store_true",
        help="Keep words containing hyphens. Disabled by default for simpler daily words.",
    )
    return parser.parse_args()


def clean_gloss(gloss: str) -> str:
    gloss = WHITESPACE_RE.sub(" ", gloss).strip()
    gloss = gloss.removeprefix("Synonym of ").strip()
    return gloss


def clean_example_text(text: str) -> str:
    return WHITESPACE_RE.sub(" ", text).strip()


def is_simple_word(word: str, allow_hyphen: bool) -> bool:
    if not word or len(word) < 2:
        return False
    if " " in word or "_" in word or "." in word or "/" in word:
        return False
    if "-" in word and not allow_hyphen:
        return False
    check_word = word.replace("-", "") if allow_hyphen else word
    return BAD_WORD_RE.search(check_word) is None


def usable_sense_glosses(senses: list[dict[str, Any]]) -> list[str]:
    glosses: list[str] = []
    seen: set[str] = set()

    for sense in senses:
        tags = set(sense.get("tags") or [])
        if tags & BAD_SENSE_TAGS:
            continue
        if sense.get("form_of") or sense.get("alt_of"):
            continue

        for raw_gloss in sense.get("glosses") or []:
            gloss = clean_gloss(raw_gloss)
            if not gloss:
                continue
            lowered = gloss.lower()
            if " of " in lowered and lowered.startswith(
                (
                    "abbreviation",
                    "alternative form",
                    "comparative form",
                    "conjugation",
                    "genitive",
                    "inflection",
                    "nominative plural",
                    "participle",
                    "plural",
                    "superlative form",
                )
            ):
                continue
            if gloss not in seen:
                seen.add(gloss)
                glosses.append(gloss)
            if len(glosses) >= 2:
                return glosses

    return glosses


def entry_to_word(entry: dict[str, Any], allow_hyphen: bool) -> dict[str, Any] | None:
    if entry.get("lang_code") != "fi":
        return None

    word = entry.get("word", "")
    pos = entry.get("pos", "")
    if pos not in ALLOWED_POS:
        return None
    if not is_simple_word(word, allow_hyphen):
        return None

    glosses = usable_sense_glosses(entry.get("senses") or [])
    if not glosses:
        return None

    item: dict[str, Any] = {
        "word": word,
        "partOfSpeech": pos,
        "definitions": glosses,
    }

    ipa = first_ipa(entry.get("sounds") or [])
    if ipa:
        item["ipa"] = ipa

    example = first_example(entry.get("senses") or [])
    if example:
        item["example"] = example

    return item


def first_example(senses: list[dict[str, Any]]) -> dict[str, str] | None:
    for sense in senses:
        tags = set(sense.get("tags") or [])
        if tags & BAD_SENSE_TAGS:
            continue
        if sense.get("form_of") or sense.get("alt_of"):
            continue

        for example in sense.get("examples") or []:
            text = clean_example_text(example.get("text") or "")
            translation = clean_example_text(
                example.get("translation") or example.get("english") or ""
            )
            if not text or not translation:
                continue
            if len(text) > MAX_EXAMPLE_LENGTH or len(translation) > MAX_EXAMPLE_LENGTH:
                continue
            return {
                "text": text,
                "translation": translation,
            }

    return None


def first_ipa(sounds: list[dict[str, Any]]) -> str | None:
    for sound in sounds:
        ipa = sound.get("ipa")
        if isinstance(ipa, str) and ipa:
            return ipa
    return None


def reservoir_add(
    reservoir: list[dict[str, Any]],
    item: dict[str, Any],
    seen_count: int,
    max_entries: int,
    rng: random.Random,
) -> None:
    if max_entries <= 0:
        reservoir.append(item)
        return
    if len(reservoir) < max_entries:
        reservoir.append(item)
        return
    index = rng.randrange(seen_count)
    if index < max_entries:
        reservoir[index] = item


def main() -> int:
    args = parse_args()
    rng = random.Random(args.seed)

    if not args.input.exists():
        print(f"Input file not found: {args.input}")
        return 1

    words: list[dict[str, Any]] = []
    matching_entries = 0
    read_entries = 0
    skipped_invalid_json = 0

    with args.input.open("r", encoding="utf-8") as source:
        for line in source:
            read_entries += 1
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                skipped_invalid_json += 1
                continue

            item = entry_to_word(entry, args.allow_compounds_with_hyphen)
            if item is None:
                continue

            matching_entries += 1
            reservoir_add(words, item, matching_entries, args.max_entries, rng)

    words.sort(key=lambda item: item["word"].casefold())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8") as output:
        json.dump(words, output, ensure_ascii=False, indent=2)
        output.write("\n")

    print(f"Read entries: {read_entries}")
    print(f"Matching lemma-like entries: {matching_entries}")
    print(f"Written entries: {len(words)}")
    print(f"Skipped invalid JSON lines: {skipped_invalid_json}")
    print(f"Output: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
