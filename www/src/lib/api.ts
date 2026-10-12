// Reads the additive catalogue from the Label Lens API. Two requests exist: the
// whole list, and one additive's page. The browser's own cache holds both and
// revalidates them with the server's ETag, so unchanged data is not downloaded twice.

import { API_URL } from "./config";
import type { AdditiveDetail, Catalogue } from "./types";

const get = async <T>(path: string, signal?: AbortSignal): Promise<T> => {
  const response = await fetch(`${API_URL}${path}`, { headers: { Accept: "application/json" }, signal });
  if (!response.ok) throw new Error(`The server answered ${response.status}`);
  return (await response.json()) as T;
};

export const fetchCatalogue = (signal?: AbortSignal) => get<Catalogue>("/additives", signal);

export const fetchDetail = (code: string, signal?: AbortSignal) => get<AdditiveDetail>(`/additives/${encodeURIComponent(code)}`, signal);
