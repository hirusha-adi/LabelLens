package com.hirusha.labellens.data

import kotlinx.serialization.Serializable

enum class Verdict(val label: String, val icon: String) {
    SAFE("Safe", "check_circle"),
    CAUTION("Caution", "warning"),
    AVOID("Avoid", "block");

    companion object {
        fun of(value: String) = when (value.lowercase()) {
            "avoid" -> AVOID
            "caution" -> CAUTION
            else -> SAFE
        }
    }
}

@Serializable
data class CatalogueMeta(
    val totalCount: Int = 0,
    /** How many additives have a full entry. The rest only have a name, category and approval status. */
    val detailedCount: Int = 0,
    val updatedLabel: String = "",
    val sources: String = "",
    val version: String = "",
)

@Serializable
data class Additive(
    val code: String,
    val name: String,
    val shortName: String = name,
    val category: String = "",
    val descriptor: String = "",
    val categoryIcon: String = "science",
    val status: String = "safe",
    val note: String = "",
    val aliases: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val sensitivities: List<String> = emptyList(),
    val origin: String = "synthetic",
    val bannedIn: List<String> = emptyList(),
    val hasDetail: Boolean = true,
) {
    val verdict get() = Verdict.of(status)
    val title get() = "$code · $name"

    /** "Colour · lemon yellow", or just the category when the descriptor adds nothing. */
    val categoryLine get() = if (descriptor.isBlank() || descriptor.equals(category, true)) category else "$category · $descriptor"
}

/** The contents of assets/catalogue.json, which ships inside the app. */
@Serializable
data class Catalogue(
    val meta: CatalogueMeta = CatalogueMeta(),
    val options: PreferenceOptions = PreferenceOptions(),
    val additives: List<Additive> = emptyList(),
)

@Serializable
data class Regulatory(val kind: String, val text: String)

@Serializable
data class DetailSection(val title: String, val summary: String? = null, val paragraphs: List<String> = emptyList())

@Serializable
data class SourceLink(val label: String, val url: String)

@Serializable
data class AdditiveDetail(
    val code: String,
    val regulatory: List<Regulatory> = emptyList(),
    val sections: List<DetailSection> = emptyList(),
    val sources: List<SourceLink> = emptyList(),
    val footnote: String = "",
)

@Serializable
data class Allergy(val name: String, val level: String = "avoid", val custom: Boolean = false)

@Serializable
data class Preferences(
    val allergies: List<Allergy> = emptyList(),
    val diet: String = "none",
    val dietLevel: String = "avoid",
    val groups: Map<String, String> = emptyMap(),
    val additives: List<String> = emptyList(),
    val sensitivities: List<String> = emptyList(),
) {
    val isEmpty get() = allergies.isEmpty() && diet == "none" && groups.values.none { it != "off" } &&
        additives.isEmpty() && sensitivities.isEmpty()
}

@Serializable
data class DietOption(val id: String, val label: String, val icon: String, val strictness: String = "none")

@Serializable
data class GroupOption(val id: String, val label: String, val icon: String, val reason: String = "", val shortReason: String = "")

@Serializable
data class SensitivityOption(val id: String, val label: String, val description: String = "", val icon: String, val reason: String = "")

@Serializable
data class PreferenceOptions(
    val allergens: List<String> = emptyList(),
    val diets: List<DietOption> = emptyList(),
    val groups: List<GroupOption> = emptyList(),
    val sensitivities: List<SensitivityOption> = emptyList(),
)

/** One past scan. Only the codes found are kept, never the photo or the label text. */
@Serializable
data class Scan(
    val id: String,
    val name: String = "Scanned label",
    /** When it was scanned, in epoch milliseconds. */
    val at: Long,
    val verdict: String = "safe",
    val codes: List<String> = emptyList(),
)

/**
 * Everything the person has set up or collected. Stored as one document so it can be
 * backed up, restored and moved to a file in one piece.
 */
@Serializable
data class UserData(
    val formatVersion: Int = 1,
    val onboarded: Boolean = false,
    val theme: String = "system",
    val preferences: Preferences = Preferences(),
    val list: List<String> = emptyList(),
    val saved: List<String> = emptyList(),
    val scans: List<Scan> = emptyList(),
)
