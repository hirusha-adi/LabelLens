package app.labellens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import app.labellens.data.Screen
import app.labellens.data.Verdict
import app.labellens.ui.FilledButton
import app.labellens.ui.LocalStore
import app.labellens.ui.Page
import app.labellens.ui.StatusTile
import app.labellens.ui.Sym
import app.labellens.ui.T
import app.labellens.ui.TextAction
import app.labellens.ui.TonalIconButton
import app.labellens.ui.cs
import app.labellens.ui.theme.ExpressiveShapes
import app.labellens.ui.theme.gab
import app.labellens.ui.theme.hg
import app.labellens.ui.theme.segShape
import kotlinx.coroutines.launch

private data class OnboardingPage(val title: String, val text: String)

private val pages = listOf(
    OnboardingPage(
        "Know what you are about to eat",
        "Label Lens explains every additive on a food label, with the evidence and sources, so you can decide for yourself.",
    ),
    OnboardingPage(
        "Scan in seconds",
        "We find E-numbers and additive names, even when the print is small or the light is bad. You confirm what we found.",
    ),
    OnboardingPage(
        "Your photos stay on your phone",
        "Text is read on the device. Label photos are never uploaded, and the app works offline.",
    ),
)

/** A01 to A03. */
@Composable
fun OnboardingScreen(replay: Boolean) {
    val store = LocalStore.current
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val finish = {
        if (replay) {
            store.nav.pop()
        } else {
            // First run carries straight on into setting up allergies and diet, which can be skipped.
            store.finishOnboarding()
            store.draft = store.preferences
            store.nav.reset(Screen.Main)
            store.nav.push(Screen.PrefsStep(0))
        }
        Unit
    }
    Page(scroll = false) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pager.currentPage > 0) {
                TonalIconButton("arrow_back", "Back", { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } })
            } else if (replay) {
                TonalIconButton("close", "Close", { store.nav.pop() })
            }
            Spacer(Modifier.weight(1f))
            if (pager.currentPage < pages.lastIndex) TextAction("Skip", finish)
        }
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().height(400.dp).clip(RoundedCornerShape(48.dp))
                        .background(listOf(cs.primaryContainer, cs.secondaryContainer, cs.tertiaryContainer)[index]),
                ) {
                    when (index) {
                        0 -> WhatItDoes()
                        1 -> HowScanningWorks()
                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(220.dp).background(cs.tertiary, ExpressiveShapes.Clover4), contentAlignment = Alignment.Center) {
                                Sym("shield_lock", size = 96.dp, tint = cs.onTertiary, fill = true)
                            }
                        }
                    }
                }
                Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 32.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    T(pages[index].title, gab(900, 32, 40))
                    T(pages[index].text, hg(400, 16, 24), color = cs.onSurfaceVariant)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { i ->
                    val current = i == pager.currentPage
                    Box(Modifier.size(if (current) 24.dp else 8.dp, 8.dp).background(if (current) cs.primary else cs.outlineVariant, CircleShape))
                }
            }
            if (pager.currentPage < pages.lastIndex) {
                FilledButton("Next", { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, trailingIcon = "arrow_forward")
            } else {
                FilledButton(if (replay) "Done" else "Get started", finish)
            }
        }
    }
}

@Composable
private fun WhatItDoes() {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-30).dp).size(220.dp).background(cs.secondaryContainer, ExpressiveShapes.Burst12))
        Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 56.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val cards = listOf(
                Triple(Verdict.AVOID, "E102 · Tartrazine", "Banned in 4 countries"),
                Triple(Verdict.CAUTION, "E621 · MSG", "Limited evidence"),
                Triple(Verdict.SAFE, "E330 · Citric acid", "No concerns"),
            )
            cards.forEachIndexed { i, (verdict, title, note) ->
                val shape = segShape(i, cards.size, 24.dp, 6.dp)
                Row(
                    Modifier.padding(start = if (i == 1) 24.dp else 0.dp).fillMaxWidth().shadow(4.dp, shape).clip(shape)
                        .background(cs.background).padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusTile(verdict, size = 40.dp, radius = 14.dp, iconSize = 22.dp)
                    Column {
                        T(title, gab(800, 16, 20), color = cs.onSurface)
                        T(note, hg(500, 13), color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HowScanningWorks() {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        val steps = listOf("photo_camera" to "1 · Photograph the ingredients", "crop" to "2 · Crop to the list", "fact_check" to "3 · Confirm the matches")
        steps.forEachIndexed { i, (icon, text) ->
            Row(
                Modifier.padding(start = (28 * i).dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val last = i == steps.lastIndex
                Box(
                    Modifier.size(64.dp).background(if (last) cs.primary else cs.background, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) { Sym(icon, size = 32.dp, tint = if (last) cs.onPrimary else cs.primary) }
                T(text, gab(800, 18, 24), color = cs.onSecondaryContainer)
            }
        }
    }
}
