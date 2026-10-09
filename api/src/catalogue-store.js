// The catalogue as the running API sees it: read from Supabase, held in memory,
// and refreshed when the seeded version changes.

const PAGE = 1000;

const toSummary = (row) => ({
  code: row.code,
  name: row.name,
  shortName: row.short_name,
  category: row.category,
  descriptor: row.descriptor,
  categoryIcon: row.category_icon,
  status: row.status,
  note: row.note,
  aliases: row.aliases,
  groups: row.groups,
  sensitivities: row.sensitivities,
  origin: row.origin,
  bannedIn: row.banned_in,
  hasDetail: row.has_detail,
});

const check = ({ data, error }, what) => {
  if (error) throw new Error(`Could not load ${what} from Supabase: ${error.message}`);
  return data;
};

/**
 * @param {() => import("@supabase/supabase-js").SupabaseClient} anon
 * @param {{ refreshSeconds?: number }} [options]
 */
export const createCatalogueStore = async (anon, { refreshSeconds = 300 } = {}) => {
  let meta = null;
  let additives = [];
  let byLowerCode = new Map();
  let details = new Map();
  let checkedAt = 0;

  const load = async () => {
    const client = anon();
    const metaRow = check(await client.from("catalogue_meta").select("*").maybeSingle(), "the catalogue");
    if (!metaRow) throw new Error("The catalogue is empty. Run `pnpm seed` to load it.");
    const rows = [];
    for (let from = 0; ; from += PAGE) {
      const page = check(await client.from("additives").select("*").order("sort_order").range(from, from + PAGE - 1), "additives");
      rows.push(...page);
      if (page.length < PAGE) break;
    }
    additives = rows.map(toSummary);
    byLowerCode = new Map(additives.map((a) => [a.code.toLowerCase(), a]));
    details = new Map();
    meta = {
      totalCount: additives.length,
      detailedCount: additives.filter((a) => a.hasDetail).length,
      updatedLabel: metaRow.updated_label,
      sources: metaRow.sources,
      version: metaRow.version,
    };
    checkedAt = Date.now();
  };

  /** Reloads when the seeded version has changed. Failures keep the copy in memory. */
  const refreshIfStale = async () => {
    if (Date.now() - checkedAt < refreshSeconds * 1000) return;
    checkedAt = Date.now();
    try {
      const { data } = await anon().from("catalogue_meta").select("version").maybeSingle();
      if (data && data.version !== meta.version) await load();
    } catch (error) {
      console.error("Catalogue refresh failed, keeping the copy in memory:", error.message);
    }
  };

  await load();

  return {
    get meta() {
      return meta;
    },
    get additives() {
      return additives;
    },
    refreshIfStale,
    /** Finds a code whatever its letter case: "e160A" gives "E160a". */
    resolve: (code) => byLowerCode.get(String(code).toLowerCase())?.code ?? null,
    detail: async (code) => {
      if (details.has(code)) return details.get(code);
      const summary = byLowerCode.get(code.toLowerCase());
      if (!summary) return null;
      const row = check(await anon().from("additive_details").select("regulatory, sections, sources, footnote").eq("code", summary.code).maybeSingle(), `details for ${summary.code}`);
      const detail = { ...summary, regulatory: row?.regulatory ?? [], sections: row?.sections ?? [], sources: row?.sources ?? [], footnote: row?.footnote ?? "" };
      details.set(code, detail);
      return detail;
    },
  };
};
