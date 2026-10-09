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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.labellens.data.FamilyProfile
import app.labellens.data.Screen
import app.labellens.data.User
import app.labellens.ui.BottomBar
import app.labellens.ui.CheckMark
import app.labellens.ui.FilledButton
import app.labellens.ui.LocalStore
import app.labellens.ui.Page
import app.labellens.ui.PlainIconButton
import app.labellens.ui.PlusBadge
import app.labellens.ui.SegList
import app.labellens.ui.SegRow
import app.labellens.ui.Sym
import app.labellens.ui.T
import app.labellens.ui.TextAction
import app.labellens.ui.TopRow
import app.labellens.ui.cs
import app.labellens.ui.statusColors
import app.labellens.ui.theme.ExpressiveShapes
import app.labellens.ui.theme.gab
import app.labellens.ui.theme.hg

@Composable
private fun toneColors(tone: String): Pair<Color, Color> = when (tone) {
    "primary" -> cs.primary to cs.onPrimary
    "secondary" -> cs.secondaryContainer to cs.onSecondaryContainer
    else -> cs.tertiaryContainer to cs.onTertiaryContainer
}

@Composable
private fun ProfileAvatar(profile: FamilyProfile, size: Int, textSize: Int) {
    val (container, content) = toneColors(profile.tone)
    Box(Modifier.size(size.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
        if (profile.icon != null) Sym(profile.icon, size = (size / 2).dp, tint = content) else T(profile.initials.orEmpty(), gab(900, textSize), color = content)
    }
}

/** P12 and P13. */
@Composable
fun ProfileTab() {
    val store = LocalStore.current
    val user = store.user
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            PlainIconButton("settings", "Settings", { store.nav.push(Screen.Settings) })
        }
        if (user == null) GuestProfile() else MemberProfile(user)
        Spacer(Modifier.height(FloatingNavClearance))
    }
}

@Composable
private fun GuestProfile() {
    val store = LocalStore.current
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(88.dp).background(cs.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
            Sym("person", size = 44.dp, tint = cs.onSurfaceVariant)
        }
        T("You're a guest", gab(900, 28, 34))
        T("Sign up free to keep your list on every device.", hg(400, 15, 22), color = cs.onSurfaceVariant, align = TextAlign.Center)
    }
    val count = if (store.meta.totalCount > 0) "all ${store.meta.totalCount} " else ""
    SegList {
        val included = listOf("Search ${count}additives", "3 scans a day", "A list on this phone")
        included.forEachIndexed { i, text ->
            SegRow(i, 6, gap = 12.dp, minHeight = 0.dp) {
                Sym("check_circle", size = 22.dp, tint = statusColors.safe.color, fill = true)
                T(text, hg(600, 15), Modifier.weight(1f))
            }
        }
        SegRow(3, 6, gap = 12.dp, minHeight = 0.dp) {
            Sym("person_add", size = 22.dp, tint = cs.onSurfaceVariant)
            T("Sync and saved preferences", hg(600, 15), Modifier.weight(1f))
            T("Free account", hg(700, 12), color = cs.onSurfaceVariant)
        }
        listOf("Personal allergy and diet alerts", "History and favourites").forEachIndexed { i, text ->
            SegRow(4 + i, 6, gap = 12.dp, minHeight = 0.dp, onClick = { store.nav.push(Screen.Trial) }) {
                Sym("lock", size = 22.dp, tint = cs.secondary, fill = true)
                T(text, hg(600, 15), Modifier.weight(1f))
                PlusBadge(lock = false)
            }
        }
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledButton("Create free account", { store.nav.push(Screen.CreateAccount) }, Modifier.fillMaxWidth())
        TextAction("Log in", { store.nav.push(Screen.Login) }, Modifier.fillMaxWidth(), style = hg(700, 15))
    }
}

