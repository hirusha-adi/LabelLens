import type { ChangeEvent } from "react";
import { plural, segRadius } from "../lib/format";
import { clearHistory, exportData, importData, resolvedTheme, setTheme, toast, useApp } from "../lib/store";
import { Icon, Link } from "../ui";

const LARGEST_BACKUP = 5 * 1024 * 1024;

const download = () => {
  const url = URL.createObjectURL(new Blob([exportData()], { type: "application/json" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = `label-lens-backup-${new Date().toISOString().slice(0, 10)}.json`;
  link.click();
  URL.revokeObjectURL(url);
  toast("Backup saved to your downloads");
};

const restore = async (event: ChangeEvent<HTMLInputElement>) => {
  const file = event.target.files?.[0];
  event.target.value = "";
  if (!file) return;
  if (file.size > LARGEST_BACKUP) return toast("That file isn't a Label Lens backup.");
  if (!window.confirm("Replace your list, preferences, saved additives and scan history in this browser with the ones in the file? This can't be undone.")) return;
  toast(importData(await file.text()) ? "Backup restored" : "That file isn't a Label Lens backup.");
};

export const ProfilePage = () => {
  const { user, options } = useApp();
  const prefs = user.preferences;
  const diet = options.diets.find((d) => d.id === prefs.diet && d.strictness !== "none")?.label;
  const avoids = Object.values(prefs.groups).filter((level) => level !== "off").length + prefs.additives.length;
  const summary = [diet, prefs.allergies.length && plural(prefs.allergies.length, "allergy", "allergies"), avoids && plural(avoids, "avoid")].filter(Boolean).join(" · ") || "Not set up yet";
  const theme = resolvedTheme(user.theme);

  return (
    <>
      <h1 className="page-title">Profile</h1>
      <div className="narrow-col stack">
        <div className="rows">
          <Link to="/app/preferences" className="row" style={{ borderRadius: segRadius(0, 1), gap: 16 }}>
            <Icon name="tune" className="muted" />
            <span className="row-main">
              <span className="row-title-plain" style={{ display: "block" }}>My preferences</span>
              <span className="row-sub" style={{ display: "block" }}>{summary}</span>
            </span>
            <Icon name="chevron_right" className="muted" />
          </Link>
        </div>

        <div>
          <div className="section-label">Appearance</div>
          <div className="seg" role="radiogroup" aria-label="Appearance">
            {([["light", "Light", "light_mode"], ["dark", "Dark", "dark_mode"]] as const).map(([id, label, icon]) => (
              <button key={id} className={theme === id ? "on" : ""} onClick={() => setTheme(id)} role="radio" aria-checked={theme === id}>
                <Icon name={theme === id ? "check" : icon} size={20} />
                {label}
              </button>
            ))}
          </div>
        </div>

        <div>
          <div className="section-label">Your data</div>
          <div className="rows">
            <button className="row" style={{ borderRadius: segRadius(0, 3), gap: 16 }} onClick={download}>
              <Icon name="download" className="muted" />
              <span className="row-main">
                <span className="row-title-plain" style={{ display: "block" }}>Back up to a file</span>
                <span className="row-sub" style={{ display: "block" }}>Works with the Android app too</span>
              </span>
            </button>
            <label className="row row-hover" style={{ borderRadius: segRadius(1, 3), gap: 16 }}>
              <input type="file" accept="application/json,.json" onChange={restore} className="sr-only" />
              <Icon name="restore" className="muted" />
              <span className="row-main">
                <span className="row-title-plain" style={{ display: "block" }}>Restore from a file</span>
                <span className="row-sub" style={{ display: "block" }}>Replace what's here with a backup</span>
              </span>
            </label>
            <button className="row" style={{ borderRadius: segRadius(2, 3), gap: 16 }} onClick={clearHistory}>
              <Icon name="delete_sweep" className="danger" />
              <span className="row-main"><span className="row-title-plain danger" style={{ display: "block" }}>Clear scan history</span></span>
            </button>
          </div>
        </div>

        <div className="note-line">
          <Icon name="shield_lock" size={20} fill style={{ color: "var(--t)" }} />
          <span>
            Labels are read on this device. Photos never leave it, and your list, history and preferences stay in this browser.
            Looking up additives asks the Label Lens server, which records your IP address, the kind of device and which additive was asked for.
          </span>
        </div>
      </div>
    </>
  );
};
