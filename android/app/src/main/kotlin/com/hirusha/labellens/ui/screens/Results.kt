package com.hirusha.labellens.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hirusha.labellens.data.Conflict
import com.hirusha.labellens.data.Match
import com.hirusha.labellens.data.ScanResult
import com.hirusha.labellens.data.Screen
import com.hirusha.labellens.data.Verdict
import com.hirusha.labellens.data.Verdicts
import com.hirusha.labellens.ui.BottomBar
import com.hirusha.labellens.ui.CheckMark
import com.hirusha.labellens.ui.EmptyState
import com.hirusha.labellens.ui.FilledButton
import com.hirusha.labellens.ui.LocalStore
import com.hirusha.labellens.ui.OutlineButton
import com.hirusha.labellens.ui.Page
import com.hirusha.labellens.ui.PlainIconButton
import com.hirusha.labellens.ui.SegList
import com.hirusha.labellens.ui.SegRow
import com.hirusha.labellens.ui.ShapeHero
import com.hirusha.labellens.ui.StatusPill
import com.hirusha.labellens.ui.Sym
import com.hirusha.labellens.ui.T
import com.hirusha.labellens.ui.TextAction
import com.hirusha.labellens.ui.TonalIconButton
import com.hirusha.labellens.ui.bold
import com.hirusha.labellens.ui.cs
import com.hirusha.labellens.ui.of
import com.hirusha.labellens.ui.statusColors
import com.hirusha.labellens.ui.theme.ExpressiveShapes
import com.hirusha.labellens.ui.theme.gab
import com.hirusha.labellens.ui.theme.hg
import com.hirusha.labellens.ui.theme.segShape

