package com.hirusha.labellens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hirusha.labellens.data.Screen
import com.hirusha.labellens.ui.FilledButton
import com.hirusha.labellens.ui.GroupLabel
import com.hirusha.labellens.ui.LocalStore
import com.hirusha.labellens.ui.PlainIconButton
import com.hirusha.labellens.ui.SegList
import com.hirusha.labellens.ui.SegRow
import com.hirusha.labellens.ui.Sym
import com.hirusha.labellens.ui.T
import com.hirusha.labellens.ui.cs
import com.hirusha.labellens.ui.theme.ExpressiveShapes
import com.hirusha.labellens.ui.theme.gab
import com.hirusha.labellens.ui.theme.hg

/** The "you" tab: what the checks are set to, and the way in to change them. */
@Composable
fun ProfileTab() {
    val store = LocalStore.current
    val prefs = store.preferences
    val editPreferences = {
        store.draft = store.preferences
        store.nav.push(Screen.PrefsStep(0))
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            PlainIconButton("settings", "Settings", { store.nav.push(Screen.Settings) })
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(96.dp).background(cs.tertiaryContainer, ExpressiveShapes.Cookie9), contentAlignment = Alignment.Center) {
                Sym("tune", size = 40.dp, tint = cs.onTertiaryContainer)
            }
            T("Your checks", gab(900, 28, 34), align = TextAlign.Center)
            T(
                if (prefs.isEmpty) "Tell Label Lens about your allergies and diet, and every scan gets a verdict for you."
                else "Every scan and additive is checked against these.",
                hg(400, 15, 22), color = cs.onSurfaceVariant, align = TextAlign.Center,
            )
        }

        if (prefs.isEmpty) {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                FilledButton("Set up my checks", editPreferences, icon = "tune")
            }
        } else {
            val diet = store.options.diets.firstOrNull { it.id == prefs.diet && it.strictness != "none" }
            val groups = store.options.groups.filter { (prefs.groups[it.id] ?: "off") != "off" }
            val sensitivities = store.options.sensitivities.filter { it.id in prefs.sensitivities }
            val rows = listOfNotNull(
                diet?.let { Triple(it.icon, "Diet", it.label) },
                prefs.allergies.takeIf { it.isNotEmpty() }?.let { list -> Triple("block", "Allergies and intolerances", list.joinToString(", ") { it.name }) },
                (groups.map { it.label } + prefs.additives).takeIf { it.isNotEmpty() }?.let { Triple("palette", "Avoiding", it.joinToString(", ")) },
                sensitivities.takeIf { it.isNotEmpty() }?.let { list -> Triple("child_care", "Stricter for", list.joinToString(", ") { it.label }) },
            )
            SegList {
                rows.forEachIndexed { i, (icon, label, value) ->
                    SegRow(i, rows.size, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), onClick = editPreferences) {
                        Sym(icon, tint = cs.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            T(label, hg(700, 15, 20))
                            T(value, hg(400, 14, 20), color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                FilledButton("Change my checks", editPreferences, icon = "edit", height = 48.dp, style = hg(700, 15), horizontalPadding = 20.dp)
            }
        }

        GroupLabel("On this phone", Modifier.padding(top = 20.dp))
        val counts = listOf(
            Triple("shopping_basket", "My list", store.myList.size),
            Triple("history", "Scans in History", store.history.size),
            Triple("bookmark", "Saved additives", store.saved.size),
        )
        SegList {
            counts.forEachIndexed { i, (icon, label, count) ->
                SegRow(i, counts.size + 1, minHeight = 52.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    Sym(icon, size = 22.dp, tint = cs.onSurfaceVariant)
                    T(label, hg(700, 15), Modifier.weight(1f))
                    T("$count", hg(700, 15), color = cs.onSurfaceVariant)
                }
            }
            SegRow(counts.size, counts.size + 1, minHeight = 52.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), onClick = { store.nav.push(Screen.Settings) }) {
                Sym("settings", size = 22.dp, tint = cs.onSurfaceVariant)
                T("Settings and backup", hg(700, 15), Modifier.weight(1f))
                Sym("chevron_right", size = 22.dp, tint = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(FloatingNavClearance))
    }
}
