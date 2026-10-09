# Food additive research and migration plan

Checked: 2026-10-10. Read progress.json at the beginning of every continuation.

## Baseline

Read all four rich examples (E100, E101, E101ii, E102), four basic examples
(E104, E110, E338, E441), the 511-entry master and all three existing scripts.
There are 350 individual files, 172 missing master entries and 11 extras: 522
distinct codes. There are 55 files whose internal code lacks E. E101ii also
incorrectly identifies itself as E101a. Preserve filenames, including E100ii.
The current four rich files are structure examples, not verified evidence.

The master mixes historical E identifiers and INS variants. An INS identifier
does not establish EU approval, nor does authorization of a parent compound
establish authorization of a derivative. Resolve identity before legal flags.

## Validation first

scripts/validate.py is implemented before dataset edits. Default mode checks
all master codes and all existing individual files and exits nonzero on any
failure. During migration, --codes checks an explicit batch only; it cannot
certify the full dataset. Run both after each batch. Do not repair unfinished
records with placeholders merely to make full validation pass.

The prohibition on empty strings conflicts with the explicitly allowed empty
status. Exempt only that status value. Optional unknown fields are omitted.
Require approval sentences to agree with status, ban lines first, EU before
US, and regulatory notes last with a year. Also check field-level sources.
Validation cannot independently establish that a source supports a claim.

## Research and authoring

1. Work in natural code order, numeric portion then suffix, about 20 records
   per batch. Track all 522 codes now; defer adding extras to the master until
   every code is reviewed, as requested.
2. Load the EU food additives database and current EU law, FDA inventories
   and applicable Title 21 provisions. Check current changes and effective
   dates. Missing from a positive list is not a documented national ban.
   Empty status means neither flag has been verified, not a worldwide ban.
3. Read compound-specific EFSA/JECFA evaluations and specifications for ADI,
   identity, manufacture, permitted foods and adverse effects. Shared family
   assessments require an explicit identity match for each claim.
4. Read rather than paste. Write short original food-use descriptions. Keep
   nutritional requirements, supplement doses, medical uses, animal-feed
   worker hazards and acceptable daily intakes distinct.
5. Verify dietary origin. A plant or synthetic ingredient supports an origin
   description, not universal halal or kosher certification. Omit unsupported
   religious or manufacturing guarantees and explain this in gaps.
6. Omit unsupported fields, including optional interactions/precautions.
   Record specific unsuccessful checks in gaps. Preserve no unsupported old
   ban, intolerance or safety assertion. Document removal and source limits.
7. Keep at least two successfully loaded source URLs per record, correctly
   labeled. Record URL-check results separately in development/url-checks.json.
   Access failure is a gap, never evidence about the additive.
8. Write development/sources/CODE.json with checked date, field URLs, gaps and
   changes. Include a baseline status comparison even when the previous
   individual file had no status; compare legal flags to the master then.
9. Use alert icons for documented bans or regulator concerns. Check icons mean
   no such concern was found in the reviewed sources, not proven absence of
   all risks. Record the reviewed scope and why each icon was selected.
10. Mark needs-review for changed approval flags, conflicting sources,
    unconfirmed historical ban claims or unresolved compound identity. Done
    permits transparently omitted optional fields, not invented coverage.

## Batch checkpoints and final build

Update progress and REPORT.md after each batch. Run full and batch validation,
record expected full failures on untouched records, fix all batch errors, and
commit only dataset work locally with the code range in the commit message.
Do not stage unrelated changes elsewhere in the repository. Do not push.

At completion, add the 11 extras to the master, preserving their identities.
The existing full-split.py runs from aggregate to singles, not the reverse.
Add a validated singles-to-full build path before rebuilding; never run the
old splitter against the stale aggregate during migration. summary-fix.py
currently derives a two-field summary from the master. Review downstream
contracts before changing that summary shape. Rebuild both aggregates only
after all 522 codes are done or needs-review, then run full validation.

At each session end write counts, all changed flags and their sources, all
documented bans/concerns, one-line review tasks, exceptions and check results
to REPORT.md, and print the same summary. An incomplete session is a recorded
checkpoint, not a claim that the dataset is complete.
