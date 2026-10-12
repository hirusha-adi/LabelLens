package com.hirusha.labellens.data

/** How one additive, or one allergen found in the label text, sits against a person's profile. */
data class Check(
    val verdict: Verdict,
    /** Short phrase such as "a colour you avoid". Null when nothing in the profile applies. */
    val reason: String? = null,
    /** Longer phrase used on the verdict card, such as "a colour you always avoid". */
    val longReason: String? = reason,
    /** Sentence for the additive page banner. */
    val explanation: String? = null,
    val icon: String = "person_alert",
)

data class Conflict(val title: String, val reason: String, val verdict: Verdict, val icon: String)

/** Personal checks. They run on the phone against the profile the user set up. */
object Verdicts {
    private fun stricter(v: Verdict) = when (v) {
        Verdict.SAFE -> Verdict.CAUTION
        else -> Verdict.AVOID
    }

    fun personal(additive: Additive, prefs: Preferences, options: PreferenceOptions): Check {
        if (additive.code in prefs.additives) {
            return Check(
                Verdict.AVOID, "an additive you avoid", "an additive you always avoid",
                "You added ${additive.code} to the additives you avoid.", "block",
            )
        }
        val found = mutableListOf<Check>()
        for (group in options.groups) {
            if (group.id !in additive.groups) continue
            when (prefs.groups[group.id]) {
                "avoid" -> found += Check(
                    Verdict.AVOID, group.shortReason, group.reason,
                    "You set ${group.label.lowercase()} to \"Always avoid\".", group.icon,
                )
                "warn" -> found += Check(
                    Verdict.CAUTION, group.shortReason, group.reason,
                    "You set ${group.label.lowercase()} to \"Warn me\".", group.icon,
                )
            }
        }
        val diet = options.diets.firstOrNull { it.id == prefs.diet }
        if (diet != null && diet.strictness == "animal") {
            val level = if (prefs.dietLevel == "warn") Verdict.CAUTION else Verdict.AVOID
            when (additive.origin) {
                "animal" -> found += Check(
                    level, "not ${diet.label.lowercase()}", "animal-derived, not ${diet.label.lowercase()}",
                    "It comes from an animal source and you follow a ${diet.label.lowercase()} diet.", diet.icon,
                )
                // Origin is not always certain, so this is never stronger than Caution.
                "either" -> found += Check(
                    Verdict.CAUTION, "may be animal-derived", "can be plant or animal based",
                    "It can be plant or animal based and you follow a ${diet.label.lowercase()} diet.", diet.icon,
                )
            }
        }
        for (sensitivity in options.sensitivities) {
            if (sensitivity.id in prefs.sensitivities && sensitivity.id in additive.sensitivities) {
                found += Check(
                    stricter(additive.verdict), sensitivity.reason, sensitivity.reason,
                    "You turned on stricter checks: ${sensitivity.label.lowercase()}.", sensitivity.icon,
                )
            }
        }
        val worst = found.maxByOrNull { it.verdict.ordinal }
        return when {
            worst == null -> Check(additive.verdict)
            worst.verdict.ordinal >= additive.verdict.ordinal -> worst
            else -> Check(additive.verdict)
        }
    }

    /** Allergens from the profile that appear in the text read from the label. */
    fun allergens(labelText: String, prefs: Preferences): List<Conflict> {
        if (labelText.isBlank()) return emptyList()
        val text = labelText.lowercase()
        return prefs.allergies.filter { allergy ->
            val name = allergy.name.lowercase()
            val stem = name.removeSuffix("s")
            Regex("\\b${Regex.escape(stem)}(s|es)?\\b").containsMatchIn(text) || text.contains(name)
        }.map {
            val avoid = it.level == "avoid"
            Conflict(
                it.name,
                if (avoid) "an allergen you always avoid" else "an allergen you asked to be warned about",
                if (avoid) Verdict.AVOID else Verdict.CAUTION,
                if (avoid) "block" else "notifications",
            )
        }
    }

    fun worst(verdicts: List<Verdict>): Verdict = verdicts.maxByOrNull { it.ordinal } ?: Verdict.SAFE

    /** One-line summary for the general (not personal) verdict. */
    fun generalSummary(additives: List<Additive>): String {
        val banned = additives.count { it.bannedIn.isNotEmpty() }
        val limited = additives.count { it.verdict == Verdict.CAUTION && it.bannedIn.isEmpty() }
        val parts = mutableListOf<String>()
        if (banned > 0) parts += if (banned == 1) "1 additive is banned in some countries." else "$banned additives are banned in some countries."
        if (limited > 0) parts += if (limited == 1) "1 has limited evidence." else "$limited have limited evidence."
        return if (parts.isEmpty()) "No concerns found in the additives we matched." else parts.joinToString(" ")
    }
}
