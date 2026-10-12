package app.labellens.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.labellens.BuildConfig
import app.labellens.data.Screen
import app.labellens.data.Store
import app.labellens.ui.Choice
import app.labellens.ui.ChoiceGroup
import app.labellens.ui.FilledButton
import app.labellens.ui.LensMark
import app.labellens.ui.Links
import app.labellens.ui.LocalStore
import app.labellens.ui.Page
import app.labellens.ui.SegList
import app.labellens.ui.SegRow
import app.labellens.ui.Sym
import app.labellens.ui.T
import app.labellens.ui.TextAction
import app.labellens.ui.TopRow
import app.labellens.ui.cs
import app.labellens.ui.openUrl
import app.labellens.ui.theme.gab
import app.labellens.ui.theme.hg
import app.labellens.ui.theme.segShape
import java.time.LocalDate

private const val BACKUP_TYPE = "application/json"
private const val LARGEST_BACKUP = 5 * 1024 * 1024

private data class SettingRow(
    val icon: String,
    val label: String,
    val sub: String? = null,
    val danger: Boolean = false,
    val external: Boolean = false,
    val chevron: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
private fun SectionLabel(text: String) {
    T(text, hg(700, 13), Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp), color = cs.primary)
}

@Composable
private fun SettingRows(rows: List<SettingRow>, offset: Int = 0, total: Int = rows.size) {
    rows.forEachIndexed { i, row ->
        SegRow(offset + i, total, padding = PaddingValues(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), onClick = row.onClick) {
            Sym(row.icon, size = 22.dp, tint = if (row.danger) cs.error else cs.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                T(row.label, hg(700, 15, 20), color = if (row.danger) cs.error else cs.onSurface)
                if (row.sub != null) T(row.sub, hg(400, 13, 18), color = cs.onSurfaceVariant)
            }
            when {
                row.external -> Sym("open_in_new", size = 20.dp, tint = cs.onSurfaceVariant)
                row.chevron -> Sym("chevron_right", size = 22.dp, tint = cs.onSurfaceVariant)
            }
        }
    }
}

private fun writeBackup(context: Context, store: Store, uri: Uri): Boolean = try {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(store.exportData().toByteArray()) } != null
} catch (e: Exception) {
    false
}

/** Null when the file cannot be read or is too large to be one of ours. */
private fun readBackup(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val bytes = stream.readNBytes(LARGEST_BACKUP + 1)
        if (bytes.size > LARGEST_BACKUP) null else bytes.decodeToString()
    }
} catch (e: Exception) {
    null
}

