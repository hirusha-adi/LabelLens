import { useState, type FormEvent } from "react";
import { segRadius } from "../lib/format";
import { goBack } from "../lib/router";
import { getState, setPreferences, useApp } from "../lib/store";
import type { Level, Preferences } from "../lib/types";
import { Icon } from "../ui";

const LevelPill = ({ level }: { level: Level | "off" }) =>
  level === "avoid" ? (
    <span className="level level-avoid"><Icon name="block" size={16} fill />Avoid</span>
  ) : level === "warn" ? (
    <span className="level level-warn"><Icon name="notifications" size={16} />Warn</span>
  ) : (
    <span className="level level-off">Off</span>
  );

/** Allergies, diet, things to avoid and stricter checks, on one page. Every change is saved at once. */
export const PreferencesPage = () => {
  const { user, options, additives } = useApp();
  const prefs = user.preferences;
  // Handlers read the stored preferences at the moment they run, not the copy this
  // render saw, so two quick clicks cannot overwrite each other.
  const now = () => getState().user.preferences;
  const update = (change: (current: Preferences) => Partial<Preferences>) => {
    const current = now();
    setPreferences({ ...current, ...change(current) });
  };
  const [custom, setCustom] = useState("");
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState("");

  // One click: off, always avoid, warn me, off again.
  const cycleAllergy = (name: string) => {
    update((p) => {
      const current = p.allergies.find((a) => a.name === name);
      if (!current) return { allergies: [...p.allergies, { name, level: "avoid", custom: false }] };
      if (current.level === "avoid") return { allergies: p.allergies.map((a) => (a.name === name ? { ...a, level: "warn" as const } : a)) };
      return { allergies: p.allergies.filter((a) => a.name !== name) };
    });
  };

  const addCustom = (event: FormEvent) => {
    event.preventDefault();
    const name = custom.trim().slice(0, 40);
    if (!name) return;
    update((p) => {
      if (p.allergies.some((a) => a.name.toLowerCase() === name.toLowerCase())) return {};
      const known = options.allergens.find((a) => a.toLowerCase() === name.toLowerCase());
      return { allergies: [...p.allergies, { name: known ?? name, level: "avoid", custom: !known }] };
    });
    setCustom("");
  };

  const addCode = (event: FormEvent) => {
    event.preventDefault();
    const text = code.trim();
    if (!text) return;
    const compact = text.toLowerCase().replace(/[\s\-.]/g, "").replace(/^ins/, "e");
    const wanted = /^\d/.test(compact) ? `e${compact}` : compact;
    const found = additives.find((a) => a.code.toLowerCase() === wanted) ?? additives.find((a) => a.name.toLowerCase() === text.toLowerCase());
    if (!found) return setCodeError("We don't have that one. Try a code like E951.");
    update((p) => (p.additives.includes(found.code) ? {} : { additives: [...p.additives, found.code] }));
    setCode("");
    setCodeError("");
  };

  const cycleGroup = (id: string) => {
    update((p) => {
      const level = p.groups[id] ?? "off";
      return { groups: { ...p.groups, [id]: level === "off" ? "warn" : level === "warn" ? "avoid" : "off" } };
    });
  };

  const levelOf = (name: string) => prefs.allergies.find((a) => a.name === name)?.level;

  return (
    <>
      <div className="page-head">
        <button className="icon-btn icon-btn-tonal" onClick={() => goBack("/app/profile")} aria-label="Back"><Icon name="arrow_back" /></button>
        <h1>My preferences</h1>
      </div>
      <div className="narrow-col stack" style={{ gap: 32 }}>
        <p className="lead" style={{ padding: "0 4px" }}>
          Every scan and additive is checked against these. Avoid makes a product's verdict Avoid; Warn makes it Caution. They are kept in this browser and never sent anywhere.
        </p>

        <section className="pref-section">
          <h2>Allergies and intolerances</h2>
          <p>We look for these words in the text of every label you scan.</p>
          <div className="legend">
            <span><Icon name="block" size={16} fill className="text-avoid" />Always avoid</span>
            <span><Icon name="notifications" size={16} style={{ color: "var(--s)" }} />Warn me</span>
            <span>Click again to switch</span>
          </div>
          <div className="pref-chips">
            {options.allergens.map((name) => {
              const level = levelOf(name);
              return (
                <button key={name} className={`pref-chip ${level ?? ""}`} onClick={() => cycleAllergy(name)} aria-label={`${name}: ${level === "avoid" ? "always avoid" : level === "warn" ? "warn me" : "off"}`}>
                  {level && <Icon name={level === "avoid" ? "block" : "notifications"} size={18} fill={level === "avoid"} />}
                  {name}
                </button>
              );
            })}
            {prefs.allergies.filter((a) => a.custom).map((allergy) => (
              <span key={allergy.name} className="pref-chip custom">
                {allergy.name}
                <button onClick={() => update((p) => ({ allergies: p.allergies.filter((a) => a.name !== allergy.name) }))} aria-label={`Remove ${allergy.name}`}><Icon name="close" size={18} /></button>
              </span>
            ))}
          </div>
          <form className="add-form" onSubmit={addCustom}>
            <input value={custom} onChange={(event) => setCustom(event.target.value)} placeholder="Add your own, such as kiwi" aria-label="Add your own allergen" maxLength={40} />
            <button className="btn btn-outline btn-small" style={{ height: 48 }} disabled={!custom.trim()}>Add</button>
          </form>
        </section>

        <section className="pref-section">
          <h2>Diet</h2>
          <p>We check where additives come from, for example animal-derived gelatine or carmine.</p>
          <div className="rows" role="radiogroup" aria-label="Diet">
            {options.diets.map((diet, i) => {
              const on = prefs.diet === diet.id;
              return (
                <button key={diet.id} className={`row ${on ? "row-selected" : ""}`} style={{ borderRadius: segRadius(i, options.diets.length), minHeight: 64 }} onClick={() => update(() => ({ diet: diet.id }))} role="radio" aria-checked={on}>
                  <Icon name={diet.icon} className={on ? "" : "muted"} />
                  <span className="row-main row-title-plain">{diet.label}</span>
                  <Icon name={on ? "check" : "chevron_right"} className={on ? "" : "muted"} style={on ? undefined : { visibility: "hidden" }} />
                </button>
              );
            })}
          </div>
          <div className="note-line" style={{ marginTop: 12 }}>
            <Icon name="info" size={18} />
            <span>Origin is not always certain. If an additive can be plant or animal based, we show Caution rather than Avoid.</span>
          </div>
        </section>

        <section className="pref-section">
          <h2>Things to avoid</h2>
          <p>Click a group to switch between Off, Warn and Avoid.</p>
          <div className="rows">
            {options.groups.map((group, i) => (
              <button key={group.id} className="row" style={{ borderRadius: segRadius(i, options.groups.length), minHeight: 64 }} onClick={() => cycleGroup(group.id)}>
                <Icon name={group.icon} className="muted" />
                <span className="row-main row-title-plain">{group.label}</span>
                <LevelPill level={prefs.groups[group.id] ?? "off"} />
              </button>
            ))}
          </div>
          <div className="section-label" style={{ paddingTop: 20 }}>Specific additives</div>
          <div className="pref-chips">
            {prefs.additives.map((additiveCode) => (
              <span key={additiveCode} className="pref-chip custom" style={{ paddingLeft: 8, gap: 8 }}>
                <span className="code-badge" style={{ minWidth: 0, height: 24, fontSize: 12, borderRadius: 6, padding: "0 6px" }}>{additiveCode}</span>
                {additives.find((a) => a.code === additiveCode)?.name ?? "Additive"}
                <button onClick={() => update((p) => ({ additives: p.additives.filter((c) => c !== additiveCode) }))} aria-label={`Remove ${additiveCode}`}><Icon name="close" size={18} /></button>
              </span>
            ))}
          </div>
          <form className="add-form" onSubmit={addCode}>
            <input value={code} onChange={(event) => { setCode(event.target.value); setCodeError(""); }} placeholder="Add by code, such as E951" aria-label="Add an additive by code" aria-invalid={Boolean(codeError)} />
            <button className="btn btn-outline btn-small" style={{ height: 48 }} disabled={!code.trim()}>Add</button>
          </form>
          {codeError && <div className="field-error" role="alert">{codeError}</div>}
        </section>

        <section className="pref-section">
          <h2>Who are you shopping for?</h2>
          <p>These make the checks stricter. Things that are usually Caution become Avoid.</p>
          <div className="rows">
            {options.sensitivities.map((sensitivity, i) => {
              const on = prefs.sensitivities.includes(sensitivity.id);
              return (
                <button key={sensitivity.id} className="row" style={{ borderRadius: segRadius(i, options.sensitivities.length) }} role="switch" aria-checked={on}
                  onClick={() => update((p) => ({ sensitivities: p.sensitivities.includes(sensitivity.id) ? p.sensitivities.filter((s) => s !== sensitivity.id) : [...p.sensitivities, sensitivity.id] }))}>
                  <span className="square-icon" style={{ background: "var(--tc)", color: "var(--otc)" }}><Icon name={sensitivity.icon} size={26} /></span>
                  <span className="row-main">
                    <span className="row-title-plain" style={{ display: "block" }}>{sensitivity.label}</span>
                    <span className="row-sub" style={{ display: "block" }}>{sensitivity.description}</span>
                  </span>
                  <span className={`switch ${on ? "on" : ""}`} />
                </button>
              );
            })}
          </div>
          <div className="note-line" style={{ marginTop: 12 }}>
            <Icon name="info" size={18} />
            <span>Label Lens is not medical advice. Talk to a doctor about allergies and pregnancy.</span>
          </div>
        </section>
      </div>
    </>
  );
};
