import { useEffect, useRef, useState } from "react";
import { LINKS } from "../lib/config";
import { navigate, useLocation } from "../lib/router";
import { dismissSnack, loadCatalogue, useApp } from "../lib/store";
import { SHAPE_COOKIE } from "../lib/shapes";
import { EmptyState, Icon, LensMark, Link, Loader } from "../ui";
import { DetailPage } from "./DetailPage";
import { HistoryPage, SavedPage } from "./LibraryPages";
import { HomePage } from "./HomePage";
import { PreferencesPage } from "./PreferencesPage";
import { ProfilePage } from "./ProfilePage";
import { ResultsPage } from "./ResultsPage";
import { ScanPage } from "./ScanPage";
import { SearchPage } from "./SearchPage";

const TABS = [
  { path: "/app", label: "Home", icon: "home" },
  { path: "/app/history", label: "History", icon: "history" },
  { path: "/app/saved", label: "Saved", icon: "bookmark" },
  { path: "/app/profile", label: "Profile", icon: "person" },
];

const TITLES: Record<string, string> = { "/app": "Label Lens", "/app/history": "History", "/app/saved": "Saved", "/app/profile": "Profile", "/app/preferences": "My preferences", "/app/search": "Search", "/app/scan": "Scan a label", "/app/results": "Scan result" };

const OverflowMenu = () => {
  const [open, setOpen] = useState(false);
  const wrap = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (event: Event) => {
      if (event instanceof KeyboardEvent ? event.key === "Escape" : !wrap.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", close);
    document.addEventListener("keydown", close);
    return () => {
      document.removeEventListener("pointerdown", close);
      document.removeEventListener("keydown", close);
    };
  }, [open]);
  return (
    <div className="menu-wrap" ref={wrap}>
      <button className="icon-btn" style={open ? { background: "var(--c4)" } : undefined} onClick={() => setOpen(!open)} aria-label="More options" aria-expanded={open}>
        <Icon name="more_vert" />
      </button>
      {open && (
        <div className="menu" role="menu" onClick={() => setOpen(false)}>
          <a href={LINKS.help} target="_blank" rel="noopener" role="menuitem"><Icon name="help" size={22} /><span>Help</span></a>
          <a href="/#how" role="menuitem"><Icon name="lightbulb" size={22} /><span>How it works</span></a>
          <hr />
          <a href={LINKS.licence} target="_blank" rel="noopener" role="menuitem"><Icon name="license" size={22} /><span>Licence</span><Icon name="open_in_new" size={18} /></a>
          <a href={LINKS.legal} target="_blank" rel="noopener" role="menuitem"><Icon name="gavel" size={22} /><span>Legal</span><Icon name="open_in_new" size={18} /></a>
          <a href={LINKS.contributing} target="_blank" rel="noopener" role="menuitem"><Icon name="volunteer_activism" size={22} /><span>Contributing</span><Icon name="open_in_new" size={18} /></a>
        </div>
      )}
    </div>
  );
};

const SearchBox = () => {
  const { pathname, query } = useLocation();
  const { meta } = useApp();
  const onSearch = pathname === "/app/search";
  const value = onSearch ? query.get("q") ?? "" : "";
  const input = useRef<HTMLInputElement>(null);
  // Typing moves to the search page; the address keeps the query so it can be shared and reloaded.
  const update = (text: string) => navigate(`/app/search${text ? `?q=${encodeURIComponent(text)}` : ""}`, { replace: onSearch });
  return (
    <div className="search-box" role="search">
      <Icon name="search" />
      <input
        ref={input}
        value={value}
        onChange={(event) => update(event.target.value)}
        onFocus={() => !onSearch && navigate("/app/search")}
        placeholder={meta ? `Search ${meta.totalCount} additives` : "Search additives"}
        aria-label="Search additives"
        type="search"
        enterKeyHint="search"
        autoComplete="off"
        spellCheck={false}
      />
      {value && (
        <button className="icon-btn" onClick={() => { update(""); input.current?.focus(); }} aria-label="Clear search">
          <Icon name="close" />
        </button>
      )}
    </div>
  );
};