/** C09 to C12: what a scan found, with the verdict and the matches to confirm. */
@Composable
fun ResultsScreen() {
    val store = LocalStore.current
    val result = store.scan
    if (result == null) {
        Page { ResultsTopBar("Scan result", scrolled = false, verdict = null) }
        return
    }
    if (result.all.isEmpty()) {
        NoMatches()
        return
    }

    val all = result.all
    var selected by rememberSaveable { mutableStateOf(all.map { it.additive.code }) }
    var textOpen by rememberSaveable { mutableStateOf(true) }
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 420 } }

    val checks = remember(result, store.preferences) { all.associate { it.additive.code to store.check(it.additive) } }
    val allergens = remember(result, store.preferences) { Verdicts.allergens(result.text, store.preferences) }
    // Until allergies, a diet or things to avoid are set, the verdict is the general one.
    val personal = !store.preferences.isEmpty
    val conflicts = allergens + all.mapNotNull { match ->
        val check = checks.getValue(match.additive.code)
        if (check.reason == null || check.verdict == Verdict.SAFE) null
        else Conflict("${match.additive.code} ${match.additive.name}", check.longReason ?: check.reason, check.verdict, check.icon)
    }.sortedByDescending { it.verdict.ordinal }
    val verdict = Verdicts.worst(checks.values.map { it.verdict } + allergens.map { it.verdict })

    Page(
        scroll = false,
        bottomBar = {
            BottomBar {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).border(BorderStroke(1.dp, cs.outlineVariant), CircleShape)
                            .clickable(role = Role.Button) {
                                store.nav.popTo(Screen.Main)
                                store.requestScan()
                            }.semantics { contentDescription = "Scan another label" },
                        contentAlignment = Alignment.Center,
                    ) { Sym("document_scanner") }
                    FilledButton(
                        "Add ${selected.size} to my list",
                        onClick = {
                            store.addToList(selected)
                            store.tab = com.hirusha.labellens.data.Tab.HOME
                            store.nav.popTo(Screen.Main)
                        },
                        Modifier.weight(1f),
                        enabled = selected.isNotEmpty(),
                    )
                }
            }
        },
    ) {
        ResultsTopBar("Scan result", scrolled, verdict) {
            PlainIconButton("bookmark_add", "Save these additives", { store.saveAll(selected) })
        }
        Column(Modifier.weight(1f).verticalScroll(scroll)) {
            VerdictCard(verdict, personal = personal, conflicts = conflicts, summary = Verdicts.generalSummary(all.map { it.additive }))
            T(
                if (all.size == 1) "1 possible additive found" else "${all.size} possible additives found",
                gab(800, 22, 28), Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            )
            MatchGroup("Matched by E-number", result.codeMatches, result, selected, checks.mapValues { it.value.verdict }, pills = true) { selected = it }
            MatchGroup("Matched by name", result.nameMatches, result, selected, checks.mapValues { it.value.verdict }, pills = false) { selected = it }
            if (result.text.isNotBlank()) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(cs.surfaceContainerLow).border(BorderStroke(1.dp, cs.outlineVariant), RoundedCornerShape(28.dp)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clickable { textOpen = !textOpen }.padding(start = 20.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T("Extracted text", hg(700, 16), Modifier.weight(1f))
                        Sym(if (textOpen) "expand_less" else "expand_more", tint = cs.onSurfaceVariant)
                    }
                    AnimatedVisibility(textOpen) {
                        val mark = SpanStyle(background = cs.primaryContainer, color = cs.onPrimaryContainer)
                        val annotated = buildAnnotatedString {
                            var cursor = 0
                            for (range in result.highlights) {
                                if (range.first > cursor) append(result.text.substring(cursor, range.first))
                                withStyle(mark) { append(result.text.substring(range.first, range.last + 1)) }
                                cursor = range.last + 1
                            }
                            if (cursor < result.text.length) append(result.text.substring(cursor))
                        }
                        T(annotated, hg(400, 14, 21), Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp), color = cs.onSurfaceVariant)
                    }
                }
            }
            if (result.otherNumbers.isNotEmpty()) {
                T(
                    "Other numbers on the label: ${result.otherNumbers.joinToString(", ")}. Not additive codes.",
                    hg(500, 13, 18), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ResultsTopBar(title: String, scrolled: Boolean, verdict: Verdict?, actions: @Composable () -> Unit = {}) {
    val store = LocalStore.current
    Row(
        Modifier.fillMaxWidth().background(if (scrolled) cs.surfaceContainer else cs.background).height(64.dp).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (scrolled) PlainIconButton("arrow_back", "Back", { store.nav.pop() }, tint = cs.onSurface)
        else TonalIconButton("arrow_back", "Back", { store.nav.pop() })
        T(title, gab(700, 22, 28), Modifier.weight(1f).padding(start = 8.dp))
        if (scrolled && verdict != null) StatusPill(verdict, Modifier.padding(end = 8.dp)) else actions()
    }
}

@Composable
private fun VerdictCard(verdict: Verdict, personal: Boolean, conflicts: List<Conflict>, summary: String) {
    val store = LocalStore.current
    val colors = statusColors.of(verdict)
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp))
            .background(cs.surfaceContainerHigh).padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val (shape, icon) = when (verdict) {
            Verdict.AVOID -> ExpressiveShapes.Sunny8 to "block"
            Verdict.CAUTION -> ExpressiveShapes.Clover4 to "priority_high"
            Verdict.SAFE -> ExpressiveShapes.Cookie9 to "check"
        }
        ShapeHero(icon, shape, size = 96.dp, iconSize = 46.dp, container = colors.color, content = colors.onColor, fill = true)
        T(if (personal) "VERDICT FOR YOU" else "GENERAL VERDICT", hg(700, 12, 16, 0.96), color = cs.onSurfaceVariant)
        T(verdict.label, gab(900, 40, 44), color = colors.color)
        T(
            when {
                !personal -> summary
                conflicts.isEmpty() -> "Nothing here conflicts with your profile"
                conflicts.size == 1 -> "1 ingredient conflicts with your profile"
                else -> "${conflicts.size} ingredients conflict with your profile"
            },
            hg(500, 15, 22), color = cs.onSurfaceVariant, align = TextAlign.Center,
        )
        if (personal && conflicts.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                conflicts.forEachIndexed { i, conflict ->
                    val tone = statusColors.of(conflict.verdict)
                    Row(
                        Modifier.fillMaxWidth().clip(segShape(i, conflicts.size, 20.dp, 6.dp)).background(tone.container).padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Sym(conflict.icon, size = 22.dp, tint = tone.onContainer)
                        T(bold("**${conflict.title}**: ${conflict.reason}"), hg(500, 15, 20), Modifier.weight(1f), color = tone.onContainer)
                    }
                }
            }
        }
        if (!personal) {
            Row(
                Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.secondaryContainer).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym("tune", tint = cs.onSecondaryContainer)
                Column(Modifier.weight(1f)) {
                    T("Is it OK for you?", hg(700, 15, 20), color = cs.onSecondaryContainer)
                    T("Add your allergies and diet to get a verdict for you.", hg(400, 14, 20), color = cs.onSecondaryContainer)
                }
                FilledButton(
                    "Set up", {
                        store.draft = store.preferences
                        store.nav.push(Screen.PrefsStep(0))
                    }, height = 40.dp, style = hg(700, 14),
                    container = cs.secondary, content = cs.onSecondary, horizontalPadding = 14.dp,
                )
            }
        }
    }
}

