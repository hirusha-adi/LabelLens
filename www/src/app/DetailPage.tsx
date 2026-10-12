import { useEffect, useState } from "react";
import { fetchDetail } from "../lib/api";
import { segRadius } from "../lib/format";
import { goBack } from "../lib/router";
import { SHAPE_CLOVER, SHAPE_COOKIE } from "../lib/shapes";
import { addToList, checkOf, removeFromList, toast, toggleSaved, useApp } from "../lib/store";
import { categoryLine, preferencesAreEmpty, type AdditiveDetail } from "../lib/types";
import { Bold, EmptyState, Icon, Link, StatusPill } from "../ui";

type Load = { status: "loading" } | { status: "failed" } | { status: "ready"; detail: AdditiveDetail };

const share = async (title: string, text: string) => {
  const url = window.location.href;
  try {
    if (navigator.share) await navigator.share({ title, text, url });
    else {
      await navigator.clipboard.writeText(url);
      toast("Link copied");
    }
  } catch (error) {
    // Closing the share sheet is not a failure.
    if (!(error instanceof DOMException && error.name === "AbortError")) toast("Couldn't share this page.");
  }
};

export const DetailPage = ({ code }: { code: string }) => {
  const { byCode, user } = useApp();
  // The address may use any letter case; the catalogue has the real spelling.
  const additive = byCode.get(code) ?? [...byCode.values()].find((a) => a.code.toLowerCase() === code.toLowerCase());
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!additive) return;
    const controller = new AbortController();
    setLoad({ status: "loading" });
    fetchDetail(additive.code, controller.signal)
      .then((detail) => setLoad({ status: "ready", detail }))
      .catch(() => !controller.signal.aborted && setLoad({ status: "failed" }));
    document.title = `${additive.code} ${additive.name} · Label Lens`;
    return () => controller.abort();
  }, [additive?.code, attempt]);

  if (!additive) {
    return (
      <EmptyState icon="search_off" shape={SHAPE_CLOVER} title={`We don't have ${code}`} actions={<Link to="/app/search" className="btn btn-primary btn-small">Search additives</Link>}>
        That code isn't in the additive list.
      </EmptyState>
    );
  }

  const check = checkOf(additive);
  const inList = user.list.includes(additive.code);
  const isSaved = user.saved.includes(additive.code);
  const personal = !preferencesAreEmpty(user.preferences);

  return (
    <>
      <div className="detail-top">
        <button className="icon-btn icon-btn-tonal" onClick={() => goBack()} aria-label="Back"><Icon name="arrow_back" /></button>
        <div style={{ display: "flex" }}>
          <button className="icon-btn" style={isSaved ? { color: "var(--p)" } : undefined} onClick={() => toggleSaved(additive.code)} aria-label={isSaved ? "Remove from Saved" : "Save"} aria-pressed={isSaved}>
            <Icon name={isSaved ? "bookmark" : "bookmark_add"} fill={isSaved} />
          </button>
          <button className="icon-btn" onClick={() => share(`${additive.code} ${additive.name}`, `${additive.code} · ${additive.name}: ${additive.note}.`)} aria-label="Share"><Icon name="share" /></button>
        </div>
      </div>
      <div className="cols">
        <div className="detail-main">
          <div className="detail-head">
            <div className="detail-chips">
              <span className="code-badge">{additive.code}</span>
              <span className="chip"><Icon name={additive.categoryIcon} size={16} />{categoryLine(additive)}</span>
              <StatusPill verdict={check.verdict} />
            </div>
            <h1>{additive.name}</h1>
            {additive.aliases.length > 0 && <p>Also called {additive.aliases.join(", ")}</p>}
          </div>

          {personal && (
            check.reason && check.verdict !== "safe" ? (
              <div className={`banner tone-${check.verdict}`}>
                <Icon name="person_alert" fill />
                <div><b>Contains {check.reason}</b>{check.explanation}</div>
              </div>
            ) : (
              <div className="banner tone-safe">
                <Icon name="verified_user" fill />
                <div><b>No conflicts with your profile</b></div>
              </div>
            )
          )}

          {load.status === "loading" && (
            <div className="rows accs" aria-busy="true">
              <div className="skeleton" style={{ height: 140, borderRadius: segRadius(0, 3) }} />
              <div className="skeleton" style={{ height: 60, borderRadius: segRadius(1, 3) }} />
              <div className="skeleton" style={{ height: 60, borderRadius: segRadius(2, 3) }} />
            </div>
          )}
          {load.status === "failed" && (
            <EmptyState icon="sync_problem" shape={SHAPE_COOKIE} tone="error" title="Couldn't load the details"
              actions={<button className="btn btn-primary btn-small" onClick={() => setAttempt(attempt + 1)}>Try again</button>}>
              Check your connection. Your list and scanning still work.
            </EmptyState>
          )}
          {load.status === "ready" && (
            <div className="rows accs">
              {load.detail.sections.map((section, i) => (
                <details key={section.title} className="acc" open={i === 0} style={{ borderRadius: segRadius(i, load.detail.sections.length) }}>
                  <summary>
                    <span>{section.title}</span>
                    <span>{section.summary}<Icon name="expand_more" className="when-closed" /><Icon name="expand_less" className="when-open" /></span>
                  </summary>
                  <div className="acc-body">{section.paragraphs.map((paragraph, n) => <span key={n}>{paragraph}</span>)}</div>
                </details>
              ))}
            </div>
          )}
        </div>

        <aside className="detail-side">
          {load.status === "ready" && load.detail.regulatory.length > 0 && (
            <div className="regulatory">
              {load.detail.regulatory.map((line, i) => (
                <div key={i}>
                  <Icon name={line.kind === "banned" ? "block" : line.kind === "approved" ? "check_circle" : "info"} size={22} fill={line.kind !== "note"}
                    className={line.kind === "banned" ? "text-avoid" : line.kind === "approved" ? "text-safe" : "muted"} />
                  <span><Bold text={line.text} /></span>
                </div>
              ))}
            </div>
          )}
          <button className={`btn ${inList ? "btn-outline" : "btn-primary"}`} onClick={() => (inList ? removeFromList(additive.code) : addToList([additive.code]))}>
            <Icon name={inList ? "check" : "add"} size={22} />
            {inList ? "In my list" : "Add to my list"}
          </button>
          {load.status === "ready" && load.detail.sources.length > 0 && (
            <div>
              <div className="section-label">Learn more</div>
              <div className="sources">
                {load.detail.sources.map((source) => (
                  <a key={source.url} href={source.url} target="_blank" rel="noopener" className="source-link">{source.label}<Icon name="open_in_new" size={18} /></a>
                ))}
              </div>
            </div>
          )}
          {load.status === "ready" && <div className="footnote">{load.detail.footnote}</div>}
        </aside>
      </div>
    </>
  );
};