const Page = ({ pathname }: { pathname: string }) => {
  if (pathname === "/app") return <HomePage />;
  if (pathname === "/app/search") return <SearchPage />;
  if (pathname === "/app/scan") return <ScanPage />;
  if (pathname === "/app/results") return <ResultsPage />;
  if (pathname === "/app/history") return <HistoryPage />;
  if (pathname === "/app/saved") return <SavedPage />;
  if (pathname === "/app/profile") return <ProfilePage />;
  if (pathname === "/app/preferences") return <PreferencesPage />;
  const detail = pathname.match(/^\/app\/additive\/([^/]+)$/);
  if (detail) return <DetailPage code={decodeURIComponent(detail[1])} />;
  return (
    <EmptyState icon="search_off" shape={SHAPE_COOKIE} title="There's nothing at this address">
      The page may have moved. <Link to="/app">Go to the start</Link>.
    </EmptyState>
  );
};

export const AppShell = () => {
  const { pathname } = useLocation();
  const { catalogueStatus, snack } = useApp();
  const isTab = TABS.some((tab) => tab.path === pathname);

  useEffect(() => {
    void loadCatalogue();
  }, []);
  useEffect(() => {
    const title = TITLES[pathname];
    if (title) document.title = title === "Label Lens" ? title : `${title} · Label Lens`;
  }, [pathname]);

  return (
    <div className="app">
      <aside className="side only-wide">
        <Link to="/app" className="brand"><LensMark vein="var(--c1)" />Label Lens</Link>
        <Link to="/app/scan" className="scan-btn"><Icon name="document_scanner" size={26} />Scan a label</Link>
        <nav aria-label="Main" style={{ display: "contents" }}>
          {TABS.map((tab) => (
            <Link key={tab.path} to={tab.path} className={`side-link ${pathname === tab.path ? "on" : ""}`} aria-label={tab.label}>
              <Icon name={tab.icon} fill={pathname === tab.path} />
              {tab.label}
            </Link>
          ))}
        </nav>
      </aside>

      <main className="app-main">
        <header className="app-header">
          <Link to="/app" className="only-narrow" aria-label="Label Lens home"><LensMark /></Link>
          <SearchBox />
          <div className="spacer" />
          <OverflowMenu />
        </header>
        {/* Keyed by path so each page plays its entrance, and page state does not leak between pages. */}
        <div className="page" key={pathname}>
          {catalogueStatus === "ready" ? (
            <Page pathname={pathname} />
          ) : catalogueStatus === "loading" ? (
            <div className="empty"><Loader /><p>Loading additives…</p></div>
          ) : (
            <EmptyState icon="sync_problem" shape={SHAPE_COOKIE} tone="error" title="Couldn't load the additive list"
              actions={<button className="btn btn-primary btn-small" onClick={() => void loadCatalogue()}>Try again</button>}>
              Check your connection. Your list, history and preferences are safe in this browser.
            </EmptyState>
          )}
        </div>
      </main>

      {isTab && (
        <nav className="bottom-nav only-narrow" aria-label="Main">
          <div className="bottom-pill">
            {TABS.map((tab) => (
              <Link key={tab.path} to={tab.path} className={pathname === tab.path ? "on" : ""} aria-label={tab.label}>
                <Icon name={tab.icon} fill={pathname === tab.path} />
              </Link>
            ))}
          </div>
          <Link to="/app/scan" className="bottom-scan"><Icon name="document_scanner" size={26} />Scan</Link>
        </nav>
      )}

      {snack && (
        <div className={`snack ${isTab ? "raised" : ""}`} role="status" key={snack.id}>
          <span>{snack.text}</span>
          {snack.undo && <button onClick={() => { snack.undo?.(); dismissSnack(); }}>Undo</button>}
        </div>
      )}
    </div>
  );
};
