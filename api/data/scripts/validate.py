#!/usr/bin/env python3
"""Validate the entire dataset, or explicitly selected codes during migration.

Run from any directory. --codes never certifies the whole dataset.
The explicitly permitted empty status is the only empty-string exception.
"""
import argparse
import datetime
import json
from pathlib import Path
import re
import sys
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = {"code", "names", "display_name", "type", "status", "icon", "more_info"}
INFO_KEYS = {"banned_in", "overview", "origin", "daily_intake", "side_effects",
             "uses", "dietary_restrictions", "interactions", "precautions", "articles"}
STATUSES = {"e u", "e", "u", ""}


def read_json(path, errors):
    def unique_keys(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"duplicate key: {key}")
            result[key] = value
        return result
    def reject_constant(value):
        raise ValueError(f"nonstandard JSON constant: {value}")
    try:
        return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_keys,
                          parse_constant=reject_constant)
    except (OSError, ValueError) as exc:
        errors.append(f"{path.relative_to(ROOT)}: {exc}")
        return None


def url(value):
    return isinstance(value, str) and urlparse(value).scheme in {"http", "https"} and bool(urlparse(value).netloc)


def empty_strings(value, path, errors, allowed_empty_path=None):
    if isinstance(value, dict):
        for key, child in value.items():
            empty_strings(child, f"{path}.{key}", errors, allowed_empty_path)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            empty_strings(child, f"{path}[{index}]", errors, allowed_empty_path)
    elif isinstance(value, str):
        if not value.strip() and path != allowed_empty_path:
            errors.append(f"{path}: empty string")
        if "\u2014" in value:
            errors.append(f"{path}: em dash is not allowed")


def regulatory_lines(text, status, prefix, errors):
    if not isinstance(text, str):
        errors.append(f"{prefix}.banned_in: must be plain text")
        return
    phase = 0
    seen = set()
    ban_seen = False
    for line in text.split("\n"):
        if re.fullmatch(r"Banned in: [^,.\n]+(?:, [^,.\n]+)*\.", line):
            order = 0
            if ban_seen:
                errors.append(f"{prefix}.banned_in: only one ban-list line is allowed")
            ban_seen = True
        elif line == "Approved in the EU.":
            order = 1
        elif line == "Approved in the US.":
            order = 2
        elif (re.search(r"\b(?:19|20)\d{2}\b", line) and line.endswith(".")
              and not re.search(r"\b(?:Banned|Approved)\b", line)):
            order = 3
        else:
            errors.append(f"{prefix}.banned_in: invalid line {line!r}")
            continue
        if order < phase or line in seen:
            errors.append(f"{prefix}.banned_in: repeated line or wrong order")
        phase = order
        seen.add(line)
    for flag, sentence in (("e", "Approved in the EU."), ("u", "Approved in the US.")):
        if (flag in status.split()) != (sentence in seen):
            errors.append(f"{prefix}.banned_in: approval lines disagree with status")


def validate_additive(code, errors):
    path = ROOT / "dist/single" / f"{code}.json"
    obj = read_json(path, errors)
    validate_record(code, obj, errors)


def validate_record(code, obj, errors):
    if not isinstance(obj, dict):
        errors.append(f"{code}: additive must be an object")
        return
    missing = REQUIRED - obj.keys()
    if missing:
        errors.append(f"{code}: missing keys {', '.join(sorted(missing))}")
    if obj.keys() - REQUIRED:
        errors.append(f"{code}: unexpected top-level keys {', '.join(sorted(obj.keys() - REQUIRED))}")
    empty_strings(obj, code, errors, f"{code}.status")
    if obj.get("code") != code or not re.fullmatch(r"E\d+[a-z]*", code):
        errors.append(f"{code}: code must start with E and match filename")
    for field in REQUIRED - {"more_info"}:
        if not isinstance(obj.get(field), str):
            errors.append(f"{code}.{field}: must be a string")
    status = obj.get("status")
    if not isinstance(status, str) or status not in STATUSES:
        errors.append(f"{code}: invalid status {status!r}")
    display = obj.get("display_name", "")
    if not isinstance(display, str) or not re.fullmatch(re.escape(code) + r" \([^\n]+\)", display):
        errors.append(f"{code}: invalid display_name")
    if not isinstance(obj.get("icon"), str) or obj["icon"] not in {"mdi-check-circle", "mdi-alert-circle"}:
        errors.append(f"{code}: invalid icon")
    info = obj.get("more_info")
    if not isinstance(info, dict):
        errors.append(f"{code}: more_info must be an object")
        return
    if info.keys() - INFO_KEYS:
        errors.append(f"{code}: unexpected more_info keys {', '.join(sorted(info.keys() - INFO_KEYS))}")
    for key, value in info.items():
        if key != "articles" and not isinstance(value, str):
            errors.append(f"{code}.{key}: must be plain text")
        elif key != "articles" and re.search(r"<[^>]+>|\[\d+\]|\[[^\]]+\]\(", value):
            errors.append(f"{code}.{key}: HTML, markdown or citation marker")
    if isinstance(info.get("overview"), str):
        sentences = re.split(r"(?<=[.!?])\s+(?=[A-Z])", info["overview"].strip())
        if not 2 <= len(sentences) <= 6:
            errors.append(f"{code}.overview: must contain 2 to 6 sentences")
    if "banned_in" in info and isinstance(status, str):
        regulatory_lines(info["banned_in"], status, code, errors)
    elif status:
        errors.append(f"{code}: missing approval lines for nonempty status")
    articles = info.get("articles")
    if not isinstance(articles, dict) or len(articles) < 2:
        errors.append(f"{code}: articles must contain at least two links")
    elif any(not url(value) for value in articles.values()):
        errors.append(f"{code}: invalid article URL")
    source = read_json(ROOT / "development/sources" / f"{code}.json", errors)
    if not isinstance(source, dict):
        return
    if source.get("code") != code:
        errors.append(f"{code}: source code mismatch")
    try:
        datetime.date.fromisoformat(source.get("checked", ""))
    except (TypeError, ValueError):
        errors.append(f"{code}: source checked must be an ISO date")
    fields = source.get("fields")
    if not isinstance(fields, dict):
        errors.append(f"{code}: source fields must be an object")
    else:
        # Require traceability for descriptions too, not only health/legal text.
        for field in {"status", "icon", "names", "type", "display_name"} | (info.keys() - {"articles"}):
            links = fields.get(field)
            # Names and existing categories are explicitly supplied by the master.
            # Keep their local provenance instead of attributing them to a website.
            supplied = source.get("inputs", {}).get(field)
            if field in {"names", "type", "display_name"} and supplied == "development/ins-summary-original.json":
                continue
            if not isinstance(links, list) or not links or any(not url(link) for link in links):
                errors.append(f"{code}: missing or invalid sources for {field}")
    for field in ("gaps", "changes"):
        value = source.get(field)
        if not isinstance(value, list) or any(not isinstance(item, str) or not item.strip() for item in value):
            errors.append(f"{code}: source {field} must be a list of nonempty strings")
    empty_strings(source, f"{code}.sources", errors)


