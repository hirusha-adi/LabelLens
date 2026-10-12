// Shared pieces used by both the landing page and the web app.

import type { CSSProperties, MouseEvent, ReactNode } from "react";
import { ICONS, type IconName } from "./lib/icons";
import { navigate } from "./lib/router";
import { SHAPE_CLOVER, SHAPE_COOKIE, SHAPE_SOFT_SQUARE, SHAPE_SUNNY } from "./lib/shapes";
import type { VerdictName } from "./lib/types";

/** A Material Symbols Rounded icon. Unknown names (the API may send new ones) fall back to a neutral icon. */
export const Icon = ({ name, size = 24, fill = false, className = "", style }: { name: string; size?: number; fill?: boolean; className?: string; style?: CSSProperties }) => (
  <span className={`icon ${fill ? "icon-fill" : ""} ${className}`} style={{ fontSize: size, ...style }} aria-hidden="true">
    {ICONS[name as IconName] ?? ICONS.science}
  </span>
);

/** The lens-and-leaf logo mark. `vein` draws the highlight on the leaf, in the colour behind it. */
export const LensMark = ({ size = 36, vein }: { size?: number; vein?: string }) => (
  <svg width={size} height={size} viewBox="30 30 48 48" aria-hidden="true">
    <circle cx="49" cy="49" r="15" fill="none" stroke="var(--p)" strokeWidth="5.5" />
    <line x1="60.5" y1="60.5" x2="70" y2="70" stroke="var(--p)" strokeWidth="7" strokeLinecap="round" />
    <path d="M41.5 56.5C41.5 47 47 41.5 56.5 41.5C56.5 51 51 56.5 41.5 56.5Z" fill="var(--leaf)" />
    {vein && <line x1="43.5" y1="54.5" x2="51.5" y2="46.5" stroke={vein} strokeWidth="1.8" strokeLinecap="round" />}
  </svg>
);

/** An in-site link that changes the page without reloading it. */
export const Link = ({ to, className, children, ...rest }: { to: string; className?: string; style?: CSSProperties; children: ReactNode; "aria-label"?: string; title?: string }) => (
  <a
    href={to}
    className={className}
    onClick={(event: MouseEvent<HTMLAnchorElement>) => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
      navigate(to);
    }}
    {...rest}
  >
    {children}
  </a>
);

export const VERDICTS: Record<VerdictName, { label: string; icon: string; big: string; shape: string }> = {
  avoid: { label: "Avoid", icon: "block", big: "block", shape: SHAPE_SUNNY },
  caution: { label: "Caution", icon: "warning", big: "priority_high", shape: SHAPE_CLOVER },
  safe: { label: "Safe", icon: "check_circle", big: "check", shape: SHAPE_SOFT_SQUARE },
};

export const StatusPill = ({ verdict }: { verdict: VerdictName }) => (
  <span className={`pill pill-${verdict}`}>
    <Icon name={VERDICTS[verdict].icon} size={18} fill />
    {VERDICTS[verdict].label}
  </span>
);

export const StatusTile = ({ verdict }: { verdict: VerdictName }) => (
  <span className={`tile tile-${verdict}`}>
    <Icon name={VERDICTS[verdict].icon} size={26} fill />
  </span>
);

/** The big verdict mark. Each level has its own shape as well as its own colour. */
export const VerdictMark = ({ verdict, size = 96 }: { verdict: VerdictName; size?: number }) => (
  <span className={`verdict-mark solid-${verdict}`} style={{ width: size, height: size, clipPath: VERDICTS[verdict].shape }}>
    <Icon name={VERDICTS[verdict].big} size={Math.round(size * 0.48)} fill />
  </span>
);

export const CheckMark = ({ checked }: { checked: boolean }) =>
  checked ? (
    <span className="check check-on">
      <Icon name="check" size={16} />
    </span>
  ) : (
    <span className="check" />
  );

export const Loader = ({ size = 48 }: { size?: number }) => <span className="loader" style={{ width: size, height: size, clipPath: SHAPE_COOKIE }} role="status" aria-label="Loading" />;

/** Turns `**bold**` markers from the API into bold text. */
export const Bold = ({ text }: { text: string }) => <>{text.split("**").map((part, i) => (i % 2 ? <b key={i}>{part}</b> : part))}</>;

/** Illustration, title and message for empty and error states. */
export const EmptyState = ({ icon, shape, tone = "primary", title, children, actions }: { icon: string; shape: string; tone?: "primary" | "error"; title: string; children: ReactNode; actions?: ReactNode }) => (
  <div className="empty">
    <span className={`empty-hero empty-${tone}`} style={{ clipPath: shape }}>
      <Icon name={icon} size={48} />
    </span>
    <h2>{title}</h2>
    <p>{children}</p>
    {actions && <div className="empty-actions">{actions}</div>}
  </div>
);
