#!/usr/bin/env python3
"""Checks every app/src/main/res/values-*/strings.xml against the English source.

For each language: every translatable key is present and nothing extra; placeholders (%1$s, %d)
match; plural items keep %d and include "other"; no string is left in English by mistake; and the
text is written in the language's expected script. Run: python3 scripts/check_strings.py
"""
import pathlib
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET

RES = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res"

# Expected script per language folder: a word in unicodedata.name() of the language's letters.
SCRIPTS = {
    "hi": "DEVANAGARI", "ta": "TAMIL", "as": "BENGALI", "bn": "BENGALI", "b+brx": "DEVANAGARI",
    "b+doi": "DEVANAGARI", "gu": "GUJARATI", "kn": "KANNADA", "ks": "ARABIC", "b+kok": "DEVANAGARI",
    "b+mai": "DEVANAGARI", "ml": "MALAYALAM", "b+mni": "MEETEI MAYEK", "mr": "DEVANAGARI",
    "ne": "DEVANAGARI", "or": "ORIYA", "pa": "GURMUKHI", "sa": "DEVANAGARI", "b+sat": "OL CHIKI",
    "sd": "ARABIC", "te": "TELUGU", "ur": "ARABIC",
    # Global languages (roadmap G1). Latin-script ones are recognised by their accented letters.
    "b+zh+Hans": "CJK", "es": "LATIN", "ar": "ARABIC", "fr": "LATIN", "pt": "LATIN",

}
PLACEHOLDER = re.compile(r"%(?:\d\$)?[sd]")


def load(folder):
    root = ET.parse(RES / folder / "strings.xml").getroot()
    out = {}
    for e in root:
        if e.tag == "string" and e.get("translatable") != "false":
            out[e.get("name")] = e.text or ""
        elif e.tag == "plurals":
            out[e.get("name")] = {i.get("quantity"): i.text or "" for i in e}
    return out


def main_script(text):
    counts = {}
    for ch in text:
        if ch.isalpha() and not ch.isascii():
            name = unicodedata.name(ch, "")
            for script in set(SCRIPTS.values()):
                if name.startswith(script):
                    counts[script] = counts.get(script, 0) + 1
    return max(counts, key=counts.get) if counts else None


def check(folder, en):
    lang = folder.removeprefix("values-")
    tr = load(folder)
    problems = [f"missing {k}" for k in en if k not in tr]
    problems += [f"extra {k}" for k in tr if k not in en]
    for key, source in en.items():
        if key not in tr:
            continue
        if isinstance(source, dict):
            items = tr[key]
            if "other" not in items:
                problems.append(f"{key}: no 'other' plural")
            problems += [f"{key}[{q}]: lacks %d" for q, s in items.items() if "%d" not in s]
            continue
        text = tr[key]
        if sorted(set(PLACEHOLDER.findall(text))) != sorted(set(PLACEHOLDER.findall(source))):
            problems.append(f"{key}: placeholders differ from English")
        if text.strip() == source.strip():
            problems.append(f"{key}: same as English")
    expected = SCRIPTS.get(lang)
    if expected is None:
        problems.append(f"no expected script listed for '{lang}' in scripts/check_strings.py")
    else:
        all_text = " ".join(v if isinstance(v, str) else " ".join(v.values()) for v in tr.values())
        found = main_script(all_text)
        if found != expected:
            problems.append(f"written mostly in {found}, expected {expected}")
    return problems


def main():
    en = load("values")
    folders = sorted(p.parent.name for p in RES.glob("values-*/strings.xml"))
    failed = False
    for folder in folders:
        problems = check(folder, en)
        print(f"{folder:14} {'OK' if not problems else 'FAIL'}")
        for p in problems:
            print(f"    {p}")
        failed |= bool(problems)
    print(f"{len(folders)} languages besides English")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
