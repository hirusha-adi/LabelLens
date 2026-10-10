#!/usr/bin/env python3
"""Apply sourced country bans and scoped restrictions to the aggregate first."""
import datetime
import json
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "development/research/regulatory-details.json"


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def regulatory_text(record, checked, receipts):
    """Keep country-wide prohibitions separate from narrow or future restrictions."""
    status = record["status"]
    if status not in {"", "e", "u", "e u"}:
        raise ValueError("Invalid approval flags")
    if not record["sources"]:
        raise ValueError("Missing regulatory sources")
    evidence = list(record["sources"])
    countries = []
    for ban in record["country_bans"]:
        if not ban.get("country") or not ban.get("scope") or not ban.get("sources"):
            raise ValueError("Country ban requires a country, use scope and sources")
        if ban.get("effective") and datetime.date.fromisoformat(ban["effective"]) > checked:
            raise ValueError("A future prohibition cannot enter the current country-ban list")
        if ban["country"] == "United States" and "u" in status.split():
            raise ValueError("US-wide food ban contradicts a current US approval flag")
        evidence.extend(ban["sources"])
        countries.append(ban["country"])
    for restriction in record["restrictions"]:
        if not restriction.get("detail") or not restriction.get("sources"):
            raise ValueError("Scoped restriction requires detail and sources")
        evidence.extend(restriction["sources"])
        if restriction["kind"] in {"future prohibition", "enjoined future provision"}:
            effective = datetime.date.fromisoformat(restriction["effective"])
            if effective <= checked:
                raise ValueError("Expired future prohibition requires a new regulatory assessment")
    if any(receipts.get(link, {}).get("loaded") is not True for link in evidence):
        raise ValueError("Regulatory evidence has an unreadable or unchecked URL")
    lines = []
    if countries:
        lines.append("Banned in: " + ", ".join(dict.fromkeys(countries)) + ".")
    for flag, line in (("e", "Approved in the EU."), ("u", "Approved in the US.")):
        if flag in status.split():
            lines.append(line)
    lines.extend(dict.fromkeys(record["notes"]))
    if not lines or any(not line.strip() or "\u2014" in line for line in lines):
        raise ValueError("Invalid plain regulatory text")
    return "\n".join(lines), list(dict.fromkeys(evidence))


def add_article(articles, link):
    if link in articles.values():
        return
    host = urlparse(link).netloc
    label = {
        "eur-lex.europa.eu": "EUR-Lex",
        "european-union.europa.eu": "European Union",
        "www.ecfr.gov": "eCFR",
        "www.canada.ca": "Health Canada",
        "www.blv.admin.ch": "Swiss Food Safety (BLV)",
        "www.fssai.gov.in": "FSSAI",
        "www.gov.uk": "GOV.UK",
        "www.legislation.gov.uk": "UK legislation",
        "code.wvlegislature.gov": "West Virginia Legislature",
        "ahea.assembly.ca.gov": "California Assembly",
        "cases.justia.com": "Justia (US court order)",
    }.get(host, host)
    candidate = label
    suffix = 2
    while candidate in articles:
        candidate = f"{label} ({suffix})"
        suffix += 1
    articles[candidate] = link


def main():
    catalog = json.loads(CATALOG.read_text())
    checked = datetime.date.fromisoformat(catalog["checked"])
    rows = json.loads((ROOT / "dist/ins-full.json").read_text())
    records = catalog["records"]
    codes = {row["code"] for row in rows}
    if codes != set(records):
        raise ValueError("Regulatory catalog must cover exactly the complete aggregate")
    receipts = json.loads((ROOT / "development/url-checks.json").read_text())["checks"]
    prepared = {}
    for row in rows:
        code = row["code"]
        record = records[code]
        if record["status"] != row["status"]:
            raise ValueError(f"{code}: catalog approval flags differ from primary record")
        if "e" in row["status"].split() and any(
            ban["country"] in catalog["eu_members"] for ban in record["country_bans"]
        ):
            raise ValueError(f"{code}: EU-wide food ban contradicts EU approval")
        prepared[code] = regulatory_text(record, checked, receipts)
        row["more_info"]["banned_in"] = prepared[code][0]
        record["banned_in"] = prepared[code][0]
        if record["country_bans"] or any(
            item["kind"] not in {"unconfirmed permission", "classification change"}
            for item in record["restrictions"]
        ):
            row["icon"] = "mdi-alert-circle"
        explicit_sources = list(dict.fromkeys(
            link for item in record["country_bans"] + record["restrictions"]
            for link in item["sources"]
        ))
        for link in explicit_sources:
            if link not in catalog["eu_member_sources"]:
                add_article(row["more_info"]["articles"], link)
    # The requested primary destination precedes source records and derivatives.
    write_json(ROOT / "dist/ins-full.json", rows)
    for row in rows:
        code = row["code"]
        record = records[code]
        path = ROOT / f"development/sources/{code}.json"
        source = json.loads(path.read_text())
        source["checked"] = catalog["checked"]
        source["fields"]["banned_in"] = prepared[code][1]
        if record["country_bans"] or record["restrictions"]:
            source["fields"]["icon"] = list(dict.fromkeys(
                source["fields"]["icon"] + [
                    link for item in record["country_bans"] + record["restrictions"]
                    for link in item["sources"]
                ]
            ))
        source["gaps"] = [gap for gap in source["gaps"]
                          if not ("banned_in" in gap and "omitted" in gap)]
        for resolved in record.get("resolved_gaps", []):
            source["gaps"] = [gap for gap in source["gaps"] if resolved not in gap]
        for gap in record["gaps"]:
            if gap not in source["gaps"]:
                source["gaps"].append(gap)
        change = ("2026-10-10 regulatory expansion: banned_in records confirmed country bans, "
                  "approval lines, scoped restrictions and limits of the checked jurisdictions. "
                  "Country and use-specific evidence is retained in regulatory-details.json.")
        if change not in source["changes"]:
            source["changes"].append(change)
        for note in record.get("changes", []):
            if note not in source["changes"]:
                source["changes"].append(note)
        write_json(path, source)
    write_json(CATALOG, catalog)
    # Keep the earlier catalog from restoring stale regulatory fields or icons.
    descriptions_path = ROOT / "development/research/description-enrichment.json"
    descriptions = json.loads(descriptions_path.read_text())
    for row in rows:
        code = row["code"]
        patch = descriptions["records"][code]
        patch["fields"]["banned_in"] = row["more_info"]["banned_in"]
        patch["sources"]["banned_in"] = prepared[code][1]
        for resolved in record.get("resolved_gaps", []):
            if resolved not in patch.setdefault("resolved_gaps", []):
                patch["resolved_gaps"].append(resolved)
        if "icon" in patch.get("record_fields", {}):
            patch["record_fields"]["icon"] = row["icon"]
            patch["record_sources"]["icon"] = json.loads(
                (ROOT / f"development/sources/{code}.json").read_text()
            )["fields"]["icon"]
    write_json(descriptions_path, descriptions)
    banned = sum(bool(record["country_bans"]) for record in records.values())
    scoped = sum(any(item["kind"] != "unconfirmed permission"
                     for item in record["restrictions"]) for record in records.values())
    print(f"Applied regulatory details for {len(rows)} codes; "
          f"{banned} records have confirmed country bans; "
          f"{scoped} have scoped, future or enjoined restrictions.")


if __name__ == "__main__":
    main()
