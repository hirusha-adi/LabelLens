import { useMemo, useState } from "react";
import { plural, segRadius } from "../lib/format";
import { allMatches, type Match } from "../lib/matcher";
import { goBack, navigate } from "../lib/router";
import { SHAPE_SUNNY } from "../lib/shapes";
import { addToList, checkOf, saveAll, useApp } from "../lib/store";
import { categoryLine, preferencesAreEmpty, type VerdictName } from "../lib/types";
import { allergensIn, generalSummary, RANK, worstOf, type Conflict } from "../lib/verdicts";
import { Bold, CheckMark, EmptyState, Icon, Link, StatusPill, VerdictMark, VERDICTS } from "../ui";

const Highlighted = ({ text, ranges }: { text: string; ranges: Array<[number, number]> }) => {
  const parts: React.ReactNode[] = [];
  let cursor = 0;
  ranges.forEach(([start, end], i) => {
    if (start > cursor) parts.push(text.slice(cursor, start));
    parts.push(<mark key={i}>{text.slice(start, end)}</mark>);
    cursor = end;
  });
  if (cursor < text.length) parts.push(text.slice(cursor));
  return <>{parts}</>;
};

export const ResultsPage = () => {
  const { scan, user } = useApp();
  const matches = useMemo(() => (scan ? allMatches(scan) : []), [scan]);
  const [selected, setSelected] = useState<string[]>(() => matches.map((m) => m.additive.code));

  if (!scan) {
    return (
      <EmptyState icon="document_scanner" shape={SHAPE_SUNNY} title="No scan to show" actions={<Link to="/app/scan" className="btn btn-primary btn-small">Scan a label</Link>}>
        Scan results aren't kept when the page is reloaded. Past scans are in History.
      </EmptyState>
    );
  }

  if (!matches.length) {
    return (
      <>
        <div className="page-head">
          <button className="icon-btn icon-btn-tonal" onClick={() => goBack()} aria-label="Back"><Icon name="arrow_back" /></button>
          <h1>Scan result</h1>
        </div>
        <EmptyState icon="document_scanner" shape={SHAPE_SUNNY} title="No additives found"
          actions={<><Link to="/app/scan" className="btn btn-outline btn-small">Scan again</Link><Link to="/app/search" className="btn btn-primary btn-small">Search manually</Link></>}>
          We read the label but didn't recognise any additive codes or names. That can mean there aren't any, or the photo was blurry. Try brighter light, and crop to just the ingredients list.
        </EmptyState>
      </>
    );
  }

  // Until allergies, a diet or things to avoid are set, the verdict is the general one.
  const personal = !preferencesAreEmpty(user.preferences);
  const checks = new Map(matches.map((m) => [m.additive.code, checkOf(m.additive)]));
  const allergens = allergensIn(scan.text, user.preferences);
  const conflicts: Conflict[] = [
    ...allergens,
    ...matches.flatMap((m): Conflict[] => {
      const check = checks.get(m.additive.code)!;
      return check.reason && check.verdict !== "safe" ? [{ title: `${m.additive.code} ${m.additive.name}`, reason: check.longReason ?? check.reason, verdict: check.verdict, icon: check.icon }] : [];
    }),
  ].sort((a, b) => RANK[b.verdict] - RANK[a.verdict]);
  const verdict: VerdictName = worstOf([...checks.values()].map((c) => c.verdict).concat(allergens.map((a) => a.verdict)));

  const toggle = (code: string) => setSelected((codes) => (codes.includes(code) ? codes.filter((c) => c !== code) : [...codes, code]));
  const groups: Array<[string, Match[]]> = [
    [scan.fromHistory ? "Additives in this scan" : "Matched by E-number", scan.codeMatches],
    ["Matched by name", scan.nameMatches],
  ];

  return (
    <>
      <div className="page-head">
        <button className="icon-btn icon-btn-tonal" onClick={() => goBack()} aria-label="Back"><Icon name="arrow_back" /></button>
        <h1>Scan result</h1>
        <button className="icon-btn" onClick={() => saveAll(selected)} aria-label="Save the selected additives"><Icon name="bookmark_add" /></button>
      </div>
      <div className="cols">
        <div className="verdict-card">
          <VerdictMark verdict={verdict} />
          <div className="verdict-kicker">{personal ? "Verdict for you" : "General verdict"}</div>
          <div className={`verdict-word text-${verdict}`}>{VERDICTS[verdict].label}</div>
          <div className="verdict-summary">
            {!personal
              ? generalSummary(matches.map((m) => m.additive))
              : conflicts.length
                ? `${plural(conflicts.length, "ingredient conflicts", "ingredients conflict")} with your profile`
                : "Nothing here conflicts with your profile"}
          </div>
          {personal && conflicts.length > 0 && (
            <div className="conflicts">
              {conflicts.map((conflict, i) => (
                <div key={conflict.title} className={`conflict tone-${conflict.verdict}`} style={{ borderRadius: conflicts.length === 1 ? 20 : i === 0 ? "20px 20px 6px 6px" : i === conflicts.length - 1 ? "6px 6px 20px 20px" : 6 }}>
                  <Icon name={conflict.icon} size={22} />
                  <span><Bold text={`**${conflict.title}**: ${conflict.reason}`} /></span>
                </div>
              ))}
            </div>
          )}
          {!personal && (
            <div className="setup-prompt">
              <Icon name="tune" />
              <div><b>Is it OK for you?</b>Add your allergies and diet to get a verdict for you.</div>
              <Link to="/app/preferences" className="btn btn-small">Set up</Link>
            </div>
          )}
        </div>

        <div className="results-col">
          <h2>{plural(matches.length, "possible additive")} found</h2>
          {groups.map(([title, rows]) => {
            if (!rows.length) return null;
            const codes = rows.map((m) => m.additive.code);
            const all = codes.every((code) => selected.includes(code));
            return (
              <div key={title}>
                <div className="group-head">
                  <span>{title}</span>
                  <button className="text-btn" onClick={() => setSelected(all ? selected.filter((c) => !codes.includes(c)) : [...new Set([...selected, ...codes])])}>{all ? "Clear" : "Select all"}</button>
                </div>
                <div className="rows">
                  {rows.map((match, i) => {
                    const on = selected.includes(match.additive.code);
                    return (
                      <div key={match.additive.code} className="row match-row row-in" style={{ borderRadius: segRadius(i, rows.length), ["--i" as string]: i }}>
                        <button className="icon-btn" onClick={() => toggle(match.additive.code)} role="checkbox" aria-checked={on} aria-label={`Select ${match.additive.code}`}><CheckMark checked={on} /></button>
                        <Link to={`/app/additive/${match.additive.code}`} className="row-main">
                          <span className="row-title" style={{ display: "block" }}>{match.additive.code} · {match.additive.name}</span>
                          <span className="row-sub" style={{ display: "block" }}>
                            {scan.fromHistory ? categoryLine(match.additive) : match.percent !== null ? `“${match.readAs}” · ${match.percent}% match` : `Read as “${match.readAs}”`}
                          </span>
                        </Link>
                        <StatusPill verdict={checks.get(match.additive.code)!.verdict} />
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}
          {scan.text && (
            <details className="extracted">
              <summary>Extracted text<Icon name="expand_more" className="muted when-closed" /><Icon name="expand_less" className="muted when-open" /></summary>
              <p><Highlighted text={scan.text} ranges={scan.highlights} /></p>
            </details>
          )}
          {scan.otherNumbers.length > 0 && <div className="small-note">Other numbers on the label: {scan.otherNumbers.join(", ")}. Not additive codes.</div>}
          <div className="action-bar">
            <Link to="/app/scan" className="round-outline" aria-label="Scan another label" title="Scan again"><Icon name="document_scanner" /></Link>
            <button className="btn btn-primary" disabled={!selected.length} onClick={() => { addToList(selected); navigate("/app"); }}>
              {selected.length ? `Add ${selected.length} to my list` : "Select additives to add"}
            </button>
          </div>
        </div>
      </div>
    </>
  );
};
