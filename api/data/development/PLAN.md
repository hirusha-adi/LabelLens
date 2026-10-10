# Revised delivery instructions, 2026-10-10

The user now requests every code in the entire dataset, with no review queue.
The primary output is dist/ins-full.json. Write researched batches there first,
then run scripts/full-split.py to synchronize individual files. The original
20 sourced records have already replaced the four stale aggregate records.

Choose the best supported facts. Retain differences between assessments with
their body and year; omit unsupported claims and document the research limits
in provenance files. No guesses, copied prose or universal dietary guarantees.
Changed flags are research decisions, not tasks handed back to the user.
Processed records are done; remaining records are todo. Read progress first.

Complete all 522 codes, including the 11 extras. Verify current EU and FDA
listings by exact compound identity and permitted function. Inventory presence
without an applicable FDA regulation is not sufficient for a US approval flag.
Future effective dates and proposals are distinct from current prohibitions.
Record actual readable URL loads, sources for each populated field, omissions
and old-to-new changes. Historical codes must not inherit related compounds'
approvals, origin, safety or ADI. Unknown empty master types need an established
food function; use unclassified only when none can be verified, with a gap.

The user requested the entire dataset in the main output. Perform the full coverage pass and keep the aggregate updated before writing derivatives. Commit the completed full pass locally.
Validation checks rich records, source files, full coverage and aggregate/single
consistency. Only the permitted empty status is exempt from empty-string checks.
Do not delete or rename existing additive files, touch unrelated changes or push.

At completion add extras to the master, sync all individual files, rebuild the
summary preserving its existing consumer contract, run full validation and write
the report. The earlier plan's aggregate deferral and needs-review workflow are
superseded by the user's latest instructions.

# Description expansion for every code

The next user request applies to all 522 records. Expand every overview within
the 2 to 6 sentence format, explaining the exact substance, food function,
composition and other characteristics established by the linked sources.
Improve companion uses, origin and other fields where specific evidence exists.
Keep uncertainty explicit for historical names, preparations and feed materials.

Retain authored prose and field URLs in
development/research/description-enrichment.json. Apply it with
scripts/enrich-descriptions.py --require-all to the primary aggregate first,
then synchronise individual files with scripts/full-split.py. Source corrections
to names, classifications and approval flags alongside the description changes.
Record readable URL loads, preserve unsupported-field gaps and keep all 522
progress entries done without introducing a human review queue.

At completion run the full validator and its contract tests, verify that
reapplying the catalog leaves records unchanged, update the report with field
coverage and cumulative corrections, and commit the entire expansion locally.

# Country bans and regulatory details for every code

Review all 522 records against explicit EU withdrawals and prohibitions, US
prohibited-food and cancelled-colour provisions, and their existing exact-identity
approval evidence. Add targeted primary evidence from other jurisdictions where
a specific ban or restriction can be established. Do not convert an absent
authorisation, an expired provisional listing for a different preparation, a
regional restriction or a future effective date into a current national ban.

Populate regulatory details in dist/ins-full.json first, preserving the required
country-list and approval-line formats. Record each country, use scope, year and
primary source in development/research/regulatory-details.json and field-level
provenance. List what remains unconfirmed and the limits of geographic coverage.
Sync individual files, update the report, validate all records and commit locally.
