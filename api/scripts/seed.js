// Loads the additive catalogue into PostgreSQL:
//
//   pnpm seed
//
// Builds the catalogue from the files in data/ (see src/catalogue.js) and replaces
// what is in the database with it, in one transaction. Safe to run again.

import { fileURLToPath } from "node:url";
import { CATALOGUE_CHANNEL } from "../src/catalogue-cache.js";
import { loadCatalogue } from "../src/catalogue.js";
import { preferenceOptions } from "../src/config.js";
import { createPool, explainDbError } from "../src/db.js";

export const seed = async (pool, catalogue = loadCatalogue()) => {
  const items = catalogue.additives.map(({ code }) => catalogue.detail(code));
  const client = await pool.connect();
  try {
    await client.query("begin");
    await client.query("create temporary table incoming on commit drop as select item, ordinality::integer as sort_order from jsonb_array_elements($1::jsonb) with ordinality as t (item, ordinality)", [
      JSON.stringify(items),
    ]);
    // Anything that has left the dataset goes, and its details with it.
    const removed = await client.query("delete from additives a where not exists (select 1 from incoming i where i.item ->> 'code' = a.code)");
    await client.query(`
      insert into additives (code, sort_order, name, short_name, category, descriptor, category_icon, status, note,
                             aliases, groups, sensitivities, origin, banned_in, has_detail, updated_at)
      select item ->> 'code', sort_order, item ->> 'name', item ->> 'shortName', item ->> 'category',
             coalesce(item ->> 'descriptor', ''), coalesce(item ->> 'categoryIcon', 'science'), item ->> 'status', item ->> 'note',
             array(select jsonb_array_elements_text(coalesce(item -> 'aliases', '[]'))),
             array(select jsonb_array_elements_text(coalesce(item -> 'groups', '[]'))),
             array(select jsonb_array_elements_text(coalesce(item -> 'sensitivities', '[]'))),
             coalesce(item ->> 'origin', 'unknown'),
             array(select jsonb_array_elements_text(coalesce(item -> 'bannedIn', '[]'))),
             coalesce((item ->> 'hasDetail')::boolean, false), now()
      from incoming
      on conflict (code) do update set
        sort_order = excluded.sort_order, name = excluded.name, short_name = excluded.short_name,
        category = excluded.category, descriptor = excluded.descriptor, category_icon = excluded.category_icon,
        status = excluded.status, note = excluded.note, aliases = excluded.aliases, groups = excluded.groups,
        sensitivities = excluded.sensitivities, origin = excluded.origin, banned_in = excluded.banned_in,
        has_detail = excluded.has_detail, updated_at = excluded.updated_at`);
    await client.query(`
      insert into additive_details (code, regulatory, sections, sources, footnote)
      select item ->> 'code', coalesce(item -> 'regulatory', '[]'), coalesce(item -> 'sections', '[]'),
             coalesce(item -> 'sources', '[]'), coalesce(item ->> 'footnote', '')
      from incoming
      on conflict (code) do update set
        regulatory = excluded.regulatory, sections = excluded.sections, sources = excluded.sources, footnote = excluded.footnote`);
    await client.query(
      `insert into catalogue_meta (id, version, updated_label, sources, options, seeded_at) values (true, $1, $2, $3, $4::jsonb, now())
       on conflict (id) do update set version = excluded.version, updated_label = excluded.updated_label,
         sources = excluded.sources, options = excluded.options, seeded_at = excluded.seeded_at`,
      [catalogue.meta.version, catalogue.meta.updatedLabel, catalogue.meta.sources, JSON.stringify(preferenceOptions)],
    );
    // Running APIs are listening for this and reload as soon as the commit lands.
    await client.query("select pg_notify($1, $2)", [CATALOGUE_CHANNEL, catalogue.meta.version]);
    await client.query("commit");
    return { loaded: items.length, removed: removed.rowCount, version: catalogue.meta.version };
  } catch (error) {
    await client.query("rollback");
    throw error;
  } finally {
    client.release();
  }
};

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  let pool;
  try {
    pool = createPool();
    const { loaded, removed, version } = await seed(pool);
    console.log(`Seeded ${loaded} additives, removed ${removed}. Version ${version}.`);
  } catch (error) {
    console.error(`Seeding failed: ${explainDbError(error)}`);
    process.exitCode = 1;
  } finally {
    await pool?.end();
  }
}
