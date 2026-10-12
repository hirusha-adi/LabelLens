// A very small router on the History API. Paths: "/" is the landing page and
// "/app/..." the web app. The host must serve index.html for any path under /app.

import { useSyncExternalStore } from "react";

const listeners = new Set<() => void>();
const notify = () => listeners.forEach((listener) => listener());

if (typeof window !== "undefined") window.addEventListener("popstate", notify);

/** How many in-app navigations deep we are, so Back knows whether there is anywhere to go. */
let depth = 0;

export const navigate = (to: string, options: { replace?: boolean } = {}) => {
  if (to === window.location.pathname + window.location.search) return;
  if (options.replace) window.history.replaceState(null, "", to);
  else {
    window.history.pushState(null, "", to);
    depth += 1;
  }
  window.scrollTo(0, 0);
  notify();
};

/** Goes back within the app, or to `fallback` when this page was opened directly. */
export const goBack = (fallback = "/app") => {
  if (depth > 0) {
    depth -= 1;
    window.history.back();
  } else navigate(fallback, { replace: true });
};

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

export const useLocation = () => {
  const href = useSyncExternalStore(subscribe, () => window.location.pathname + window.location.search);
  const [pathname, search = ""] = href.split("?");
  return { pathname: pathname.replace(/\/+$/, "") || "/", query: new URLSearchParams(search) };
};