@Composable
private fun MatchGroup(
    title: String,
    matches: List<Match>,
    result: ScanResult,
    selected: List<String>,
    verdicts: Map<String, Verdict>,
    pills: Boolean,
    onChange: (List<String>) -> Unit,
) {
    if (matches.isEmpty()) return
    val store = LocalStore.current
    val codes = matches.map { it.additive.code }
    val allSelected = selected.containsAll(codes)
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(if (result.fromHistory) "Additives in this scan" else title, hg(700, 14, 20), Modifier.weight(1f), color = cs.onSurfaceVariant)
        TextAction(if (allSelected) "Select none" else "Select all", { onChange(if (allSelected) selected - codes.toSet() else (selected + codes).distinct()) })
    }
    SegList {
        matches.forEachIndexed { i, match ->
            val additive = match.additive
            val isSelected = additive.code in selected
            val verdict = verdicts[additive.code] ?: additive.verdict
            SegRow(
                i, matches.size, minHeight = 68.dp, gap = 6.dp,
                padding = PaddingValues(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                onClick = { store.nav.push(Screen.Detail(additive.code)) },
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Checkbox) {
                        onChange(if (isSelected) selected - additive.code else selected + additive.code)
                    }.semantics { contentDescription = if (isSelected) "Selected" else "Not selected" },
                    contentAlignment = Alignment.Center,
                ) { CheckMark(isSelected) }
                Column(Modifier.weight(1f)) {
                    T("${additive.code} · ${additive.name}", gab(800, 16, 22), maxLines = 2)
                    T(
                        when {
                            result.fromHistory -> additive.categoryLine
                            match.percent != null -> "“${match.readAs}” · ${match.percent}% match"
                            else -> "Read as “${match.readAs}”"
                        },
                        hg(400, 14, 20), color = cs.onSurfaceVariant, maxLines = 2,
                    )
                }
                if (pills) StatusPill(verdict) else Sym(verdict.icon, tint = statusColors.of(verdict).color, fill = true)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun NoMatches() {
    val store = LocalStore.current
    Page(
        bottomBar = {
            BottomBar(color = cs.background) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("Scan again", {
                        store.nav.popTo(Screen.Main)
                        store.requestScan()
                    }, Modifier.weight(1f), borderColor = cs.outlineVariant)
                    FilledButton("Search manually", { store.nav.replace(Screen.Search) }, Modifier.weight(1f), horizontalPadding = 8.dp)
                }
            }
        },
    ) {
        ResultsTopBar("Scan result", scrolled = false, verdict = null)
        EmptyState(
            "No additives found",
            "We read the label but didn't recognise any additive codes or names. That can mean there aren't any, or the photo was blurry.",
            Modifier.padding(top = 72.dp),
            hero = { ShapeHero("document_search", ExpressiveShapes.Sunny8) },
        )
        val tips = listOf("light_mode" to "Try brighter light, and avoid glare", "crop" to "Crop only the ingredients list")
        SegList(Modifier.padding(top = 24.dp)) {
            tips.forEachIndexed { i, (icon, text) ->
                SegRow(i, tips.size, gap = 12.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), minHeight = 0.dp) {
                    Sym(icon, size = 22.dp, tint = cs.primary)
                    T(text, hg(500, 14, 20))
                }
            }
        }
    }
}