@Composable
private fun MemberProfile(user: User) {
    val store = LocalStore.current
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(96.dp).background(cs.tertiaryContainer, ExpressiveShapes.Cookie9), contentAlignment = Alignment.Center) {
            T(user.initials, gab(900, 36), color = cs.onTertiaryContainer)
        }
        T(user.name, gab(900, 28, 34), align = TextAlign.Center)
        T(user.email, hg(400, 15), color = cs.onSurfaceVariant)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.height(28.dp).clip(CircleShape).background(if (store.hasPlus) cs.primaryContainer else cs.surfaceContainerHighest).padding(start = 8.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val content = if (store.hasPlus) cs.onPrimaryContainer else cs.onSurfaceVariant
                Sym(if (store.hasPlus) "workspace_premium" else "person", size = 16.dp, tint = content, fill = true)
                T("${user.plan.replaceFirstChar { it.uppercase() }} plan", hg(700, 12), color = content)
            }
            Box(
                Modifier.height(28.dp).clip(CircleShape).border(BorderStroke(1.dp, cs.outlineVariant), CircleShape)
                    .clickable(role = Role.Button) { store.nav.push(Screen.Settings) }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { T("Edit profile", hg(700, 12)) }
        }
    }
    val family = store.family
    if (store.isFamily && family != null) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Family profiles · ${family.profiles.size} of ${family.max}", hg(700, 14), Modifier.weight(1f), color = cs.onSurfaceVariant)
            TextAction("Manage", { store.nav.push(Screen.FamilyProfiles) })
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            family.profiles.take(3).forEachIndexed { i, profile ->
                val mine = i == 0
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(if (mine) cs.primaryContainer else cs.surfaceContainerHigh)
                        .clickable { store.nav.push(Screen.FamilyProfiles) }.padding(horizontal = 10.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ProfileAvatar(profile, 44, 16)
                    T(profile.name, hg(700, 13), color = if (mine) cs.onPrimaryContainer else cs.onSurface, maxLines = 1)
                    T(profile.short, hg(500, 11), color = if (mine) cs.onPrimaryContainer else cs.onSurfaceVariant, maxLines = 1, align = TextAlign.Center)
                }
            }
        }
    }
    SegList {
        SegRow(0, 3, minHeight = 60.dp, padding = PaddingValues(horizontal = 16.dp), onClick = {
            store.draft = store.preferences
            store.nav.push(Screen.PrefsStep(0))
        }) {
            Sym("tune", tint = cs.onSurfaceVariant)
            T("My preferences", hg(700, 16), Modifier.weight(1f))
            Sym("chevron_right", tint = cs.onSurfaceVariant)
        }
        SegRow(1, 3, minHeight = 60.dp, padding = PaddingValues(horizontal = 16.dp), onClick = {
            store.nav.push(if (store.hasPlus) Screen.ManageSubscription else Screen.Plans)
        }) {
            Sym("card_membership", tint = cs.onSurfaceVariant)
            T("Subscription", hg(700, 16), Modifier.weight(1f))
            T(store.subscription?.let { "Renews ${it.renewsShort}" } ?: "Free", hg(500, 13), color = cs.onSurfaceVariant)
            Sym("chevron_right", tint = cs.onSurfaceVariant)
        }
        SegRow(2, 3, minHeight = 60.dp, padding = PaddingValues(horizontal = 16.dp), onClick = { store.logout() }) {
            Sym("logout", tint = cs.error)
            T("Log out", hg(700, 16), Modifier.weight(1f), color = cs.error)
        }
    }
}

/** P14: choose who a scan is for. */
@Composable
fun FamilyScreen() {
    val store = LocalStore.current
    val family = store.family
    var selected by rememberSaveable(family?.profiles?.map { it.id }) {
        mutableStateOf(family?.profiles?.filter { it.selected }?.map { it.id } ?: emptyList())
    }
    val names = family?.profiles?.filter { it.id in selected }?.map { it.name.substringBefore(",").substringBefore(" (") }.orEmpty()
    Page(
        bottomBar = if (family == null) null else ({
            BottomBar {
                FilledButton(
                    when (names.size) {
                        0 -> "Pick someone to scan for"
                        1 -> "Scan for ${names[0]}"
                        2 -> "Scan for ${names[0]} and ${names[1]}"
                        else -> "Scan for ${names.size} people"
                    },
                    {
                        store.selectFamily(selected.toSet())
                        store.nav.popTo(Screen.Main)
                        store.requestScan()
                    },
                    Modifier.fillMaxWidth(), enabled = names.isNotEmpty(),
                )
            }
        }),
    ) {
        TopRow(onBack = { store.nav.pop() })
        T("Family", gab(900, 36, 44), Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 6.dp))
        T(
            "Each person has their own allergies and diet. Verdicts check everyone you pick before you scan.",
            hg(400, 15, 22), Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp), color = cs.onSurfaceVariant,
        )
        if (family == null) {
            T("Family profiles are part of the Family plan.", hg(400, 15, 22), Modifier.padding(horizontal = 24.dp), color = cs.onSurfaceVariant)
            return@Page
        }
        val left = family.max - family.profiles.size
        val rows = family.profiles.size + if (left > 0) 1 else 0
        SegList {
            family.profiles.forEachIndexed { i, profile ->
                val isSelected = profile.id in selected
                SegRow(
                    i, rows, padding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    onClick = { selected = if (isSelected) selected - profile.id else selected + profile.id },
                ) {
                    ProfileAvatar(profile, 48, 17)
                    Column(Modifier.weight(1f)) {
                        T(profile.name + (profile.role?.let { " · $it" } ?: ""), hg(700, 16, 22))
                        T(profile.summary, hg(400, 14, 20), color = cs.onSurfaceVariant)
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CheckMark(isSelected) }
                }
            }
            if (left > 0) {
                SegRow(rows - 1, rows, onClick = { store.snack("Adding profiles isn't available in this preview build.") }) {
                    Box(Modifier.size(48.dp).dashedBorder(cs.primary, 24.dp), contentAlignment = Alignment.Center) { Sym("add", tint = cs.primary) }
                    T("Add a profile ($left left)", hg(700, 16), color = cs.primary)
                }
            }
        }
        T(family.note, hg(400, 13, 18), Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp), color = cs.onSurfaceVariant)
    }
}
