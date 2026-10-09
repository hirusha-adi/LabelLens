package app.labellens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.labellens.BuildConfig
import app.labellens.data.Outcome
import app.labellens.data.Screen
import app.labellens.ui.CheckMark
import app.labellens.ui.Choice
import app.labellens.ui.ChoiceGroup
import app.labellens.ui.FilledButton
import app.labellens.ui.LensMark
import app.labellens.ui.Links
import app.labellens.ui.LocalStore
import app.labellens.ui.Page
import app.labellens.ui.RadioMark
import app.labellens.ui.SegList
import app.labellens.ui.SegRow
import app.labellens.ui.Sym
import app.labellens.ui.T
import app.labellens.ui.TextAction
import app.labellens.ui.ToggleSwitch
import app.labellens.ui.TopRow
import app.labellens.ui.bold
import app.labellens.ui.cs
import app.labellens.ui.openUrl
import app.labellens.ui.statusColors
import app.labellens.ui.theme.gab
import app.labellens.ui.theme.hg
import app.labellens.ui.theme.segShape
import kotlinx.coroutines.launch

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

/** P15 and P16, as one scrolling screen. */
@Composable
fun SettingsScreen() {
    val store = LocalStore.current
    val context = LocalContext.current
    val user = store.user
    var deleting by remember { mutableStateOf(false) }
    val later = { store.snack("This isn't available in this preview build.") }

    Page {
        TopRow(onBack = { store.nav.pop() })
        T("Settings", gab(900, 36, 44), Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 4.dp))

        if (user != null) {
            SectionLabel("Account")
            SegList {
                SettingRows(
                    listOf(
                        SettingRow("mail", "Change email", user.email, onClick = later),
                        SettingRow("key", "Change password", user.passwordChangedLabel.ifBlank { null }, onClick = later),
                        SettingRow("person_remove", "Delete account", "Permanently remove your data") { deleting = true },
                    ),
                )
            }
        }

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
            val region = store.regions?.let { r -> r.regions.firstOrNull { it.id == r.selected }?.label?.substringBefore(" ·") }
            SettingRows(
                listOfNotNull(
                    SettingRow("tune", "My preferences", summary) {
                        store.draft = store.preferences
                        store.nav.push(Screen.PrefsStep(0))
                    },
                    SettingRow("card_membership", "Subscription", store.subscription?.let { "${it.title.substringBefore(" ·")} · renews ${it.renewsShort}" } ?: "Free plan") {
                        store.nav.push(if (store.hasPlus) Screen.ManageSubscription else Screen.Plans)
                    },
                    SettingRow("public", "Language and region", listOfNotNull("English", region).joinToString(" · ")) { store.nav.push(Screen.RegionSettings) },
                    SettingRow("notifications", "Notifications", "Status changes, trial reminders", onClick = later),
                ),
            )
        }

        SectionLabel("Appearance")
        ChoiceGroup(
            listOf(Choice("light", "Light", "light_mode"), Choice("dark", "Dark", "dark_mode"), Choice("system", "System", "check")),
            store.themeMode, { store.chooseTheme(it) }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        )

        SectionLabel("Privacy and data")
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 2.dp).fillMaxWidth().clip(segShape(0, 4)).background(cs.tertiaryContainer).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Sym("shield_lock", tint = cs.onTertiaryContainer, fill = true)
            Column(Modifier.weight(1f)) {
                T("Labels are read on this phone", hg(700, 15, 20), color = cs.onTertiaryContainer)
                T(
                    "Photos never leave your device. We only sync your list, preferences and the text of scans in History.",
                    hg(400, 14, 20), color = cs.onTertiaryContainer,
                )
            }
        }
        SegList {
            SettingRows(listOf(SettingRow("download", "Download my data", onClick = later)), offset = 1, total = 4)
            SegRow(2, 4, padding = PaddingValues(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), onClick = { store.shareStats = !store.shareStats }) {
                Sym("analytics", size = 22.dp, tint = cs.onSurfaceVariant)
                T("Share anonymous usage stats", hg(700, 15), Modifier.weight(1f))
                ToggleSwitch(store.shareStats, { store.shareStats = it }, "Share anonymous usage stats")
            }
            SettingRows(
                listOf(
                    SettingRow("delete_sweep", "Clear scan history", danger = true, chevron = false) {
                        if (store.hasPlus) store.clearHistory() else store.snack("There's no scan history to clear.")
                    },
                ),
                offset = 3, total = 4,
            )
        }

        SectionLabel("About")
        SegList {
            SettingRows(
                listOf(
                    SettingRow("help", "Help and support") { context.openUrl(Links.HELP) },
                    SettingRow(
                        "database", "Data sources",
                        listOf(store.meta.sources, store.meta.updatedLabel.takeIf { it.isNotBlank() }?.let { "updated $it" }).filterNot { it.isNullOrBlank() }.joinToString(" · ").ifBlank { null },
                    ) { context.openUrl(Links.HELP) },
                    SettingRow("description", "Terms", external = true) { context.openUrl(Links.TERMS) },
                    SettingRow("policy", "Privacy policy", external = true) { context.openUrl(Links.PRIVACY) },
                    SettingRow("license", "Open-source licences") { context.openUrl(Links.LICENCE) },
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
    if (deleting) DeleteAccountDialog { deleting = false }
}

/** P18. */
@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit) {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    val stats = store.user?.stats
    var understood by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Sym("delete_forever", Modifier.align(Alignment.CenterHorizontally), size = 28.dp, tint = cs.error)
            T("Delete your account?", gab(700, 24, 32), Modifier.fillMaxWidth(), color = cs.onSurface, align = TextAlign.Center)
            T("This can't be undone. We'll permanently delete:", hg(400, 14, 20), color = cs.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Your profile and family profiles",
                    "Allergies, diet and preferences",
                    "${stats?.scans ?: 0} scans in History and ${stats?.saved ?: 0} saved additives",
                ).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Sym("remove", size = 20.dp, tint = cs.error)
                        T(line, hg(500, 14, 20), color = cs.onSurface)
                    }
                }
            }
            if (store.hasPlus) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(statusColors.caution.container).padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Sym("warning", size = 20.dp, tint = statusColors.caution.onContainer, fill = true)
                    T(
                        bold("Your Plus subscription is **not** cancelled automatically. Cancel it in Google Play first."),
                        hg(500, 13, 18), Modifier.weight(1f), color = statusColors.caution.onContainer,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Checkbox, enabled = !busy) { understood = !understood },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CheckMark(understood, color = cs.error, onColor = cs.onError) }
                T("I understand this is permanent", hg(500, 14, 20), color = cs.onSurface)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                TextAction("Keep account", onDismiss, height = 40.dp, enabled = !busy)
                FilledButton(
                    "Delete account",
                    {
                        busy = true
                        scope.launch {
                            val outcome = store.deleteAccount()
                            busy = false
                            if (outcome is Outcome.Failure) {
                                onDismiss()
                                store.snack("Couldn't delete your account. Check your connection and try again.")
                            } else {
                                store.snack("Your account has been deleted")
                            }
                        }
                    },
                    enabled = understood, loading = busy, height = 40.dp, style = hg(700, 14), horizontalPadding = 16.dp,
                    container = cs.error, content = cs.onError,
                )
            }
        }
    }
}

