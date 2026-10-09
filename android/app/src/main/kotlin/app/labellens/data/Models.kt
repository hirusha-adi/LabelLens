package app.labellens.data

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
    val updatedLabel: String = "",
    val syncedLabel: String = "",
    val sources: String = "",
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
) {
    val verdict get() = Verdict.of(status)
    val title get() = "$code · $name"

    /** "Colour · lemon yellow", or just the category when the descriptor adds nothing. */
    val categoryLine get() = if (descriptor.isBlank() || descriptor.equals(category, true)) category else "$category · $descriptor"
}

@Serializable
data class Catalogue(val meta: CatalogueMeta = CatalogueMeta(), val additives: List<Additive> = emptyList())

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

@Serializable
data class UserStats(val scans: Int = 0, val saved: Int = 0)

@Serializable
data class User(
    val name: String,
    val email: String,
    val initials: String = "",
    val plan: String = "free",
    val verified: Boolean = true,
    val passwordChangedLabel: String = "",
    val stats: UserStats = UserStats(),
)

@Serializable
data class AuthResponse(val token: String, val user: User)

@Serializable
data class Credentials(val email: String, val password: String, val name: String = "")

@Serializable
data class EmailBody(val email: String)

@Serializable
data class ForgotResponse(val ok: Boolean = true, val expiresLabel: String = "1 hour", val resendInSeconds: Int = 42)

@Serializable
data class ApiError(val error: String = "", val message: String = "")

@Serializable
data class Ok(val ok: Boolean = true)

@Serializable
data class Price(val price: String, val per: String, val was: String? = null, val note: String = "", val full: String = "")

@Serializable
data class Plan(
    val id: String,
    val name: String,
    val tagline: String = "",
    val recommended: Boolean = false,
    val features: List<String> = emptyList(),
    val yearly: Price? = null,
    val monthly: Price? = null,
) {
    fun price(yearlyPeriod: Boolean) = if (yearlyPeriod) yearly else monthly
}

@Serializable
data class CompareRow(val name: String, val free: String, val plus: String, val family: String)

@Serializable
data class Plans(
    val trialDays: Int = 7,
    val trialEndsLabel: String = "",
    val yearlySavePercent: Int = 0,
    val storeNote: String = "",
    val plans: List<Plan> = emptyList(),
    val comparison: List<CompareRow> = emptyList(),
    val comparisonNote: String = "",
)

@Serializable
data class Subscription(
    val planId: String,
    val title: String,
    val status: String = "Active",
    val renewsOn: String = "",
    val renewsShort: String = "",
    val price: String = "",
    val billedTo: String = "",
    val manageUrl: String = "",
    val note: String = "",
)

@Serializable
data class SubscriptionEnvelope(val subscription: Subscription? = null)

@Serializable
data class PurchaseBody(val planId: String, val period: String)

@Serializable
data class PurchaseResponse(val user: User, val subscription: Subscription? = null, val trialEndsLabel: String = "")

@Serializable
data class HistoryItem(
    val id: String,
    val day: String = "Today",
    val name: String = "Scanned label",
    val time: String = "",
    val verdict: String = "safe",
    val codes: List<String> = emptyList(),
)

@Serializable
data class HistoryEnvelope(val items: List<HistoryItem> = emptyList())

@Serializable
data class HistoryBody(val codes: List<String>, val verdict: String, val name: String? = null)

@Serializable
data class SavedEnvelope(val codes: List<String> = emptyList())

@Serializable
data class FamilyProfile(
    val id: String,
    val name: String,
    val role: String? = null,
    val initials: String? = null,
    val icon: String? = null,
    val tone: String = "primary",
    val short: String = "",
    val summary: String = "",
    val selected: Boolean = false,
)

@Serializable
data class Family(val max: Int = 5, val profiles: List<FamilyProfile> = emptyList(), val note: String = "")

@Serializable
data class FamilySelection(val selected: List<String>)

@Serializable
data class Region(val id: String, val label: String)

@Serializable
data class Regions(
    val language: String = "",
    val codeStyle: String = "",
    val selected: String = "",
    val regions: List<Region> = emptyList(),
)

@Serializable
data class RegionBody(val region: String)
