package app.labellens.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.labellens.BuildConfig
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

data class SnackbarMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val id: Long = System.nanoTime(),
)

/** Outcome of a call the UI waits on. */
sealed interface Outcome {
    data object Success : Outcome
    data class Failure(val message: String, val code: String = "", val offline: Boolean = false) : Outcome
}

/**
 * App state and the actions that change it. One instance lives for the whole process.
 * The list, theme and scan counter are kept on the phone; everything else comes from the API.
 */
class Store(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val api = Api(BuildConfig.API_BASE_URL)
    val nav = Navigator()
    private val local = context.getSharedPreferences("labellens", Context.MODE_PRIVATE)
    private val catalogueFile = File(context.filesDir, "catalogue.json")
    private val details = mutableMapOf<String, AdditiveDetail>()

    var catalogue by mutableStateOf(emptyList<Additive>())
        private set
    private var index: Map<String, Additive> = emptyMap()
    var meta by mutableStateOf(CatalogueMeta())
        private set
    /** When the additive list was last confirmed with the API, in epoch milliseconds. 0 if never. */
    var lastSync by mutableStateOf(0L)
        private set
    var offline by mutableStateOf(false)
        private set
    var options by mutableStateOf(PreferenceOptions())
        private set
    var user by mutableStateOf<User?>(null)
        private set
    var preferences by mutableStateOf(Preferences())
        private set

    /** The profile being edited in the preferences flow, before it is saved. */
    var draft by mutableStateOf(Preferences())
    var myList by mutableStateOf(emptyList<String>())
        private set
    var saved by mutableStateOf(emptyList<String>())
        private set

    /** Null until the first load finishes. */
    var history by mutableStateOf<List<HistoryItem>?>(null)
        private set
    var plans by mutableStateOf<Plans?>(null)
        private set
    var subscription by mutableStateOf<Subscription?>(null)
        private set
    var family by mutableStateOf<Family?>(null)
        private set
    var regions by mutableStateOf<Regions?>(null)
        private set
    var themeMode by mutableStateOf("system")
        private set
    var shareStats by mutableStateOf(false)
    var tab by mutableStateOf(Tab.HOME)
    var scan by mutableStateOf<ScanResult?>(null)
    var scansUsed by mutableStateOf(0)
        private set
    var showScanSheet by mutableStateOf(false)
    var showUpgradeSheet by mutableStateOf(false)
    var snackbar by mutableStateOf<SnackbarMessage?>(null)
    var trialEndsLabel by mutableStateOf("")
        private set

    val isGuest get() = user == null
    val hasPlus get() = user?.plan == "plus" || user?.plan == "family"
    val isFamily get() = user?.plan == "family"
    val scansLeft get() = (FREE_SCANS_PER_DAY - scansUsed).coerceAtLeast(0)

    fun additive(code: String): Additive? = index[code]

    /** Personal check for Plus members, the general verdict for everyone else. */
    fun check(additive: Additive): Check =
        if (hasPlus) Verdicts.personal(additive, preferences, options) else Check(additive.verdict)

    // ---------- Start-up ----------

    fun start() {
        themeMode = local.getString(KEY_THEME, "system") ?: "system"
        myList = decodeList(local.getString(KEY_LIST, null))
        if (local.getString(KEY_SCAN_DATE, null) == today()) scansUsed = local.getInt(KEY_SCAN_COUNT, 0)
        local.getString(KEY_PREFERENCES, null)?.let { text ->
            runCatching { preferences = AppJson.decodeFromString<Preferences>(text) }
        }
        local.getString(KEY_USER, null)?.let { text ->
            runCatching { user = AppJson.decodeFromString<User>(text) }
        }
        api.token = local.getString(KEY_TOKEN, null)
        if (api.token == null) user = null
        if (catalogueFile.exists()) {
            runCatching { applyCatalogue(AppJson.decodeFromString<Catalogue>(catalogueFile.readText())) }
            lastSync = local.getLong(KEY_LAST_SYNC, 0L)
        }
        nav.reset(
            when {
                !local.getBoolean(KEY_ONBOARDED, false) -> Screen.Onboarding()
                !local.getBoolean(KEY_ENTERED, false) -> Screen.Welcome
                else -> Screen.Main
            },
        )
        refresh()
    }

    fun refresh() {
        scope.launch {
            loadCatalogue()
            if (offline) return@launch
            runCatching { options = api.get<PreferenceOptions>("/preferences/options") }
            runCatching { plans = api.get<Plans>("/plans") }
            runCatching { regions = api.get<Regions>("/regions").let { r -> r.copy(selected = local.getString(KEY_REGION, r.selected) ?: r.selected) } }
            if (api.token != null) loadAccount()
        }
    }

    private suspend fun loadCatalogue() {
        try {
            // Null means the copy on the phone is still current.
            val fresh = api.getIfChanged<Catalogue>("/additives", if (catalogue.isEmpty()) "" else meta.version)
            if (fresh != null) {
                applyCatalogue(fresh)
                details.clear()
                withContext(Dispatchers.IO) { catalogueFile.writeText(AppJson.encodeToString(fresh)) }
            }
            offline = false
            lastSync = System.currentTimeMillis()
            local.edit().putLong(KEY_LAST_SYNC, lastSync).apply()
        } catch (e: IOException) {
            offline = true
        } catch (e: Exception) {
            // The server answered, just not with something usable. Keep what is on the phone.
            offline = false
        }
    }

    /** "today", "yesterday" or "3 days ago", for the offline banner. */
    val lastSyncLabel: String
        get() {
            if (lastSync == 0L) return "never"
            val days = ((System.currentTimeMillis() - lastSync) / 86_400_000L).toInt()
            return when (days) {
                0 -> "today"
                1 -> "yesterday"
                else -> "$days days ago"
            }
        }

    private fun applyCatalogue(value: Catalogue) {
        catalogue = value.additives
        index = value.additives.associateBy { it.code }
        meta = value.meta
    }

    private suspend fun loadAccount() {
        try {
            applyUser(api.get<User>("/me"))
        } catch (e: ApiException) {
            // The session is gone on the server: logged out elsewhere, or the account was deleted.
            if (e.status == 401) clearSession()
            return
        } catch (e: IOException) {
            offline = true
            return
        }
        runCatching { setPreferencesLocal(api.get<Preferences>("/me/preferences")) }
        runCatching { saved = api.get<SavedEnvelope>("/saved").codes }
        runCatching { subscription = api.get<SubscriptionEnvelope>("/subscription").subscription }
        runCatching { family = api.get<Family>("/family") }
        loadHistory()
    }

    suspend fun detail(code: String): AdditiveDetail =
        details[code] ?: api.get<AdditiveDetail>("/additives/$code").also { details[code] = it }

    // ---------- Session ----------

    fun finishOnboarding() {
        local.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun continueAsGuest() {
        local.edit().putBoolean(KEY_ENTERED, true).apply()
        tab = Tab.HOME
        nav.reset(Screen.Main)
    }

    suspend fun login(email: String, password: String): Outcome =
        call { openSession(api.post<Credentials, AuthResponse>("/auth/login", Credentials(email, password))) }

    suspend fun loginWithGoogle(): Outcome = call { openSession(api.post<AuthResponse>("/auth/google")) }

    suspend fun signUp(name: String, email: String, password: String): Outcome =
        call { openSession(api.post<Credentials, AuthResponse>("/auth/signup", Credentials(email, password, name))) }

    suspend fun forgotPassword(email: String): Result<ForgotResponse> =
        runCatching { api.post<EmailBody, ForgotResponse>("/auth/forgot", EmailBody(email)) }

    suspend fun resendVerification(): Outcome = call { api.post<Ok>("/auth/resend-verification") }

    /** Asks the server whether the email has been confirmed yet. */
    suspend fun checkVerified(): Boolean {
        val fresh = runCatching { api.get<User>("/me") }.getOrNull() ?: return false
        applyUser(fresh)
        return fresh.verified
    }

    private suspend fun openSession(auth: AuthResponse) {
        api.token = auth.token
        local.edit().putString(KEY_TOKEN, auth.token).putBoolean(KEY_ENTERED, true).apply()
        applyUser(auth.user)
        history = null
        loadAccount()
    }

    private fun applyUser(value: User?) {
        user = value
        local.edit().putString(KEY_USER, value?.let { AppJson.encodeToString(it) }).apply()
    }

    private fun clearSession() {
        api.token = null
        applyUser(null)
        local.edit().remove(KEY_TOKEN).remove(KEY_PREFERENCES).apply()
        preferences = Preferences()
        saved = emptyList()
        history = null
        subscription = null
        family = null
    }

    fun logout() {
        scope.launch {
            runCatching { api.post<Ok>("/auth/logout") }
            clearSession()
            local.edit().putBoolean(KEY_ENTERED, false).apply()
            tab = Tab.HOME
            nav.reset(Screen.Welcome)
        }
    }

    suspend fun deleteAccount(): Outcome = call {
        api.delete<Ok>("/me")
        clearSession()
        local.edit().putBoolean(KEY_ENTERED, false).apply()
        tab = Tab.HOME
        nav.reset(Screen.Welcome)
    }

    // ---------- Preferences ----------

    private fun setPreferencesLocal(value: Preferences) {
        preferences = value
        local.edit().putString(KEY_PREFERENCES, AppJson.encodeToString(value)).apply()
    }

    fun savePreferences(value: Preferences) {
        setPreferencesLocal(value)
        if (api.token == null) return
        scope.launch {
            val outcome = call { api.put<Preferences, Preferences>("/me/preferences", value) }
            if (outcome is Outcome.Failure) snack("Saved on this phone. We'll sync your preferences when you're back online.")
        }
    }

    // ---------- My list (stays on the phone) ----------

    private fun setList(value: List<String>) {
        myList = value
        local.edit().putString(KEY_LIST, AppJson.encodeToString(ListSerializer(String.serializer()), value)).apply()
    }

    fun addToList(codes: Collection<String>) = setList((myList + codes).distinct())

    fun removeFromList(code: String) {
        val before = myList
        setList(myList - code)
        snack("Removed $code from your list", "Undo") { setList(before) }
    }

    fun clearList() {
        val before = myList
        setList(emptyList())
        snack("List cleared", "Undo") { setList(before) }
    }

    // ---------- Saved additives ----------

    fun toggleSaved(code: String) {
        val before = saved
        val removing = code in saved
        saved = if (removing) saved - code else listOf(code) + saved
        scope.launch {
            val outcome = call {
                saved = if (removing) api.delete<SavedEnvelope>("/saved/$code").codes else api.put<SavedEnvelope>("/saved/$code").codes
            }
            if (outcome is Outcome.Failure) {
                saved = before
                snack("Couldn't update Saved. Check your connection and try again.")
            }
        }
        if (removing) snack("Removed $code from Saved", "Undo") { toggleSaved(code) } else snack("Saved $code")
    }

    fun saveAll(codes: List<String>) {
        val fresh = codes.filter { it !in saved }
        if (fresh.isEmpty()) return snack("Already in Saved")
        saved = fresh + saved
        scope.launch { fresh.forEach { code -> call { api.put<SavedEnvelope>("/saved/$code") } } }
        snack(if (fresh.size == 1) "Saved ${fresh.first()}" else "Saved ${fresh.size} additives")
    }

    // ---------- Scanning ----------

    /** Opens the scan sheet, or the upgrade prompt when today's free scans are used up. */
    fun requestScan() {
        if (!hasPlus && scansLeft == 0) showUpgradeSheet = true else showScanSheet = true
    }

    fun recordScan(result: ScanResult) {
        scan = result
        if (!hasPlus) {
            scansUsed += 1
            local.edit().putString(KEY_SCAN_DATE, today()).putInt(KEY_SCAN_COUNT, scansUsed).apply()
            return
        }
        val codes = result.all.map { it.additive.code }
        val verdict = Verdicts.worst(
            result.all.map { check(it.additive).verdict } + Verdicts.allergens(result.text, preferences).map { it.verdict },
        )
        syncScan(HistoryBody(codes, verdict.name.lowercase()))
    }

    private fun syncScan(body: HistoryBody) {
        scope.launch {
            val outcome = call {
                val item = api.post<HistoryBody, HistoryItem>("/history", body)
                history = listOf(item) + history.orEmpty()
            }
            if (outcome is Outcome.Failure) {
                snack("Couldn't sync your history. We'll try again when you're back online.", "Retry") { syncScan(body) }
            }
        }
    }

    suspend fun loadHistory() {
        if (api.token == null) return
        val outcome = call { history = api.get<HistoryEnvelope>("/history").items }
        if (outcome is Outcome.Failure && history == null) history = emptyList()
    }

    fun clearHistory() {
        scope.launch {
            when (call { api.delete<Ok>("/history") }) {
                is Outcome.Success -> {
                    history = emptyList()
                    snack("Scan history cleared")
                }
                is Outcome.Failure -> snack("Couldn't clear your history. Check your connection and try again.")
            }
        }
    }

    // ---------- Plans ----------

    suspend fun purchase(planId: String, yearly: Boolean): Outcome = call {
        val response = api.post<PurchaseBody, PurchaseResponse>("/subscription/purchase", PurchaseBody(planId, if (yearly) "yearly" else "monthly"))
        applyUser(response.user)
        subscription = response.subscription
        trialEndsLabel = response.trialEndsLabel
        runCatching { family = api.get<Family>("/family") }
        loadHistory()
    }

    fun restorePurchases() {
        scope.launch {
            val outcome = call {
                val response = api.post<PurchaseResponse>("/subscription/restore")
                applyUser(response.user)
                subscription = response.subscription
            }
            snack(
                when {
                    outcome is Outcome.Failure -> "Couldn't reach Google Play. Try again when you're online."
                    hasPlus -> "Your purchases are up to date"
                    else -> "No purchases to restore"
                },
            )
        }
    }

    // ---------- Family, region, settings ----------

    fun selectFamily(selected: Set<String>) {
        family = family?.let { f -> f.copy(profiles = f.profiles.map { it.copy(selected = it.id in selected) }) }
        scope.launch { call { api.put<FamilySelection, Family>("/family/selection", FamilySelection(selected.toList())) } }
    }

    fun selectRegion(id: String) {
        regions = regions?.copy(selected = id)
        local.edit().putString(KEY_REGION, id).apply()
        if (api.token != null) scope.launch { call { api.put<RegionBody, Regions>("/me/region", RegionBody(id)) } }
    }

    fun chooseTheme(mode: String) {
        themeMode = mode
        local.edit().putString(KEY_THEME, mode).apply()
    }

    fun snack(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        snackbar = SnackbarMessage(text, actionLabel, onAction)
    }

    // ---------- Helpers ----------

    private suspend fun call(block: suspend () -> Unit): Outcome = try {
        block()
        offline = false
        Outcome.Success
    } catch (e: ApiException) {
        Outcome.Failure(e.message ?: "Something went wrong.", e.code)
    } catch (e: IOException) {
        offline = true
        Outcome.Failure("You're offline. Check your connection and try again.", offline = true)
    } catch (e: kotlinx.serialization.SerializationException) {
        Outcome.Failure("Something went wrong.")
    }

    private fun decodeList(text: String?): List<String> =
        text?.let { runCatching { AppJson.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() } ?: emptyList()

    private fun today() = LocalDate.now().toString()

    companion object {
        const val FREE_SCANS_PER_DAY = 3
        private const val KEY_THEME = "theme"
        private const val KEY_LIST = "list"
        private const val KEY_SCAN_DATE = "scanDate"
        private const val KEY_SCAN_COUNT = "scanCount"
        private const val KEY_PREFERENCES = "preferences"
        private const val KEY_USER = "user"
        private const val KEY_TOKEN = "token"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_ENTERED = "entered"
        private const val KEY_REGION = "region"
        private const val KEY_LAST_SYNC = "lastSync"
    }
}
