# Food additive dataset session report

Checked: 2026-10-10. This is an incomplete migration checkpoint.

- Done: 1 (E120).
- Needs-review: 19.
- Todo: 502.
- Total: 522 distinct codes, including the 11 extras.
- First batch: E100 through E127 in natural code order, 20 records.
- Next todo: E128.

The batch has 20 rich files and 20 field-level source records. Six previously missing individual files were created. Unknown claims were omitted and documented as gaps. The 11 extras are tracked now and will be added to the master at final completion.

## Approval flags changed

These comparisons use the original master flags. All five changes are also applied to the master and require review. Removing an unverified flag does not assert a national ban.

| Code | Old | New | Reason and evidence |
| --- | --- | --- | --- |
| E100 | 'e u' | 'e' | US flag removed: the FDA provision covers turmeric oleoresin, but the exact purified E100 preparation was not confirmed. [FDA regulation](https://www.ecfr.gov/current/title-21/section-73.615), [EU specifications](https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=CELEX:32012R0231). |
| E100ii | 'e u' | 'u' | EU flag removed: the database identifies E100 curcumin; a distinct turmeric-powder colour listing was not confirmed. [EU database](https://ec.europa.eu/food/food-feed-portal/backend/api/policy-items/POL-FAD-IMPORT-2985), [FDA turmeric listing](https://www.ecfr.gov/current/title-21/section-73.600). |
| E101ii | 'e' | 'e u' | US nutrient use verified for the sodium phosphate. This is not US colour approval. [21 CFR 184.1697](https://www.ecfr.gov/current/title-21/section-184.1697). |
| E106 | '' | 'e u' | The compound matches E101(ii), while its repository filename is retained. US use is as a nutrient. [EU specifications](https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=CELEX:32012R0231), [EU riboflavin listing](https://ec.europa.eu/food/food-feed-portal/backend/api/policy-items/POL-FAD-IMPORT-2986), [FDA regulation](https://www.ecfr.gov/current/title-21/section-184.1697). |
| E121 | '' | 'u' | US listing verified for certain mature orange skins. The July 2026 revocation proposal is not treated as a final prohibition. [FDA regulation](https://www.ecfr.gov/current/title-21/section-74.302), [FDA inventory](https://hfpappexternal.fda.gov/scripts/fdcc/index.cfm?id=CitrusRed2&set=ColorAdditives). |

E127 retains `e u` for this check. FDA records a food-use termination date of 15 January 2027, so the future revocation is recorded separately and requires another check then. [FDA inventory](https://hfpappexternal.fda.gov/scripts/fdcc/index.cfm?id=FDCRed3&set=ColorAdditives).

## Bans and regulatory concerns

16 records use mdi-alert-circle. Each row states the documented concern; a historical assessment gap is not proof of harm at current food-use levels. Confirmed national food bans are recorded only for E123 and E125 in the United States.

| Code | Concern and source |
| --- | --- |
| E100 | EFSA identified possible ADI exceedance for some high-consuming children in 2014. [Source 1](https://efsa.onlinelibrary.wiley.com/doi/10.2903/j.efsa.2010.1679), [Source 2](https://efsa.onlinelibrary.wiley.com/doi/10.2903/j.efsa.2014.3876) |
| E102 | EFSA identified intolerance reactions, including within the ADI, in 2009. [Source 1](https://efsa.onlinelibrary.wiley.com/doi/abs/10.2903/j.efsa.2009.1331), [Source 2](https://apps.who.int/food-additives-contaminants-jecfa-database/Home/Chemical/2017) |
| E103 | JECFA allocated no ADI and records withdrawal of specifications. [Source 1](https://apps.who.int/food-additives-contaminants-jecfa-database/Home/Chemical/2359) |
| E104 | EFSA reduced the ADI in 2009; EU use levels were subsequently reduced. [Source 1](https://efsa.onlinelibrary.wiley.com/doi/10.2903/j.efsa.2009.1329), [Source 2](https://efsa.onlinelibrary.wiley.com/doi/10.2903/j.efsa.2015.4070), [Source 3](https://apps.who.int/food-additives-contaminants-jecfa-database/Home/Chemical/871), [Source 4](https://www.efsa.europa.eu/en/press/news/091112) |
| E105 | JECFA postponed its ADI decision and subsequently withdrew specifications. [Source 1](https://www.inchem.org/documents/jecfa/jeceval/jec_851.htm) |
| E107 | JECFA records no allocated ADI after the earlier temporary value and withdrawn specifications. [Source 1](https://apps.who.int/food-additives-contaminants-jecfa-database/Home/Chemical/3225) |
| E110 | EFSA imposed a temporary reduced ADI in 2009; the uncertainty was reassessed and the ADI revised in 2014. [Source 1](https://efsa.onlinelibrary.wiley.com/doi/10.2903/j.efsa.2014.3765), [Source 2](https://www.efsa.europa.eu/en/press/news/091112) |
| E111 | JECFA postponed the ADI decision and records withdrawal of specifications. [Source 1](https://www.inchem.org/documents/jecfa/jeceval/jec_1796.htm) |
| E120 | EFSA identified an allergy risk without a threshold dose in 2015. [Source 1](https://efsa.onlinelibrary.wiley.com/doi/10.2903/j.efsa.2015.4288) |
| E121 | JECFA rejected food-additive use in 1969; FDA proposed revocation for abandonment in 2026. [Source 1](https://www.inchem.org/documents/jecfa/jecmono/v46aje10.htm), [Source 2](https://www.fda.gov/news-events/press-announcements/fda-takes-further-steps-remove-outdated-authorizations-color-additives-food) |
| E122 | EFSA identified possible ADI exceedance in maximum-permitted-level exposure modelling in 2015. [Source 1](https://efsa.onlinelibrary.wiley.com/doi/10.2903/j.efsa.2015.4072), [Source 2](https://www.efsa.europa.eu/en/press/news/091112) |
| E123 | FDA prohibits US food use; EFSA lowered the ADI in 2010. [Source 1](https://www.ecfr.gov/current/title-21/section-81.30), [Source 2](https://efsa.onlinelibrary.wiley.com/doi/10.2903/j.efsa.2010.1649) |
| E124 | EFSA reduced the ADI in 2009; EU use levels were reduced from 2013. [Source 1](https://efsa.onlinelibrary.wiley.com/doi/10.2903/j.efsa.2009.1328), [Source 2](https://efsa.onlinelibrary.wiley.com/doi/10.2903/j.efsa.2015.4073), [Source 3](https://www.efsa.europa.eu/en/press/news/091112) |
| E125 | FDA prohibits US food use under the cancellation of certificates provision. [Source 1](https://www.ecfr.gov/current/title-21/section-81.30) |
| E126 | JECFA could not complete the safety evaluation and described sensitisation reports. [Source 1](https://apps.who.int/food-additives-contaminants-jecfa-database/Home/Chemical/3822), [Source 2](https://www.inchem.org/documents/jecfa/jecmono/v06je27.htm) |
| E127 | FDA ordered revocation of food authorization in 2025 because of the statutory animal-cancer provision. [Source 1](https://www.fda.gov/food/food-ingredients-packaging/fda-encourages-food-manufacturers-accelerate-phasing-out-use-fdc-red-no-3-foods-2027-deadline) |

E100ii, E101, E101ii and E106 use mdi-check-circle: no national ban or notable food-use safety concern was found in their reviewed sources. This describes the limited review, not absence of risk everywhere. Missing data and different assessment methods remain recorded as gaps.

The old E102 ban list for Austria, Germany, Norway and the United Arab Emirates was not confirmed and was removed. Its alleged 2013 EU genotoxicity note was also not retained without an applicable source.

## Needs-review list

| Code | What a person should check |
| --- | --- |
| E100 | Check whether the US turmeric-oleoresin listing covers the exact purified E100 preparation; the US flag was removed until that identity can be confirmed. |
| E100ii | Check INS 100(ii) powder versus EU E100 extract; the EU flag was removed because an exact colour listing was not verified. |
| E101 | Check the different JECFA group ADI and EFSA decision not to allocate one; verify manufacture and dietary suitability for commercial preparations. |
| E101ii | Check the sodium-salt identity, changed food approval flags and legacy code mapping; US nutrient use does not confer colour approval. EFSA and JECFA ADI conclusions differ. |
| E102 | Verify the removed Austria/Germany/Norway/United Arab Emirates ban list and the differing EFSA/JECFA ADIs. |
| E103 | Check historical alkannin/alkanet identity, withdrawn specifications and the missing modern food safety/manufacturing assessment. |
| E104 | Check differing EFSA and JECFA ADIs and missing commercial dietary suitability evidence. |
| E105 | Check the postponed JECFA evaluation and missing modern food-use safety and manufacturing evidence; do not confuse it with Yellow AB, CAS 85-84-7. |
| E106 | Check the sodium-salt identity, changed food approval flags and legacy code mapping; US nutrient use does not confer colour approval. EFSA and JECFA ADI conclusions differ. |
| E107 | Check why the temporary ADI was withdrawn and locate a current assessment before restoring any safety or dietary statements. |
| E110 | Verify dietary suitability of commercial preparations; the old universal religious and vegan guarantee lacks source support. |
| E111 | Check the postponed evaluation and missing modern manufacturing, dietary and food-use information. |
| E121 | Check whether the July 2026 proposed revocation has become final; the US flag reflects the currently loaded CFR and FDA inventory, despite JECFA rejection. |
| E122 | Verify manufacturing details and dietary suitability; no commercial preparation certification was established. |
| E123 | Check the differing EFSA and JECFA ADIs and commercial dietary suitability. |
| E124 | Check the differing EFSA and JECFA ADIs and commercial dietary suitability; do not transfer carmine properties. |
| E125 | Locate a compound-specific intake/safety assessment and manufacture/dietary evidence; keep the US food prohibition separate from nonfood uses. |
| E126 | Locate a modern assessment and manufacturing/dietary information; the historical JECFA evaluation was inadequate to establish an ADI. |
| E127 | Recheck the US flag on 15 January 2027 and confirm any intervening effective-date changes; verify commercial dietary suitability. |

## Name corrections

- E101ii: "Riboflavin-5'-Phosphate" to "Riboflavin-5'-phosphate sodium". The applicable EU specification and FDA nutrient listing identify the sodium salt.
- E103: "Alkannin[11]" to "Alkannin". Removed an embedded citation marker; WHO lists alkannin/alkanet.
- E101ii internal code: E101a to E101ii, matching its existing filename.

## Validation and data exceptions

- Validator written before migration edits; its 10 contract tests pass.
- Batch validation: 20 codes, zero errors.
- Full validation: 522 codes, 5,091 errors, exit 1. All failures are outside the researched batch. Detailed baseline and current logs are retained. The full dataset is not valid yet.
- 71 distinct cited URLs loaded readable content during this session. Receipts are in url-checks.json. A browser challenge or blank HTTP 202 was not counted as a successful content load.
- The instruction to reject every empty string conflicts with the allowed empty status. Only the additive status field is exempted.
- The initial data contained 55 internal codes missing E, rather than the stated 57. E101ii had a separate incorrect identifier. Remaining basic files will be fixed in later batches.
- Historical E identifiers, INS variants and nutrient listings require compound and function matching. A US nutrient authorization does not imply a US colour authorization.
- FDA effective dates and proposals must be distinguished from current bans. E121 and E127 require date-sensitive review.
- Differing EFSA and JECFA ADIs are shown explicitly, not silently reconciled. No ADI allocated is kept distinct from an ADI not specified.
- Universal vegan, vegetarian, halal and kosher guarantees in basic data were not confirmed. Verified plant or insect origins support only the explicitly stated dietary conclusions; other claims are omitted and logged.
- Some historical colours lack adequate modern evaluations. Their missing fields remain documented gaps rather than inferred from related dyes.
- The existing full-split.py writes singles from an aggregate; it does not rebuild the aggregate from singles. summary-fix.py drops type and status. Aggregate build updates, adding the extras and both final rebuilds are deferred until all 522 records are done or needs-review.

The batch is committed locally with its code range. No push is performed.
