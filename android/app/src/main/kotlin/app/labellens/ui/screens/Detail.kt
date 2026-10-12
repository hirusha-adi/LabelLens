package app.labellens.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.labellens.data.Additive
import app.labellens.data.AdditiveDetail
import app.labellens.data.Screen
import app.labellens.data.Verdict
import app.labellens.ui.CodeBadge
import app.labellens.ui.EmptyState
import app.labellens.ui.FilledButton
import app.labellens.ui.Links
import app.labellens.ui.LoadingShape
import app.labellens.ui.LocalStore
import app.labellens.ui.OutlineButton
import app.labellens.ui.Page
import app.labellens.ui.PlainIconButton
import app.labellens.ui.ShapeHero
import app.labellens.ui.StatusPill
import app.labellens.ui.Sym
import app.labellens.ui.T
import app.labellens.ui.TopRow
import app.labellens.ui.bold
import app.labellens.ui.cs
import app.labellens.ui.of
import app.labellens.ui.openUrl
import app.labellens.ui.skeletonAlpha
import app.labellens.ui.statusColors
import app.labellens.ui.theme.ExpressiveShapes
import app.labellens.ui.theme.gab
import app.labellens.ui.theme.hg
import app.labellens.ui.theme.segShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private sealed interface DetailState {
    data object Loading : DetailState
    data object Failed : DetailState
    data class Ready(val detail: AdditiveDetail) : DetailState
}