def validate_aggregate(selected, errors):
    records = read_json(ROOT / "dist/ins-full.json", errors)
    if not isinstance(records, list):
        errors.append("dist/ins-full.json: must be an array")
        return
    indexed = {}
    for item in records:
        if not isinstance(item, dict) or not isinstance(item.get("code"), str):
            errors.append("dist/ins-full.json: each entry must have a string code")
            continue
        code = item["code"]
        if code in indexed:
            errors.append(f"dist/ins-full.json: duplicate code {code}")
        indexed[code] = item
    for code in sorted(selected):
        if code not in indexed:
            errors.append(f"dist/ins-full.json: missing {code}")
            continue
        path = ROOT / "dist/single" / f"{code}.json"
        if path.is_file():
            single = read_json(path, errors)
            if indexed[code] != single:
                errors.append(f"dist/ins-full.json: {code} differs from its individual file")


def validate_loaded_urls(selected, errors):
    ledger = read_json(ROOT / "development/url-checks.json", errors)
    checks = ledger.get("checks", {}) if isinstance(ledger, dict) else {}
    for code in sorted(selected):
        record = read_json(ROOT / "dist/single" / f"{code}.json", errors)
        source = read_json(ROOT / "development/sources" / f"{code}.json", errors)
        used = set()
        if isinstance(record, dict):
            info = record.get("more_info", {})
            articles = info.get("articles", {}) if isinstance(info, dict) else {}
            if isinstance(articles, dict):
                used.update(value for value in articles.values() if isinstance(value, str))
        if isinstance(source, dict) and isinstance(source.get("fields"), dict):
            for links in source["fields"].values():
                if isinstance(links, list):
                    used.update(link for link in links if isinstance(link, str))
        for link in sorted(used):
            receipt = checks.get(link, {})
            if not isinstance(receipt, dict) or receipt.get("loaded") is not True:
                errors.append(f"{code}: URL has no successful load receipt: {link}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--codes", nargs="+", help="validate only this migration batch; not a full dataset check")
    args = parser.parse_args()
    errors = []
    master = read_json(ROOT / "development/ins-summary-original.json", errors)
    master_codes = set()
    if not isinstance(master, list):
        errors.append("master list must be an array")
    else:
        for item in master:
            if not isinstance(item, dict) or not isinstance(item.get("code"), str):
                errors.append("master entry must have a string code")
                continue
            code = item["code"]
            if code in master_codes:
                errors.append(f"{code}: duplicate master code")
            master_codes.add(code)
    file_codes = {p.stem for p in (ROOT / "dist/single").glob("*.json")}
    selected = set(args.codes) if args.codes else master_codes | file_codes
    for code in sorted(selected):
        if not re.fullmatch(r"E\d+[a-z]*", code):
            errors.append(f"{code!r}: invalid selected code")
            continue
        validate_additive(code, errors)
    validate_aggregate(selected, errors)
    validate_loaded_urls(selected, errors)
    for error in errors:
        print(error, file=sys.stderr)
    scope = "BATCH ONLY" if args.codes else "FULL DATASET"
    print(f"{scope}: {len(selected)} codes checked; {len(errors)} errors.")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