/** P17. */
@Composable
fun RegionScreen() {
    val store = LocalStore.current
    val regions = store.regions
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { if (regions == null) store.refresh() }
    Page {
        TopRow(onBack = { store.nav.pop() })
        T("Language and region", gab(900, 36, 44), Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 6.dp))
        T(
            "Your region decides which approvals and bans we show first. Other regions are still listed on every additive.",
            hg(400, 15, 22), Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp), color = cs.onSurfaceVariant,
        )
        if (regions == null) {
            T(
                if (store.offline) "Regions need a connection. Check your connection and try again." else "Loading regions…",
                hg(400, 15, 22), Modifier.padding(horizontal = 24.dp), color = cs.onSurfaceVariant,
            )
            return@Page
        }
        val context = LocalContext.current
        SegList {
            SegRow(0, 2, minHeight = 64.dp, padding = PaddingValues(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), onClick = {
                // Per-app language lives in the system settings.
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)),
                )
            }) {
                Sym("translate", size = 22.dp, tint = cs.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    T("App language", hg(700, 15, 20))
                    T(regions.language, hg(400, 13, 18), color = cs.onSurfaceVariant)
                }
                Sym("chevron_right", size = 22.dp, tint = cs.onSurfaceVariant)
            }
            SegRow(1, 2, minHeight = 64.dp, padding = PaddingValues(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)) {
                Sym("straighten", size = 22.dp, tint = cs.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    T("Codes shown as", hg(700, 15, 20))
                    T(regions.codeStyle, hg(400, 13, 18), color = cs.onSurfaceVariant)
                }
            }
        }
        T("Region", hg(700, 13), Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp), color = cs.primary)
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).fillMaxWidth().height(56.dp).clip(CircleShape)
                .background(cs.surfaceContainerHigh).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym("search", tint = cs.onSurfaceVariant)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) T("Search countries", hg(500, 16), color = cs.onSurfaceVariant)
                BasicTextField(query, { query = it }, Modifier.fillMaxWidth(), textStyle = hg(500, 16).copy(color = cs.onSurface), singleLine = true, cursorBrush = SolidColor(cs.primary))
            }
        }
        val shown = regions.regions.filter { query.isBlank() || it.label.contains(query.trim(), true) }
        if (shown.isEmpty()) {
            T("No regions match \"${query.trim()}\".", hg(400, 15, 22), Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = cs.onSurfaceVariant)
        }
        SegList {
            shown.forEachIndexed { i, region ->
                val selected = region.id == regions.selected
                SegRow(
                    i, shown.size, color = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
                    padding = PaddingValues(start = 16.dp, end = 8.dp), onClick = { store.selectRegion(region.id) },
                ) {
                    T(region.label, hg(700, 15), Modifier.weight(1f), color = if (selected) cs.onPrimaryContainer else cs.onSurface)
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { RadioMark(selected) }
                }
            }
        }
    }
}
