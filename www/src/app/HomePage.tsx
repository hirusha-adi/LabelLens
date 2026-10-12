import type { ChangeEvent } from "react";
import { segRadius } from "../lib/format";
import { navigate } from "../lib/router";
import { SHAPE_BURST } from "../lib/shapes";
import { checkOf, clearList, setScanSource, useApp } from "../lib/store";
import { VERDICTS, Icon, Link, StatusTile } from "../ui";

/** Starts a scan from a chosen photo. Shared by Home and the scan page. */
export const pickPhoto = (event: ChangeEvent<HTMLInputElement>) => {
  const file = event.target.files?.[0];
  event.target.value = "";
  if (!file) return;
  setScanSource({ kind: "photo", url: URL.createObjectURL(file) });
  navigate("/app/scan");
};

export const HomePage = () => {
  const { user, byCode, meta } = useApp();
  const list = user.list.flatMap((code) => byCode.get(code) ?? []);
  return (
    <>
      <div className="home-hero">
        <h1>Know what you are about to eat.</h1>
        <p>Scan an ingredients list or search {meta?.totalCount ?? ""} additives. Labels are read on this device.</p>
      </div>
      <div className="cols">
        <section style={{ flex: "3 1 400px", minWidth: 0 }}>
          {list.length ? (
            <>
              <div className="list-head">
                <span>My list · {list.length}</span>
                <button className="text-btn" onClick={clearList}>Clear all</button>
              </div>
              <div className="rows">
                {list.map((additive, i) => {
                  const check = checkOf(additive);
                  return (
                    <Link key={additive.code} to={`/app/additive/${additive.code}`} className="row row-in" style={{ borderRadius: segRadius(i, list.length), ["--i" as string]: i }}>
                      <StatusTile verdict={check.verdict} />
                      <span className="row-main">
                        <span className="row-title" style={{ display: "block" }}>{additive.code} · {additive.name}</span>
                        <span className="row-sub" style={{ display: "block" }}>{VERDICTS[check.verdict].label} · {check.reason ?? additive.note}</span>
                      </span>
                      <Icon name="chevron_right" className="muted" />
                    </Link>
                  );
                })}
              </div>
            </>
          ) : (
            <div className="empty-list">
              <div className="basket">
                <div className="basket-burst" style={{ clipPath: SHAPE_BURST }} />
                <div className="basket-main"><Icon name="shopping_basket" size={48} /></div>
              </div>
              <h2>Your list is empty</h2>
              <p>Scan a label or search to add additives you want to keep an eye on.</p>
            </div>
          )}
        </section>

        <section style={{ flex: "2 1 320px", minWidth: 0, display: "flex", flexDirection: "column", gap: 20 }}>
          <div className="panel scan-card">
            <h2>Scan a label</h2>
            <p>Get the ingredients list in frame. Your photo stays on this device.</p>
            <div className="rows">
              <label className="row row-hover" style={{ borderRadius: segRadius(0, 2), gap: 16 }}>
                <input type="file" accept="image/*" onChange={pickPhoto} className="sr-only" />
                <span className="square-icon" style={{ background: "var(--p)", color: "var(--op)" }}><Icon name="upload" size={26} /></span>
                <span>
                  <span className="row-title-plain" style={{ display: "block" }}>Upload a photo</span>
                  <span className="row-sub" style={{ display: "block" }}>PNG or JPG, held flat in good light</span>
                </span>
              </label>
              <button className="row" style={{ borderRadius: segRadius(1, 2), gap: 16 }} onClick={() => { setScanSource({ kind: "sample" }); navigate("/app/scan"); }}>
                <span className="square-icon" style={{ background: "var(--sc)", color: "var(--osc)" }}><Icon name="photo_library" size={26} /></span>
                <span>
                  <span className="row-title-plain" style={{ display: "block" }}>Use a sample label</span>
                  <span className="row-sub" style={{ display: "block" }}>Fruit gummies, to see how it works</span>
                </span>
              </button>
            </div>
            <div className="on-device"><Icon name="shield_lock" size={18} fill />Read on-device. Your photo is not uploaded.</div>
          </div>
          <div>
            <div className="section-label">How it works</div>
            <div className="rows">
              {[
                ["Capture the label", "Photograph the ingredients list."],
                ["Confirm the matches", "Tick the additives we found."],
                ["Read the details", "Evidence, sources and where it's banned."],
              ].map(([title, text], i) => (
                <div className="row" key={title} style={{ borderRadius: segRadius(i, 3), minHeight: 0 }}>
                  <span className="step-dot">{i + 1}</span>
                  <div>
                    <div className="row-title-plain">{title}</div>
                    <div className="row-sub">{text}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>
      </div>
    </>
  );
};
