package app.labellens.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.labellens.data.Matcher
import app.labellens.data.Screen
import app.labellens.data.Verdict
import app.labellens.ui.EmptyState
import app.labellens.ui.FilledButton
import app.labellens.ui.GroupLabel
import app.labellens.ui.LoadingShape
import app.labellens.ui.LocalStore
import app.labellens.ui.PlainIconButton
import app.labellens.ui.PlusBadge
import app.labellens.ui.SegList
import app.labellens.ui.SegRow
import app.labellens.ui.ShapeHero
import app.labellens.ui.StatusPill
import app.labellens.ui.StatusTile
import app.labellens.ui.Sym
import app.labellens.ui.T
import app.labellens.ui.cs
import app.labellens.ui.of
import app.labellens.ui.statusColors
import app.labellens.ui.theme.ExpressiveShapes
import app.labellens.ui.theme.gab
import app.labellens.ui.theme.hg
import app.labellens.ui.theme.segShape

/** P10, and the same treatment for Saved: a Plus feature shown to free users and guests. */
@Composable
private fun LockedTab(title: String, heading: String, message: String) {
    val store = LocalStore.current
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 60.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(title, gab(900, 36, 44))
            PlusBadge()
        }
        Box {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).alpha(0.5f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(0.6f to 0.4f, 0.5f to 0.35f, 0.65f to 0.3f).forEachIndexed { i, (first, second) ->
                    Row(
                        Modifier.fillMaxWidth().height(76.dp).clip(segShape(i, 3)).background(cs.surfaceContainerHigh).padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(56.dp).background(cs.surfaceContainerHighest, RoundedCornerShape(16.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.fillMaxWidth(first).height(12.dp).background(cs.surfaceContainerHighest, CircleShape))
                            Box(Modifier.fillMaxWidth(second).height(10.dp).background(cs.surfaceContainerHighest, CircleShape))
                        }
                    }
                }
            }
            Column(
                Modifier.padding(start = 28.dp, end = 28.dp, top = 100.dp).fillMaxWidth().shadow(8.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(56.dp).background(cs.secondary, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    Sym("lock", size = 28.dp, tint = cs.onSecondary, fill = true)
                }
                T(heading, gab(800, 22, 28), align = TextAlign.Center)
                T(message, hg(400, 15, 22), color = cs.onSurfaceVariant, align = TextAlign.Center)
                FilledButton(
                    "Try Plus free", { store.nav.push(Screen.Trial) }, Modifier.padding(top = 4.dp), height = 48.dp,
                    container = cs.secondary, content = cs.onSecondary, style = hg(800, 15), horizontalPadding = 24.dp,
                )
            }
        }
        Spacer(Modifier.height(FloatingNavClearance))
    }
}

