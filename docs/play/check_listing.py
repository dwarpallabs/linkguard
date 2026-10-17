#!/usr/bin/env python3
"""Checks docs/play/listing-*.md against Google Play's field limits. Run: python3 docs/play/check_listing.py"""
import pathlib
import re
import sys

LIMITS = {"Title": 30, "Short description": 80, "Full description": 4000, "Release notes": 500}
here = pathlib.Path(__file__).parent
ok = True
for path in sorted(here.glob("listing-*.md")):
    text = re.sub(r"<!--.*?-->", "", path.read_text(encoding="utf-8"), flags=re.S)
    sections = dict(re.findall(r"^## (.+?)\n(.*?)(?=^## |\Z)", text, flags=re.S | re.M))
    for field, limit in LIMITS.items():
        value = sections.get(field, "").strip()
        n = len(value)  # Play counts characters (Unicode code points)
        status = "ok" if 0 < n <= limit else "FAIL"
        ok &= status == "ok"
        print(f"{path.name:16} {field:18} {n:5} / {limit:<5} {status}")
sys.exit(0 if ok else 1)