/** C13 to C16: one additive, with what is known about it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(code: String) {
    val store = LocalStore.current
    val context = LocalContext.current
    val additive = store.additive(code)
    var state by remember { mutableStateOf<DetailState>(DetailState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(code, attempt) {
        state = DetailState.Loading
        state = try {
            store.detail(code)?.let { DetailState.Ready(it) } ?: DetailState.Failed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DetailState.Failed
        }
    }

    Page {
        val ready = state as? DetailState.Ready
        TopRow(onBack = { store.nav.pop() }, modifier = Modifier.height(64.dp)) {
            if (ready != null && additive != null) {
                val isSaved = code in store.saved
                PlainIconButton(
                    if (isSaved) "bookmark" else "bookmark_add", if (isSaved) "Remove from Saved" else "Save",
                    { store.toggleSaved(code) },
                    tint = if (isSaved) cs.primary else cs.onSurfaceVariant, fill = isSaved,
                )
                PlainIconButton("share", "Share", {
                    val text = "${additive.code} · ${additive.name}: ${additive.verdict.label}. ${additive.note}. From Label Lens."
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                })
            }
        }
        if (additive == null) {
            EmptyState(
                "We don't have $code", "This additive isn't in the list saved on this phone.", Modifier.padding(top = 56.dp),
                hero = { ShapeHero("search_off", ExpressiveShapes.Clover4) },
            )
            return@Page
        }
        val check = store.check(additive)
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CodeBadge(additive.code)
                if (state is DetailState.Loading) {
                    Box(Modifier.width(120.dp).height(28.dp).alpha(skeletonAlpha()).background(cs.surfaceContainerHigh, RoundedCornerShape(8.dp)))
                } else {
                    Row(
                        Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(cs.secondaryContainer).padding(start = 8.dp, end = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Sym(additive.categoryIcon, size = 16.dp, tint = cs.onSecondaryContainer)
                        T(additive.categoryLine, hg(700, 12), color = cs.onSecondaryContainer, maxLines = 1)
                    }
                    if (ready != null) StatusPill(check.verdict)
                }
            }
            T(additive.name, gab(900, 36, 44, -0.36))
            when {
                state is DetailState.Loading -> Box(Modifier.padding(top = 4.dp).fillMaxWidth(0.7f).height(16.dp).alpha(skeletonAlpha()).background(cs.surfaceContainerHigh, CircleShape))
                ready != null && additive.aliases.isNotEmpty() ->
                    T("Also called ${additive.aliases.joinToString(", ")}", hg(400, 15, 22), color = cs.onSurfaceVariant)
            }
        }
        when (val current = state) {
            DetailState.Loading -> DetailSkeleton()
            DetailState.Failed -> EmptyState(
                "Couldn't load the details",
                "Something went wrong reading this entry. Your list and scanning still work.",
                Modifier.padding(top = 56.dp),
                hero = { ShapeHero("sync_problem", ExpressiveShapes.Cookie9, size = 112.dp, iconSize = 46.dp, container = cs.errorContainer, content = cs.onErrorContainer) },
                actions = {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton(
                            "Report problem",
                            { context.openUrl("mailto:${Links.SUPPORT_EMAIL}?subject=${android.net.Uri.encode("Problem loading $code")}") },
                            height = 40.dp, style = hg(700, 14), borderColor = cs.outlineVariant,
                        )
                        FilledButton("Try again", { attempt++ }, icon = "refresh", height = 40.dp, style = hg(700, 14), horizontalPadding = 16.dp)
                    }
                },
            )
            is DetailState.Ready -> DetailBody(additive, current.detail)
        }
    }
}

@Composable
private fun DetailSkeleton() {
    val alpha = skeletonAlpha()
    val block = cs.surfaceContainerHigh
    Column(Modifier.alpha(alpha)) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp).fillMaxWidth().height(76.dp).background(block, RoundedCornerShape(20.dp)))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Column(
                Modifier.fillMaxWidth().background(cs.surfaceContainerLow, segShape(0, 3)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.fillMaxWidth(0.4f).height(20.dp).background(block, CircleShape))
                Box(Modifier.fillMaxWidth().height(14.dp).background(block, CircleShape))
                Box(Modifier.fillMaxWidth().height(14.dp).background(block, CircleShape))
                Box(Modifier.fillMaxWidth(0.6f).height(14.dp).background(block, CircleShape))
            }
            Box(Modifier.fillMaxWidth().height(56.dp).background(cs.surfaceContainerLow, segShape(1, 3)))
            Box(Modifier.fillMaxWidth().height(56.dp).background(cs.surfaceContainerLow, segShape(2, 3)))
        }
    }
    // The indicator only appears if loading takes longer than 400ms.
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(400)
        slow = true
    }
    if (slow) {
        Column(
            Modifier.fillMaxWidth().padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LoadingShape(48.dp, cs.primary)
            T("Loading details…", hg(500, 13), color = cs.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(additive: Additive, detail: AdditiveDetail) {
    val store = LocalStore.current
    val context = LocalContext.current
    val check = store.check(additive)

    when {
        // Nothing set up yet, so there is nothing personal to say.
        store.preferences.isEmpty -> Unit
        check.reason != null && check.verdict != Verdict.SAFE -> {
            val tone = statusColors.of(check.verdict)
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(tone.container).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Sym("person_alert", tint = tone.onContainer, fill = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T("Contains ${check.reason}", hg(700, 15, 20), color = tone.onContainer)
                    if (check.explanation != null) T(check.explanation, hg(400, 14, 20), color = tone.onContainer)
                }
            }
        }
        else -> Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(statusColors.safe.container).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym("verified_user", tint = statusColors.safe.onContainer, fill = true)
            T("No conflicts with your profile", hg(700, 15, 20), color = statusColors.safe.onContainer)
        }
    }

    if (detail.regulatory.isNotEmpty()) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            detail.regulatory.forEach { line ->
                val (icon, tint) = when (line.kind) {
                    "banned" -> "block" to statusColors.avoid.color
                    "approved" -> "check_circle" to statusColors.safe.color
                    else -> "info" to cs.onSurfaceVariant
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Sym(icon, size = 22.dp, tint = tint, fill = line.kind != "note")
                    T(bold(line.text), hg(500, 14, 20), Modifier.weight(1f))
                }
            }
        }
    }

    if (detail.sections.isEmpty()) {
        // About a third of the list has no written entry yet: only a name, category and approval status.
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerLow).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Sym("description", size = 22.dp, tint = cs.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T("No detailed entry yet", hg(700, 15, 20))
                T(
                    "We know what ${additive.name} is used for, but don't have side effects, origin or intake limits for it. The links below are a good place to look.",
                    hg(400, 14, 20), color = cs.onSurfaceVariant,
                )
            }
        }
    }

    var open by rememberSaveable { mutableStateOf(detail.sections.firstOrNull()?.title.orEmpty()) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        detail.sections.forEachIndexed { i, section ->
            val expanded = open == section.title
            Column(Modifier.fillMaxWidth().clip(segShape(i, detail.sections.size)).background(cs.surfaceContainerLow)) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.Button) { open = if (expanded) "" else section.title }
                        .padding(start = 20.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(section.title, gab(800, 18), Modifier.weight(1f))
                    if (section.summary != null) T(section.summary, hg(500, 14), color = cs.onSurfaceVariant, maxLines = 1)
                    Sym(if (expanded) "expand_less" else "expand_more", tint = cs.onSurfaceVariant)
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        section.paragraphs.forEach { T(it, hg(400, 15, 23)) }
                    }
                }
            }
        }
    }

    if (detail.sources.isNotEmpty()) {
        T("Learn more", hg(700, 14, 20), Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp), color = cs.onSurfaceVariant)
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            detail.sources.forEach { source ->
                Row(
                    Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).border(BorderStroke(1.dp, cs.outlineVariant), RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { context.openUrl(source.url) }.padding(start = 16.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(source.label, hg(600, 14), color = cs.primary)
                    Sym("open_in_new", size = 18.dp, tint = cs.primary)
                }
            }
        }
    }
    if (detail.footnote.isNotBlank()) {
        T(detail.footnote, hg(400, 12, 16), Modifier.padding(horizontal = 20.dp, vertical = 16.dp), color = cs.onSurfaceVariant)
    }
}
