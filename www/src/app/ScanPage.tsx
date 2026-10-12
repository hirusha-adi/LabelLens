import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { SAMPLE_LABEL } from "../lib/config";
import { scanText } from "../lib/matcher";
import { readLabel, type Reading } from "../lib/ocr";
import { navigate } from "../lib/router";
import { SHAPE_COOKIE } from "../lib/shapes";
import { getState, recordScan, setScanSource, toast, useApp } from "../lib/store";
import { Icon, Link } from "../ui";
import { pickPhoto } from "./HomePage";

/** The crop rectangle, as fractions of the picture. */
interface Crop { left: number; top: number; right: number; bottom: number }
const FULL: Crop = { left: 0.04, top: 0.08, right: 0.96, bottom: 0.92 };
const MIN = 0.12;

type Handle = "tl" | "tr" | "bl" | "br" | "move";

const clamp = (value: number, low: number, high: number) => Math.min(high, Math.max(low, value));

/** Turns the picture a quarter turn clockwise and returns it as a new object URL. */
const rotate = async (url: string) => {
  const image = new Image();
  image.src = url;
  await image.decode();
  const canvas = document.createElement("canvas");
  canvas.width = image.naturalHeight;
  canvas.height = image.naturalWidth;
  const context = canvas.getContext("2d")!;
  context.translate(canvas.width, 0);
  context.rotate(Math.PI / 2);
  context.drawImage(image, 0, 0);
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.92));
  if (!blob) throw new Error("Could not rotate the picture");
  return URL.createObjectURL(blob);
};

/** Cuts the chosen part out of the picture, no larger than is useful for reading text. */
const cropped = (image: HTMLImageElement, crop: Crop) => {
  const sx = crop.left * image.naturalWidth;
  const sy = crop.top * image.naturalHeight;
  const sw = (crop.right - crop.left) * image.naturalWidth;
  const sh = (crop.bottom - crop.top) * image.naturalHeight;
  const scale = Math.min(1, 2400 / Math.max(sw, sh));
  const canvas = document.createElement("canvas");
  canvas.width = Math.max(1, Math.round(sw * scale));
  canvas.height = Math.max(1, Math.round(sh * scale));
  canvas.getContext("2d")!.drawImage(image, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height);
  return new Promise<Blob>((resolve, reject) => canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("Could not crop the picture"))), "image/png"));
};

