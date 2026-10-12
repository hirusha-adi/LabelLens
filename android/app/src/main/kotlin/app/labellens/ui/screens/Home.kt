package app.labellens.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.labellens.data.Screen
import app.labellens.data.Store
import app.labellens.data.Tab
import app.labellens.ui.GroupLabel
import app.labellens.ui.LensMark
import app.labellens.ui.theme.LocalInversePrimaryContent
import app.labellens.ui.Links
import app.labellens.ui.LocalStore
import app.labellens.ui.SegList
import app.labellens.ui.SegRow
import app.labellens.ui.StatusTile
import app.labellens.ui.Sym
import app.labellens.ui.T
import app.labellens.ui.TextAction
import app.labellens.ui.cs
import app.labellens.ui.openUrl
import app.labellens.ui.theme.ExpressiveShapes
import app.labellens.ui.theme.gab
import app.labellens.ui.theme.hg

/** Home, History, Saved and Profile, with the floating toolbar and Scan button over them. */
@Composable
fun MainScreen() {
    val store = LocalStore.current
    BackHandler(enabled = store.tab != Tab.HOME) { store.tab = Tab.HOME }
    Box(Modifier.fillMaxSize().background(cs.background)) {
        when (store.tab) {
            Tab.HOME -> HomeTab()
            Tab.HISTORY -> HistoryTab()
            Tab.SAVED -> SavedTab()
            Tab.PROFILE -> ProfileTab()
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FloatingNav()
        }
    }
}

/** Space to leave under tab content so the last rows clear the floating toolbar. */
val FloatingNavClearance = 150.dp

@Composable
private fun FloatingNav() {
    val store = LocalStore.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.shadow(6.dp, CircleShape).height(64.dp).clip(CircleShape).background(cs.inverseSurface).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val selected = store.tab == tab
                Box(
                    Modifier.width(if (selected) 56.dp else 48.dp).height(48.dp).clip(CircleShape)
                        .background(if (selected) cs.inversePrimary else cs.inverseSurface)
                        .clickable(role = Role.Tab) { store.tab = tab }
                        .semantics { contentDescription = tab.name.lowercase().replaceFirstChar { it.uppercase() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Sym(tab.icon, tint = if (selected) LocalInversePrimaryContent.current else cs.inverseOnSurface, fill = selected)
                }
            }
        }
        Row(
            Modifier.shadow(6.dp, RoundedCornerShape(20.dp)).height(64.dp).clip(RoundedCornerShape(20.dp)).background(cs.primary)
                .clickable(role = Role.Button) { store.requestScan() }.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym("document_scanner", size = 26.dp, tint = cs.onPrimary)
            T("Scan", gab(800, 16), color = cs.onPrimary)
        }
    }
}

@Composable
private fun HomeTab() {
    val store = LocalStore.current
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LensMark(32.dp)
            Spacer(Modifier.weight(1f))
            OverflowMenu()
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T("Label Lens", gab(900, 36, 44, -0.36))
            T("Know what you are about to eat.", hg(500, 16, 24), color = cs.onSurfaceVariant)
        }
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp).clip(CircleShape).background(cs.surfaceContainerHigh)
                .clickable(role = Role.Button) { store.nav.push(Screen.Search) }.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym("search", tint = cs.onSurfaceVariant)
            T(searchHint(store), hg(500, 16), color = cs.onSurfaceVariant)
        }
        val items = store.myList.mapNotNull { store.additive(it) }
        if (items.isEmpty()) EmptyList() else {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T("My list · ${items.size}", gab(800, 20, 26), Modifier.weight(1f))
                TextAction("Clear all", { store.clearList() })
            }
            SegList {
                items.forEachIndexed { i, additive ->
                    val check = store.check(additive)
                    SegRow(
                        i, items.size, minHeight = 72.dp,
                        onClick = { store.nav.push(Screen.Detail(additive.code)) },
                        onLongClick = { store.removeFromList(additive.code) },
                    ) {
                        StatusTile(check.verdict)
                        Column(Modifier.weight(1f)) {
                            T(additive.title, gab(800, 17, 24), maxLines = 2)
                            T("${check.verdict.label} · ${check.reason ?: additive.descriptor.ifBlank { additive.category.lowercase() }}", hg(400, 14, 20), color = cs.onSurfaceVariant, maxLines = 2)
                        }
                        Sym("chevron_right", tint = cs.onSurfaceVariant)
                    }
                }
            }
            T(
                "Touch and hold an additive to remove it.",
                hg(400, 12, 16), Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = cs.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(FloatingNavClearance))
    }
}

fun searchHint(store: Store) =
    if (store.meta.totalCount > 0) "Search ${store.meta.totalCount} additives" else "Search additives"

@Composable
private fun EmptyList() {
    Column(
        Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(120.dp)) {
            Box(Modifier.align(Alignment.TopEnd).size(80.dp).background(cs.secondaryContainer, ExpressiveShapes.Burst12))
            Box(
                Modifier.align(Alignment.BottomStart).size(100.dp).background(cs.primaryContainer, RoundedCornerShape(40.dp)),
                contentAlignment = Alignment.Center,
            ) { Sym("shopping_basket", size = 48.dp, tint = cs.onPrimaryContainer) }
        }
        T("Your list is empty", gab(800, 22, 28))
        T("Scan a label or search to add additives you want to keep an eye on.", hg(400, 15, 22), color = cs.onSurfaceVariant, align = TextAlign.Center)
    }
    GroupLabel("How it works", Modifier.padding(top = 0.dp))
    val steps = listOf(
        "Capture the label" to "Photograph the ingredients list.",
        "Confirm the matches" to "Tick the additives we found.",
        "Read the details" to "Evidence, sources and where it's banned.",
    )
    SegList {
        steps.forEachIndexed { i, (title, text) ->
            SegRow(i, steps.size) {
                Box(Modifier.size(40.dp).background(cs.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    T("${i + 1}", gab(900, 18), color = cs.onPrimaryContainer)
                }
                Column {
                    T(title, hg(700, 16, 22))
                    T(text, hg(400, 14, 20), color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun OverflowMenu() {
    val store = LocalStore.current
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (open) cs.surfaceContainerHighest else cs.background)
                .clickable(role = Role.Button) { open = true }.semantics { contentDescription = "More options" },
            contentAlignment = Alignment.Center,
        ) { Sym("more_vert", tint = if (open) cs.onSurface else cs.onSurfaceVariant) }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(0.dp, 4.dp),
            shape = RoundedCornerShape(20.dp),
            containerColor = cs.surfaceContainer,
        ) {
            Column(Modifier.width(240.dp).padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MenuItem("help", "Help") { open = false; context.openUrl(Links.HELP) }
                MenuItem("lightbulb", "How it works") { open = false; store.nav.push(Screen.Onboarding(replay = true)) }
                Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(cs.outlineVariant))
                MenuItem("license", "Licence", external = true) { open = false; context.openUrl(Links.LICENCE) }
                MenuItem("gavel", "Legal", external = true) { open = false; context.openUrl(Links.LEGAL) }
                MenuItem("volunteer_activism", "Contributing", external = true) { open = false; context.openUrl(Links.CONTRIBUTING) }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: String, label: String, external: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym(icon, size = 22.dp, tint = cs.onSurfaceVariant)
        T(label, hg(600, 15), Modifier.weight(1f))
        // External links get a trailing open_in_new icon.
        if (external) Sym("open_in_new", size = 18.dp, tint = cs.onSurfaceVariant)
    }
}
