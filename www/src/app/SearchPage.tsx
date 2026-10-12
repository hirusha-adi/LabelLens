import { useMemo, useState } from "react";
import { plural, segRadius } from "../lib/format";
import { searchAdditives } from "../lib/matcher";
import { navigate, useLocation } from "../lib/router";
import { SHAPE_CLOVER } from "../lib/shapes";
import { addToList, useApp } from "../lib/store";
import { categoryLine } from "../lib/types";
import { CheckMark, EmptyState, Link } from "../ui";

// Rendering every additive at once is fine, but growing the list in steps keeps
// the first paint quick on phones.
const PAGE = 60;

export const SearchPage = () => {
  const { additives } = useApp();
  const { query } = useLocation();
  const q = query.get("q") ?? "";
  const [selected, setSelected] = useState<string[]>([]);
  const [limit, setLimit] = useState(PAGE);
  const results = useMemo(() => searchAdditives(q, additives), [q, additives]);
  const shown = results.slice(0, limit);
  const toggle = (code: string) => setSelected((codes) => (codes.includes(code) ? codes.filter((c) => c !== code) : [...codes, code]));

  if (!results.length) {
    return (
      <EmptyState icon="search_off" shape={SHAPE_CLOVER} title={`No additives match "${q.trim()}"`}
        actions={<button className="btn btn-outline btn-small" onClick={() => navigate("/app/search", { replace: true })}>Clear search</button>}>
        Try the name, or a code like E330 or INS 330. Spaces and dashes don't matter.
      </EmptyState>
    );
  }

  return (
    <div className="narrow-col">
      <div className="result-meta">
        <span>{q.trim() ? plural(results.length, "result") : `All additives · ${results.length}`}</span>
        <span style={{ color: "var(--p)" }}>{selected.length ? `${selected.length} selected` : ""}</span>
      </div>
      <div className="rows">
        {shown.map((additive, i) => {
          const on = selected.includes(additive.code);
          return (
            <div key={additive.code} className={`row result-row ${on ? "row-selected" : ""} ${i < 20 ? "row-in" : ""}`} style={{ borderRadius: segRadius(i, shown.length), ["--i" as string]: i }}>
              <span className="code-badge">{additive.code}</span>
              <Link to={`/app/additive/${additive.code}`} className="row-main">
                <span className="row-title-plain" style={{ display: "block" }}>{additive.name}</span>
                <span className="row-sub" style={{ display: "block" }}>{categoryLine(additive)}</span>
              </Link>
              <button className="icon-btn" onClick={() => toggle(additive.code)} role="checkbox" aria-checked={on} aria-label={`Select ${additive.code} ${additive.name}`}>
                <CheckMark checked={on} />
              </button>
            </div>
          );
        })}
      </div>
      {results.length > limit && (
        <button className="btn btn-outline" style={{ alignSelf: "center", marginTop: 16 }} onClick={() => setLimit(limit + PAGE * 3)}>
          Show more ({results.length - limit} left)
        </button>
      )}
      {selected.length > 0 && (
        <div className="action-bar">
          <button className="btn btn-outline" onClick={() => setSelected([])}>Clear</button>
          <button className="btn btn-primary" onClick={() => { addToList(selected); setSelected([]); }}>Add {selected.length} to my list</button>
        </div>
      )}
    </div>
  );
};