export const ScanPage = () => {
  const { scanSource } = useApp();
  const source = scanSource ?? { kind: "sample" as const };
  const [crop, setCrop] = useState<Crop>(FULL);
  const [reading, setReading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [broken, setBroken] = useState(false);
  const frame = useRef<HTMLDivElement>(null);
  const picture = useRef<HTMLImageElement>(null);
  const job = useRef<Reading | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);

  // A new picture starts with a fresh crop; leaving the page stops any reading in progress.
  useEffect(() => {
    setCrop(FULL);
    setBroken(false);
  }, [source.kind === "photo" ? source.url : "sample"]);
  useEffect(() => () => {
    job.current?.cancel();
    clearTimeout(timer.current);
  }, []);

  const drag = (handle: Handle) => (event: ReactPointerEvent) => {
    if (reading) return;
    const box = frame.current?.getBoundingClientRect();
    if (!box) return;
    event.preventDefault();
    event.stopPropagation();
    const start = { x: event.clientX, y: event.clientY, crop };
    const move = (e: PointerEvent) => {
      const dx = (e.clientX - start.x) / box.width;
      const dy = (e.clientY - start.y) / box.height;
      const c = start.crop;
      if (handle === "move") {
        const mx = clamp(dx, -c.left, 1 - c.right);
        const my = clamp(dy, -c.top, 1 - c.bottom);
        setCrop({ left: c.left + mx, right: c.right + mx, top: c.top + my, bottom: c.bottom + my });
        return;
      }
      setCrop({
        left: handle.endsWith("l") ? clamp(c.left + dx, 0, c.right - MIN) : c.left,
        right: handle.endsWith("r") ? clamp(c.right + dx, c.left + MIN, 1) : c.right,
        top: handle.startsWith("t") ? clamp(c.top + dy, 0, c.bottom - MIN) : c.top,
        bottom: handle.startsWith("b") ? clamp(c.bottom + dy, c.top + MIN, 1) : c.bottom,
      });
    };
    const stop = () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", stop);
      window.removeEventListener("pointercancel", stop);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", stop);
    window.addEventListener("pointercancel", stop);
  };

  const finish = (text: string) => {
    recordScan(scanText(text, getState().additives));
    navigate("/app/results", { replace: true });
  };

  const read = async () => {
    setReading(true);
    setProgress(0);
    if (source.kind === "sample") {
      // The sample is text, not a picture, so there is nothing to recognise. A short pause shows what reading looks like.
      timer.current = setTimeout(() => finish(SAMPLE_LABEL), 1200);
      return;
    }
    try {
      const image = picture.current;
      if (!image) throw new Error("No picture");
      job.current = readLabel(await cropped(image, crop), setProgress);
      finish(await job.current.text);
    } catch (error) {
      if (!(error instanceof DOMException && error.name === "AbortError")) toast("Couldn't read that photo. Try again, or choose another one.");
      setReading(false);
    } finally {
      job.current = null;
    }
  };

  const cancel = () => {
    job.current?.cancel();
    clearTimeout(timer.current);
    setReading(false);
  };

  const turn = async () => {
    if (source.kind !== "photo" || reading) return;
    try {
      setScanSource({ kind: "photo", url: await rotate(source.url) });
    } catch {
      toast("Couldn't rotate that photo.");
    }
  };

  const box = { left: `${crop.left * 100}%`, top: `${crop.top * 100}%`, width: `${(crop.right - crop.left) * 100}%`, height: `${(crop.bottom - crop.top) * 100}%` };

  return (
    <div className="camera">
      <div className="camera-bg" />
      <div className="camera-stage">
        {broken ? (
          <div style={{ textAlign: "center", maxWidth: 320 }}>
            <h2>Couldn't open that photo</h2>
            <p className="camera-hint" style={{ marginTop: 8 }}>It may not be an image this browser can show. Choose another.</p>
          </div>
        ) : (
          <div className="camera-frame" ref={frame}>
            {source.kind === "photo" ? (
              <img ref={picture} src={source.url} alt="The label you chose" onError={() => setBroken(true)} style={{ maxHeight: "min(calc(76vh - 284px), 436px)" }} />
            ) : (
              <div className="sample-label">
                <b>INGREDIENTS:</b> {SAMPLE_LABEL.replace(/^INGREDIENTS:\s*/, "")}
              </div>
            )}
            <div className={`crop-box ${reading ? "reading" : ""}`} style={box} onPointerDown={drag("move")}>
              {reading ? (
                <div className="scan-line" />
              ) : (
                <>
                  <div className="crop-grid" />
                  {(["tl", "tr", "bl", "br"] as const).map((handle) => (
                    <div key={handle} className={`crop-handle ${handle}`} onPointerDown={drag(handle)} />
                  ))}
                </>
              )}
            </div>
          </div>
        )}
      </div>

      <div className="camera-top">
        <Link to="/app" className="camera-btn" aria-label="Close"><Icon name="close" /></Link>
        {!reading && !broken && <span className="camera-chip" style={{ cursor: "default" }}><Icon name="crop" size={20} />Crop to the ingredients list</span>}
        {source.kind === "photo" && !broken ? (
          <button className="camera-btn" onClick={turn} aria-label="Rotate" disabled={reading}><Icon name="rotate_right" /></button>
        ) : (
          <span style={{ width: 48 }} />
        )}
      </div>

      {reading ? (
        <div className="camera-bottom reading" role="status">
          <div className="camera-loader"><span className="loader" style={{ width: 40, height: 40, clipPath: SHAPE_COOKIE }} /></div>
          <h2>Reading label…</h2>
          <div className="camera-hint">
            On this device.{source.kind === "photo" && progress > 0 ? ` ${Math.round(progress * 100)}%` : " The first read takes a little longer."}
          </div>
          <button className="camera-cancel" onClick={cancel}>Cancel</button>
        </div>
      ) : (
        <div className="camera-bottom">
          {!broken && <div className="camera-hint">Drag the corners. A tighter crop reads faster.</div>}
          <label className="camera-chip">
            <input type="file" accept="image/*" onChange={pickPhoto} className="sr-only" />
            <Icon name="upload" size={20} />
            {source.kind === "photo" ? "Choose another photo" : "Upload your own photo"}
          </label>
          {!broken && <button className="camera-read" onClick={read}><Icon name="text_select_start" size={22} />Read label</button>}
        </div>
      )}
    </div>
  );
};