/** P08 to P10. */
@Composable
fun HistoryTab() {
    val store = LocalStore.current
    if (!store.hasPlus) {
        LockedTab("History", "Keep every scan", "History saves each product with its verdict. It's part of Plus.")
        return
    }
    LaunchedEffect(Unit) { store.loadHistory() }
    var filter by rememberSaveable { mutableStateOf("all") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val history = store.history

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (!history.isNullOrEmpty()) {
                PlainIconButton(if (searching) "close" else "search", if (searching) "Close search" else "Search history", {
                    searching = !searching
                    query = ""
                })
            }
        }
        T("History", gab(900, 36, 44), Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp))
        when {
            history == null -> Column(Modifier.fillMaxWidth().padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LoadingShape(48.dp, cs.primary)
                T("Loading your scans…", hg(500, 13), color = cs.onSurfaceVariant)
            }
            history.isEmpty() -> EmptyState(
                "No scans yet",
                "Every label you scan is saved here with its verdict, so you can check a product again without rescanning.",
                Modifier.padding(top = 68.dp),
                hero = { ShapeHero("history", ExpressiveShapes.Sunny8, size = 130.dp, iconSize = 52.dp) },
                actions = {
                    FilledButton("Scan a label", { store.requestScan() }, Modifier.padding(top = 8.dp), icon = "document_scanner", height = 48.dp, style = hg(700, 15), horizontalPadding = 20.dp)
                },
            )
            else -> {
                if (searching) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth().height(48.dp).clip(CircleShape)
                            .background(cs.surfaceContainerHigh).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Sym("search", size = 20.dp, tint = cs.onSurfaceVariant)
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) T("Search your scans", hg(500, 15), color = cs.onSurfaceVariant)
                            BasicTextField(query, { query = it }, Modifier.fillMaxWidth(), textStyle = hg(500, 15).copy(color = cs.onSurface), singleLine = true, cursorBrush = SolidColor(cs.primary))
                        }
                    }
                }
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "All", "avoid" to "Avoid", "caution" to "Caution", "safe" to "Safe").forEach { (id, label) ->
                        val selected = filter == id
                        val shape = RoundedCornerShape(8.dp)
                        Row(
                            Modifier.height(32.dp).clip(shape)
                                .then(if (selected) Modifier.background(cs.secondaryContainer) else Modifier.border(BorderStroke(1.dp, cs.outlineVariant), shape))
                                .clickable(role = Role.RadioButton) { filter = id }.padding(start = if (selected) 8.dp else 12.dp, end = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selected) Sym("check", size = 18.dp, tint = cs.onSecondaryContainer)
                            T(label, hg(if (selected) 700 else 600, 13), color = if (selected) cs.onSecondaryContainer else cs.onSurface)
                        }
                    }
                }
                val shown = history.filter { (filter == "all" || it.verdict == filter) && (query.isBlank() || it.name.contains(query.trim(), true)) }
                if (shown.isEmpty()) {
                    T("No scans match.", hg(400, 15, 22), Modifier.padding(horizontal = 20.dp, vertical = 24.dp), color = cs.onSurfaceVariant)
                }
                shown.groupBy { it.day }.forEach { (day, items) ->
                    GroupLabel(day)
                    SegList {
                        items.forEachIndexed { i, item ->
                            val verdict = Verdict.of(item.verdict)
                            SegRow(
                                i, items.size, gap = 12.dp, minHeight = 76.dp,
                                padding = PaddingValues(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                                onClick = {
                                    if (item.codes.isEmpty()) store.snack("No additives were found in this scan.") else {
                                        store.scan = Matcher.fromCodes(item.codes, store.catalogue)
                                        store.nav.push(Screen.Results)
                                    }
                                },
                            ) {
                                // Label photos never leave the phone, so History shows the verdict instead of a thumbnail.
                                Box(Modifier.size(56.dp).background(statusColors.of(verdict).container, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                    Sym("document_scanner", size = 26.dp, tint = statusColors.of(verdict).onContainer)
                                }
                                Column(Modifier.weight(1f)) {
                                    T(item.name, gab(800, 16, 22), maxLines = 1)
                                    val count = when (item.codes.size) {
                                        0 -> "no additives"
                                        1 -> "1 additive"
                                        else -> "${item.codes.size} additives"
                                    }
                                    T("${item.time} · $count", hg(400, 13, 18), color = cs.onSurfaceVariant, maxLines = 1)
                                }
                                StatusPill(verdict)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(FloatingNavClearance))
    }
}

/** P11. */
@Composable
fun SavedTab() {
    val store = LocalStore.current
    if (!store.hasPlus) {
        LockedTab("Saved", "Keep an eye on additives", "Save additives and we'll tell you if their status changes. It's part of Plus.")
        return
    }
    var sort by rememberSaveable { mutableStateOf("recent") }
    val items = store.saved.mapNotNull { store.additive(it) }.let { list ->
        when (sort) {
            "name" -> list.sortedBy { it.name }
            "status" -> list.sortedByDescending { store.check(it).verdict.ordinal }
            else -> list
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (items.size > 1) {
                PlainIconButton("sort", "Change sort order", {
                    sort = when (sort) {
                        "recent" -> "status"
                        "status" -> "name"
                        else -> "recent"
                    }
                    store.snack(
                        when (sort) {
                            "status" -> "Sorted by status"
                            "name" -> "Sorted by name"
                            else -> "Sorted by most recent"
                        },
                    )
                })
            }
        }
        T("Saved", gab(900, 36, 44), Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp))
        T(
            "Additives you're keeping an eye on. We'll tell you if their status changes.",
            hg(400, 15, 22), Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), color = cs.onSurfaceVariant,
        )
        if (items.isEmpty()) {
            EmptyState(
                "Nothing saved yet",
                "Tap the bookmark on any additive to keep it here.",
                Modifier.padding(top = 48.dp),
                hero = { ShapeHero("bookmark", ExpressiveShapes.Cookie9, size = 130.dp, iconSize = 52.dp) },
                actions = {
                    FilledButton("Search additives", { store.nav.push(Screen.Search) }, Modifier.padding(top = 8.dp), icon = "search", height = 48.dp, style = hg(700, 15), horizontalPadding = 20.dp)
                },
            )
        }
        SegList {
            items.forEachIndexed { i, additive ->
                SegRow(
                    i, items.size, minHeight = 72.dp,
                    padding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    onClick = { store.nav.push(Screen.Detail(additive.code)) },
                ) {
                    StatusTile(store.check(additive).verdict)
                    Column(Modifier.weight(1f)) {
                        T(additive.title, gab(800, 17, 24), maxLines = 2)
                        T(additive.categoryLine, hg(400, 14, 20), color = cs.onSurfaceVariant, maxLines = 1)
                    }
                    PlainIconButton("bookmark", "Remove from Saved", { store.toggleSaved(additive.code) }, tint = cs.primary, fill = true)
                }
            }
        }
        Spacer(Modifier.height(FloatingNavClearance))
    }
}
