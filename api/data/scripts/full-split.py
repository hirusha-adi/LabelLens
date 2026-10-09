#!/usr/bin/env python3
"""Sync validated records from primary dist/ins-full.json to individual files.

Run from any directory. --codes limits synchronization to a research batch.
Existing files outside the selected records are never removed.
"""
import argparse
import json
import sys
import validate


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--codes", nargs="+")
    args = parser.parse_args()
    errors = []
    records = validate.read_json(validate.ROOT / "dist/ins-full.json", errors)
    if not isinstance(records, list):
        errors.append("dist/ins-full.json must be an array")
        records = []
    indexed = {}
    for item in records:
        if not isinstance(item, dict) or not isinstance(item.get("code"), str):
            errors.append("Every aggregate entry must have a string code")
            continue
        code = item["code"]
        if code in indexed:
            errors.append(f"Duplicate aggregate code: {code}")
        indexed[code] = item
    selected = set(args.codes) if args.codes else set(indexed)
    for code in sorted(selected):
        if code not in indexed:
            errors.append(f"Missing aggregate code: {code}")
        else:
            validate.validate_record(code, indexed[code], errors)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    output = validate.ROOT / "dist/single"
    output.mkdir(parents=True, exist_ok=True)
    for code in sorted(selected):
        (output / f"{code}.json").write_text(json.dumps(indexed[code], ensure_ascii=False, indent=4) + "\n", encoding="utf-8")
    print(f"Synced {len(selected)} records from dist/ins-full.json.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
