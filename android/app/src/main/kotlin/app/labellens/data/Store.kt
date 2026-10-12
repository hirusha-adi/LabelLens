package app.labellens.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.labellens.BuildConfig
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

data class SnackbarMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val id: Long = System.nanoTime(),
)

/**
 * App state and the actions that change it. One instance lives for the whole process.
 *
 * The additive catalogue comes from the Label Lens server and is kept on the phone
 * once fetched. A copy also ships inside the app (assets/catalogue.json and
 * details.json), so a first launch with no connection still works.
 *
 * Everything the person sets up or collects is one [UserData] document in the app's
 * private storage. It is never sent anywhere.
 *
 * That document survives app updates because updates never touch app data. It
 * survives a reinstall or a new phone through Android's own backup, which the
 * manifest opts into (see res/xml/backup_rules.xml), provided backup is switched on
 * for the device. For anyone who has it off, Settings can write the document to a
 * file and read it back.
 */
class Store(
    private val context: Context,
    /** Null keeps the app on the catalogue it already has. Tests use that. */
    private val api: Api? = Api(BuildConfig.API_BASE_URL),
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val nav = Navigator()
    private val storage = context.getSharedPreferences(USER_FILE, Context.MODE_PRIVATE)
    private val catalogueFile = File(context.filesDir, "catalogue.json")

    /** Bookkeeping for what has been downloaded. Not part of the backup: a restored phone just fetches again. */
    private val cache = context.getSharedPreferences("labellens_cache", Context.MODE_PRIVATE)

    var catalogue by mutableStateOf(emptyList<Additive>())
        private set
    private var index: Map<String, Additive> = emptyMap()
    var meta by mutableStateOf(CatalogueMeta())
        private set
    var options by mutableStateOf(PreferenceOptions())
        private set

    /** Pages fetched from the server this session. */
    private val fetchedDetails = mutableMapOf<String, AdditiveDetail>()
    private var bundledDetails: Map<String, AdditiveDetail>? = null
    private val detailsLock = Mutex()

    var preferences by mutableStateOf(Preferences())
        private set

    /** The profile being edited in the preferences flow, before it is saved. */
    var draft by mutableStateOf(Preferences())
    var myList by mutableStateOf(emptyList<String>())
        private set
    var saved by mutableStateOf(emptyList<String>())
        private set
    var history by mutableStateOf(emptyList<Scan>())
        private set
    var themeMode by mutableStateOf("system")
        private set
    private var onboarded = false

    var tab by mutableStateOf(Tab.HOME)
    var scan by mutableStateOf<ScanResult?>(null)
    var showScanSheet by mutableStateOf(false)
    var snackbar by mutableStateOf<SnackbarMessage?>(null)

    fun additive(code: String): Additive? = index[code]

    /** How this additive sits against the person's allergies, diet and things to avoid. */
    fun check(additive: Additive): Check = Verdicts.personal(additive, preferences, options)

    // ---------- Start-up ----------

    fun start() {
        // The newest list the phone has: the one last fetched, or failing that the one in the app.
        val fetched = runCatching { AppJson.decodeFromString<Catalogue>(catalogueFile.readText()) }.getOrNull()
        show(fetched?.takeIf { it.additives.isNotEmpty() } ?: context.assets.open(CATALOGUE_ASSET).bufferedReader().use { AppJson.decodeFromString<Catalogue>(it.readText()) })

        apply(read())
        nav.reset(if (onboarded) Screen.Main else Screen.Onboarding())
        refresh()
    }

    private fun show(value: Catalogue) {
        catalogue = value.additives
        index = value.additives.associateBy { it.code }
        meta = value.meta
        if (value.options.allergens.isNotEmpty()) options = value.options
    }

    /** Asks the server for a newer list. Quietly keeps the current one if there is none or no connection. */
    fun refresh() {
        val server = api ?: return
        scope.launch {
            try {
                // The ETag only counts while the file it describes is still there.
                val held = cache.getString(KEY_CATALOGUE_ETAG, null).takeIf { catalogueFile.exists() }
                val (fresh, etag) = server.catalogue(held) ?: return@launch
                if (fresh.additives.isEmpty()) return@launch
                show(fresh)
                fetchedDetails.clear()
                apply(current())
                withContext(Dispatchers.IO) { catalogueFile.writeText(AppJson.encodeToString(fresh)) }
                cache.edit().putString(KEY_CATALOGUE_ETAG, etag).apply()
            } catch (e: IOException) {
                // Offline, or the server is having a bad moment. The list on the phone stays.
            } catch (e: SerializationException) {
                // Not the shape this version understands. Same answer.
            }
        }
    }

    /**
     * The long-form page for one additive: from the server when it can be reached,
     * otherwise the copy that shipped with the app.
     */
    suspend fun detail(code: String): AdditiveDetail? {
        fetchedDetails[code]?.let { return it }
        if (api != null) {
            try {
                api.detail(code)?.let {
                    fetchedDetails[code] = it
                    return it
                }
            } catch (e: IOException) {
                // Fall through to the bundled copy.
            } catch (e: SerializationException) {
                // Fall through to the bundled copy.
            }
        }
        val bundled = bundledDetails ?: detailsLock.withLock {
            bundledDetails ?: withContext(Dispatchers.IO) {
                context.assets.open(DETAILS_ASSET).bufferedReader().use {
                    AppJson.decodeFromString(MapSerializer(String.serializer(), AdditiveDetail.serializer()), it.readText())
                }
            }.also { bundledDetails = it }
        }
        return bundled[code]
    }

    // ---------- The saved document ----------

    private fun read(): UserData {
        storage.getString(KEY_DATA, null)?.let { text ->
            runCatching { return AppJson.decodeFromString<UserData>(text) }
        }
        return readEarlierFormat()
    }

    /** Builds before 2.1 kept each setting under its own key in another file. Carries them over once. */
    private fun readEarlierFormat(): UserData {
        val old = context.getSharedPreferences("labellens", Context.MODE_PRIVATE)
        if (old.all.isEmpty()) return UserData()
        val strings = ListSerializer(String.serializer())
        return UserData(
            onboarded = old.getBoolean("onboarded", false),
            theme = old.getString("theme", "system") ?: "system",
            preferences = old.getString("preferences", null)?.let { runCatching { AppJson.decodeFromString<Preferences>(it) }.getOrNull() } ?: Preferences(),
            list = old.getString("list", null)?.let { runCatching { AppJson.decodeFromString(strings, it) }.getOrNull() } ?: emptyList(),
        ).also {
            storage.edit().putString(KEY_DATA, AppJson.encodeToString(it)).apply()
            old.edit().clear().apply()
        }
    }

    private fun apply(data: UserData) {
        onboarded = data.onboarded
        themeMode = data.theme
        preferences = data.preferences
        // Codes that are no longer in the catalogue are dropped rather than shown as blanks.
        myList = data.list.filter { it in index }
        saved = data.saved.filter { it in index }
        history = data.scans
    }

    private fun current() = UserData(
        onboarded = onboarded,
        theme = themeMode,
        preferences = preferences,
        list = myList,
        saved = saved,
        scans = history,
    )

    private fun persist() {
        storage.edit().putString(KEY_DATA, AppJson.encodeToString(current())).apply()
    }

    /** The whole document as text, for writing to a file the person chooses. */
    fun exportData(): String = Json(AppJson) { prettyPrint = true }.encodeToString(current())

    /** Replaces everything with the document in [text]. Returns false, changing nothing, if it is not one. */
    fun importData(text: String): Boolean {
        val data = runCatching { AppJson.decodeFromString<UserData>(text) }.getOrNull() ?: return false
        if (data.formatVersion > UserData().formatVersion) return false
        apply(data.copy(onboarded = true))
        persist()
        return true
    }

    // ---------- First run ----------

    fun finishOnboarding() {
        onboarded = true
        persist()
    }

    // ---------- Preferences ----------

    fun savePreferences(value: Preferences) {
        preferences = value
        persist()
    }

    fun chooseTheme(mode: String) {
        themeMode = mode
        persist()
    }

    // ---------- My list ----------

    private fun replaceList(value: List<String>) {
        myList = value
        persist()
    }

    fun addToList(codes: Collection<String>) = replaceList((myList + codes).distinct())

    fun removeFromList(code: String) {
        val before = myList
        replaceList(myList - code)
        snack("Removed $code from your list", "Undo") { replaceList(before) }
    }

    fun clearList() {
        val before = myList
        replaceList(emptyList())
        snack("List cleared", "Undo") { replaceList(before) }
    }

    // ---------- Saved additives ----------

    private fun replaceSaved(value: List<String>) {
        saved = value
        persist()
    }

    fun toggleSaved(code: String) {
        val before = saved
        if (code in saved) {
            replaceSaved(saved - code)
            snack("Removed $code from Saved", "Undo") { replaceSaved(before) }
        } else {
            replaceSaved(listOf(code) + saved)
            snack("Saved $code")
        }
    }

    fun saveAll(codes: List<String>) {
        val fresh = codes.filter { it !in saved }
        if (fresh.isEmpty()) return snack("Already in Saved")
        replaceSaved(fresh + saved)
        snack(if (fresh.size == 1) "Saved ${fresh.first()}" else "Saved ${fresh.size} additives")
    }

    // ---------- Scanning and history ----------

    fun requestScan() {
        showScanSheet = true
    }

    fun recordScan(result: ScanResult) {
        scan = result
        val verdict = Verdicts.worst(
            result.all.map { check(it.additive).verdict } + Verdicts.allergens(result.text, preferences).map { it.verdict },
        )
        val entry = Scan(
            id = "s${System.currentTimeMillis()}",
            at = System.currentTimeMillis(),
            verdict = verdict.name.lowercase(),
            codes = result.all.map { it.additive.code },
        )
        history = (listOf(entry) + history).take(HISTORY_LIMIT)
        persist()
    }

    fun clearHistory() {
        if (history.isEmpty()) return snack("There's no scan history to clear")
        val before = history
        history = emptyList()
        persist()
        snack("Scan history cleared", "Undo") {
            history = before
            persist()
        }
    }

    fun snack(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        snackbar = SnackbarMessage(text, actionLabel, onAction)
    }

    companion object {
        /** Named in res/xml/backup_rules.xml and data_extraction_rules.xml. Rename all three together. */
        const val USER_FILE = "labellens_user"
        private const val KEY_DATA = "data"
        private const val KEY_CATALOGUE_ETAG = "catalogueEtag"
        private const val CATALOGUE_ASSET = "catalogue.json"
        private const val DETAILS_ASSET = "details.json"
        private const val HISTORY_LIMIT = 500
    }
}
