#!/usr/bin/env python3
"""Apply authored, field-sourced descriptions to the primary aggregate first.

The catalog contains prose, not instructions to infer missing facts. Existing
intake, safety and approval statements survive unless explicitly replaced.
Individual files are synchronized separately by full-split.py.
"""
import argparse
import json
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "development/research/description-enrichment.json"


def write_json(path, value, indent=2):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=indent) + "\n", encoding="utf-8")


def article_label(link):
    host = urlparse(link).netloc
    if host.endswith("wikipedia.org"):
        return "Wikipedia"
    if host == "pubchem.ncbi.nlm.nih.gov":
        return "PubChem"
    if host == "efsa.onlinelibrary.wiley.com":
        return "EFSA Journal (Wiley)"
    if host == "eur-lex.europa.eu":
        return "EUR-Lex"
    if host.endswith("ecfr.gov"):
        return "eCFR"
    if host.endswith("fao.org"):
        return "FAO"
    if host.endswith("who.int"):
        return "WHO JECFA"
    if host.endswith("hc-sc.gc.ca"):
        return "Health Canada"
    if host.endswith("fda.gov"):
        return "FDA"
    return host


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-all", action="store_true")
    args = parser.parse_args()
    catalog = json.loads(CATALOG.read_text())
    rows = json.loads((ROOT / "dist/ins-full.json").read_text())
    patches = catalog["records"]
    codes = {row["code"] for row in rows}
    if set(patches) - codes:
        raise ValueError("Description catalog includes unknown codes")
    if args.require_all and set(patches) != codes:
        raise ValueError(f"Missing description records: {sorted(codes - set(patches))}")
    if args.require_all and any("overview" not in patch["fields"] for patch in patches.values()):
        raise ValueError("Every description record must contain an authored overview")
    receipts = json.loads((ROOT / "development/url-checks.json").read_text())["checks"]
    changes = {}
    for row in rows:
        code = row["code"]
        if code not in patches:
            continue
        patch = patches[code]
        for field, value in patch.get("record_fields", {}).items():
            if field not in {"names", "display_name", "type", "status", "icon"}:
                raise ValueError(f"Unsupported record correction: {code}.{field}")
            urls = patch.get("record_sources", {}).get(field, [])
            if not urls or any(not receipts.get(url, {}).get("loaded") for url in urls):
                raise ValueError(f"Missing source for record correction: {code}.{field}")
            if row[field] != value:
                changes.setdefault(code, []).append(field)
            row[field] = value
        for field, value in patch["fields"].items():
            if not isinstance(value, str) or not value.strip() or "\u2014" in value:
                raise ValueError(f"Invalid description: {code}.{field}")
            urls = patch["sources"].get(field, [])
            if not urls or any(not receipts.get(url, {}).get("loaded") for url in urls):
                raise ValueError(f"Missing readable source receipt: {code}.{field}")
            if row["more_info"].get(field) != value:
                changes.setdefault(code, []).append(field)
            row["more_info"][field] = value
        articles = row["more_info"]["articles"]
        # Choose the same candidates on every run so reapplying the catalog
        # does not keep appending articles to otherwise unchanged records.
        candidates = list(dict.fromkeys(
            link for links in patch["sources"].values() for link in links
        ))[:3]
        for link in candidates:
            if link in articles.values():
                continue
            label = article_label(link)
            candidate = label
            suffix = 2
            while candidate in articles:
                candidate = f"{label} ({suffix})"
                suffix += 1
            articles[candidate] = link
    # The user's requested primary destination is written before derivatives.
    write_json(ROOT / "dist/ins-full.json", rows)
    for code, patch in patches.items():
        path = ROOT / f"development/sources/{code}.json"
        provenance = json.loads(path.read_text())
        provenance["checked"] = catalog["checked"]
        for field in patch.get("record_fields", {}):
            provenance["fields"][field] = patch["record_sources"][field]
            provenance.get("inputs", {}).pop(field, None)
        for field in patch["fields"]:
            provenance["fields"][field] = patch["sources"][field]
            provenance.get("inputs", {}).pop(field, None)
            if field in patch.get("inputs", {}):
                provenance.setdefault("inputs", {})[field] = patch["inputs"][field]
            provenance["gaps"] = [g for g in provenance["gaps"]
                                  if not (field in g and ("omitted" in g or "No source" in g))]
            if field == "overview":
                provenance["gaps"] = [g for g in provenance["gaps"]
                                      if "overview omitted" not in g]
            if field == "uses":
                provenance["gaps"] = [g for g in provenance["gaps"]
                                      if "uses omitted" not in g]
        for gap in patch.get("gaps", []):
            if gap not in provenance["gaps"]:
                provenance["gaps"].append(gap)
        for resolved in patch.get("resolved_gaps", []):
            provenance["gaps"] = [g for g in provenance["gaps"] if resolved not in g]
        for field in changes.get(code, []):
            note = patch.get("change_notes", {}).get(field, f"2026-10-10 description expansion: {field} replaced or added with compound-specific detail from the linked field sources.")
            if note not in provenance["changes"]:
                provenance["changes"].append(note)
        write_json(path, provenance)
    master_path = ROOT / "development/ins-summary-original.json"
    master = json.loads(master_path.read_text())
    indexed = {row["code"]: row for row in rows}
    for item in master:
        for field in patches.get(item["code"], {}).get("record_fields", {}):
            if field in {"names", "type", "status"}:
                item[field] = indexed[item["code"]][field]
    write_json(master_path, master)
    print(f"Applied descriptions for {len(patches)} codes to dist/ins-full.json; {len(changes)} records changed.")


if __name__ == "__main__":
    main()