/** P15 and P16, as one scrolling screen. */
@Composable
fun SettingsScreen() {
    val store = LocalStore.current
    val context = LocalContext.current
    // Holds the backup that was picked until the person confirms replacing what is on the phone.
    var pendingRestore by remember { mutableStateOf<String?>(null) }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_TYPE)) { uri ->
        if (uri != null) store.snack(if (writeBackup(context, store, uri)) "Backup saved" else "Couldn't save the backup there. Try another folder.")
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = readBackup(context, uri)
            if (text == null) store.snack("Couldn't read that file.") else pendingRestore = text
        }
    }
    val noFilesApp = { store.snack("No files app found on this phone.") }

    Page {
        TopRow(onBack = { store.nav.pop() })
        T("Settings", gab(900, 36, 44), Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 4.dp))

        SectionLabel("Checks")
        SegList {
            val prefs = store.preferences
            val diet = store.options.diets.firstOrNull { it.id == prefs.diet && it.strictness != "none" }?.label
            val avoids = prefs.groups.values.count { it != "off" } + prefs.additives.size
            val summary = listOfNotNull(
                diet,
                prefs.allergies.size.takeIf { it > 0 }?.let { if (it == 1) "1 allergy" else "$it allergies" },
                avoids.takeIf { it > 0 }?.let { if (it == 1) "1 avoid" else "$it avoids" },
            ).joinToString(" · ").ifBlank { "Not set up yet" }
            SettingRows(
                listOf(
                    SettingRow("tune", "My preferences", summary) {
                        store.draft = store.preferences
                        store.nav.push(Screen.PrefsStep(0))
                    },
                ),
            )
        }

        SectionLabel("Appearance")
        ChoiceGroup(
            listOf(Choice("light", "Light", "light_mode"), Choice("dark", "Dark", "dark_mode"), Choice("system", "System", "check")),
            store.themeMode, { store.chooseTheme(it) }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        )

        SectionLabel("Your data")
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 2.dp).fillMaxWidth().clip(segShape(0, 4)).background(cs.tertiaryContainer).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Sym("shield_lock", tint = cs.onTertiaryContainer, fill = true)
            Column(Modifier.weight(1f)) {
                T("What's yours stays on this phone", hg(700, 15, 20), color = cs.onTertiaryContainer)
                T(
                    "Your list, preferences, history and label photos are kept here and never sent anywhere. " +
                        "Additive information is fetched from the Label Lens server, which records your device model, " +
                        "your IP address and which additive was asked for. " +
                        "If Android backup is on, your data comes back when you reinstall or move to a new phone.",
                    hg(400, 14, 20), color = cs.onTertiaryContainer,
                )
            }
        }
        SegList {
            SettingRows(
                listOf(
                    SettingRow("download", "Back up to a file", "Save a copy you keep yourself") {
                        try {
                            saveBackup.launch("label-lens-backup-${LocalDate.now()}.json")
                        } catch (e: ActivityNotFoundException) {
                            noFilesApp()
                        }
                    },
                    SettingRow("restore", "Restore from a file", "Replace what's here with a backup") {
                        try {
                            openBackup.launch(arrayOf(BACKUP_TYPE, "text/plain", "application/octet-stream"))
                        } catch (e: ActivityNotFoundException) {
                            noFilesApp()
                        }
                    },
                    SettingRow("delete_sweep", "Clear scan history", danger = true, chevron = false) { store.clearHistory() },
                ),
                offset = 1, total = 4,
            )
        }

        SectionLabel("About")
        SegList {
            SettingRows(
                listOf(
                    SettingRow("lightbulb", "How it works") { store.nav.push(Screen.Onboarding(replay = true)) },
                    SettingRow("help", "Help and support", external = true) { context.openUrl(Links.HELP) },
                    SettingRow(
                        "database", "Data sources",
                        listOf(store.meta.sources, store.meta.updatedLabel.takeIf { it.isNotBlank() }?.let { "updated $it" }).filterNot { it.isNullOrBlank() }.joinToString(" · ").ifBlank { null },
                        external = true,
                    ) { context.openUrl(Links.HELP) },
                    SettingRow("description", "Terms", external = true) { context.openUrl(Links.TERMS) },
                    SettingRow("policy", "Privacy policy", external = true) { context.openUrl(Links.PRIVACY) },
                    SettingRow("license", "Open-source licences", external = true) { context.openUrl(Links.LICENCE) },
                ),
            )
        }
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LensMark(24.dp, stroke = cs.onSurfaceVariant, leaf = cs.onSurfaceVariant)
            T("Label Lens ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", hg(500, 13), color = cs.onSurfaceVariant)
        }
    }

    pendingRestore?.let { text ->
        RestoreDialog(
            onDismiss = { pendingRestore = null },
            onConfirm = {
                pendingRestore = null
                store.snack(if (store.importData(text)) "Backup restored" else "That file isn't a Label Lens backup.")
            },
        )
    }
}

@Composable
private fun RestoreDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Sym("restore", Modifier.align(Alignment.CenterHorizontally), size = 28.dp, tint = cs.primary)
            T("Restore this backup?", gab(700, 24, 32), Modifier.fillMaxWidth(), color = cs.onSurface, align = TextAlign.Center)
            T(
                "Your list, preferences, saved additives and scan history on this phone will be replaced with the ones in the file. This can't be undone.",
                hg(400, 14, 20), color = cs.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                TextAction("Cancel", onDismiss, height = 40.dp)
                FilledButton("Restore", onConfirm, height = 40.dp, style = hg(700, 14), horizontalPadding = 16.dp)
            }
        }
    }
}
