import { useState } from "react";
import { dayLabel, plural, segRadius, timeLabel } from "../lib/format";
import { resultFromCodes } from "../lib/matcher";
import { navigate } from "../lib/router";
import { SHAPE_COOKIE, SHAPE_SUNNY } from "../lib/shapes";
import { checkOf, getState, showResult, toast, toggleSaved, useApp } from "../lib/store";
import { categoryLine, type Scan, type VerdictName } from "../lib/types";
import { EmptyState, Icon, Link, StatusPill, StatusTile } from "../ui";

const FILTERS: Array<["all" | VerdictName, string]> = [["all", "All"], ["avoid", "Avoid"], ["caution", "Caution"], ["safe", "Safe"]];

const open = (scan: Scan) => {
  if (!scan.codes.length) return toast("No additives were found in this scan.");
  showResult(resultFromCodes(scan.codes, getState().additives));
  navigate("/app/results");
};

export const HistoryPage = () => {
  const { user } = useApp();
  const [filter, setFilter] = useState<"all" | VerdictName>("all");
  const shown = user.scans.filter((scan) => filter === "all" || scan.verdict === filter);
  const days = new Map<string, Scan[]>();
  for (const scan of shown) days.set(dayLabel(scan.at), [...(days.get(dayLabel(scan.at)) ?? []), scan]);

  return (
    <>
      <h1 className="page-title">History</h1>
      {!user.scans.length ? (
        <EmptyState icon="history" shape={SHAPE_SUNNY} title="No scans yet" actions={<Link to="/app/scan" className="btn btn-primary btn-small"><Icon name="document_scanner" size={20} />Scan a label</Link>}>
          Every label you scan is kept here with its verdict, so you can check a product again without rescanning. Only the additives found are kept, never the photo.
        </EmptyState>
      ) : (
        <div className="narrow-col">
          <div className="chips" role="radiogroup" aria-label="Filter by verdict">
            {FILTERS.map(([id, label]) => (
              <button key={id} className={`filter-chip ${filter === id ? "on" : ""}`} onClick={() => setFilter(id)} role="radio" aria-checked={filter === id}>
                {filter === id && <Icon name="check" size={18} />}
                {label}
              </button>
            ))}
          </div>
          {!shown.length && <p className="lead" style={{ paddingTop: 16 }}>No scans match.</p>}
          {[...days].map(([day, scans]) => (
            <div key={day}>
              <div className="day-label">{day}</div>
              <div className="rows">
                {scans.map((scan, i) => (
                  <button key={scan.id} className="row history-row row-in" style={{ borderRadius: segRadius(i, scans.length), ["--i" as string]: i }} onClick={() => open(scan)}>
                    {/* Label photos are never kept, so the row shows the verdict instead of a thumbnail. */}
                    <span className={`scan-thumb tone-${scan.verdict}`}><Icon name="document_scanner" size={26} /></span>
                    <span className="row-main">
                      <span className="row-title" style={{ display: "block" }}>{scan.name}</span>
                      <span className="row-sub" style={{ display: "block" }}>{timeLabel(scan.at)} · {scan.codes.length ? plural(scan.codes.length, "additive") : "no additives"}</span>
                    </span>
                    <StatusPill verdict={scan.verdict} />
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
};

export const SavedPage = () => {
  const { user, byCode } = useApp();
  const saved = user.saved.flatMap((code) => byCode.get(code) ?? []);
  return (
    <>
      <h1 className="page-title" style={{ paddingBottom: 4 }}>Saved</h1>
      {!saved.length ? (
        <EmptyState icon="bookmark" shape={SHAPE_COOKIE} title="Nothing saved yet" actions={<Link to="/app/search" className="btn btn-primary btn-small"><Icon name="search" size={20} />Search additives</Link>}>
          Tap the bookmark on any additive to keep it here.
        </EmptyState>
      ) : (
        <div className="narrow-col">
          <p className="lead">Additives you're keeping an eye on.</p>
          <div className="rows">
            {saved.map((additive, i) => (
              <div key={additive.code} className="row row-in" style={{ borderRadius: segRadius(i, saved.length), paddingRight: 8, ["--i" as string]: i }}>
                <Link to={`/app/additive/${additive.code}`} className="row-main" style={{ display: "flex", alignItems: "center", gap: 14, textDecoration: "none", color: "inherit" }}>
                  <StatusTile verdict={checkOf(additive).verdict} />
                  <span style={{ minWidth: 0 }}>
                    <span className="row-title" style={{ display: "block" }}>{additive.code} · {additive.name}</span>
                    <span className="row-sub" style={{ display: "block" }}>{categoryLine(additive)}</span>
                  </span>
                </Link>
                <button className="icon-btn" style={{ color: "var(--p)" }} onClick={() => toggleSaved(additive.code)} aria-label={`Remove ${additive.code} from Saved`}><Icon name="bookmark" fill /></button>
              </div>
            ))}
          </div>
        </div>
      )}
    </>
  );
};
