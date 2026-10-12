package app.labellens.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.labellens.data.Allergy
import app.labellens.data.Preferences
import app.labellens.data.Screen
import app.labellens.data.Tab
import app.labellens.ui.BottomBar
import app.labellens.ui.Choice
import app.labellens.ui.ChoiceGroup
import app.labellens.ui.Field
import app.labellens.ui.FilledButton
import app.labellens.ui.InfoNote
import app.labellens.ui.LocalStore
import app.labellens.ui.Page
import app.labellens.ui.RadioMark
import app.labellens.ui.SegList
import app.labellens.ui.SegRow
import app.labellens.ui.Sym
import app.labellens.ui.T
import app.labellens.ui.TextAction
import app.labellens.ui.ToggleSwitch
import app.labellens.ui.TonalIconButton
import app.labellens.ui.cs
import app.labellens.ui.statusColors
import app.labellens.ui.theme.gab
import app.labellens.ui.theme.hg

private const val STEPS = 4

fun Modifier.dashedBorder(color: Color, radius: Dp) = drawBehind {
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color,
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** A13 to A17: the four preference steps. Edits go to the store's draft until Review saves them. */
@Composable
fun PrefsStepScreen(step: Int) {
    val store = LocalStore.current
    val next = { store.nav.push(if (step < STEPS - 1) Screen.PrefsStep(step + 1) else Screen.PrefsReview) }
    val titles = listOf(
        "Any allergies or intolerances?" to "We'll flag them on every scan. Change these any time in Settings.",
        "Do you follow a diet?" to "We'll check where additives come from, for example animal-derived gelatine or carmine.",
        "Anything you'd rather avoid?" to "Pick groups, or specific additives by code.",
        "Who are you shopping for?" to "These make the checks stricter. Things that are usually Caution become Avoid.",
    )
    Page(
        bottomBar = {
            BottomBar(color = cs.background, modifier = Modifier.padding(horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (step == 0) {
                        val count = store.draft.allergies.size
                        T("$count selected", hg(600, 14), Modifier.weight(1f), color = cs.onSurfaceVariant)
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (step < STEPS - 1) FilledButton("Next", next, trailingIcon = "arrow_forward") else FilledButton("Review", next)
                }
            }
        },
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step == 0) TonalIconButton("close", "Close", { store.nav.popTo(Screen.Main) })
            else TonalIconButton("arrow_back", "Back", { store.nav.pop() })
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                repeat(STEPS) { i -> Box(Modifier.size(36.dp, 6.dp).background(if (i <= step) cs.primary else cs.surfaceContainerHighest, CircleShape)) }
            }
            TextAction("Skip", next)
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T(titles[step].first, gab(900, 32, 40))
            T(titles[step].second, hg(400, 15, 22), color = cs.onSurfaceVariant)
        }
        when (step) {
            0 -> AllergiesStep()
            1 -> DietStep()
            2 -> AvoidStep()
            else -> SensitivityStep()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AllergiesStep() {
    val store = LocalStore.current
    val draft = store.draft
    var adding by remember { mutableStateOf(false) }
    fun update(allergies: List<Allergy>) {
        store.draft = draft.copy(allergies = allergies)
    }

    Row(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Sym("block", size = 16.dp, tint = statusColors.avoid.color, fill = true)
            T("Always avoid", hg(600, 12), color = cs.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Sym("notifications", size = 16.dp, tint = cs.secondary)
            T("Warn me", hg(600, 12), color = cs.onSurfaceVariant)
        }
        T("Long-press to switch", hg(600, 12), color = cs.onSurfaceVariant)
    }
    FlowRow(
        Modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val chip = RoundedCornerShape(12.dp)
        store.options.allergens.forEach { name ->
            val current = draft.allergies.firstOrNull { it.name == name }
            val toggle = { update(if (current == null) draft.allergies + Allergy(name) else draft.allergies - current) }
            val switch = {
                update(
                    if (current == null) draft.allergies + Allergy(name, "warn")
                    else draft.allergies.map { if (it.name == name) it.copy(level = if (it.level == "avoid") "warn" else "avoid") else it },
                )
            }
            val base = Modifier.height(40.dp).clip(chip)
            if (current == null) {
                Box(
                    base.border(BorderStroke(1.dp, cs.outlineVariant), chip).combinedClickable(onClick = toggle, onLongClick = switch).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { T(name, hg(600, 14)) }
            } else {
                val avoid = current.level == "avoid"
                val container = if (avoid) statusColors.avoid.container else cs.secondaryContainer
                val content = if (avoid) statusColors.avoid.onContainer else cs.onSecondaryContainer
                Row(
                    base.background(container).combinedClickable(onClick = toggle, onLongClick = switch)
                        .semantics { contentDescription = "$name, ${if (avoid) "always avoid" else "warn me"}" }
                        .padding(start = 10.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sym(if (avoid) "block" else "notifications", size = 18.dp, tint = content, fill = avoid)
                    T(name, hg(700, 14), color = content)
                }
            }
        }
        draft.allergies.filter { it.custom }.forEach { custom ->
            Row(
                Modifier.height(40.dp).clip(chip).background(cs.surfaceContainerHigh).padding(start = 16.dp, end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(custom.name, hg(600, 14))
                Box(
                    Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button) { update(draft.allergies - custom) }
                        .semantics { contentDescription = "Remove ${custom.name}" },
                    contentAlignment = Alignment.Center,
                ) { Sym("close", size = 18.dp) }
            }
        }
        DashedChip("Add your own") { adding = true }
    }
    if (adding) {
        AddCustomSheet(
            onDismiss = { adding = false },
            onAdd = { name, level ->
                adding = false
                if (draft.allergies.none { it.name.equals(name, true) }) {
                    update(draft.allergies + Allergy(name, level, custom = store.options.allergens.none { it.equals(name, true) }))
                }
            },
        )
    }
}

@Composable
private fun DashedChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).dashedBorder(cs.outline, 12.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym("add", size = 18.dp, tint = cs.primary)
        T(label, hg(700, 14), color = cs.primary)
    }
}

/** A14. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomSheet(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("avoid") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = cs.surfaceContainerLow,
        contentColor = cs.onSurface,
        scrimColor = statusColors.scrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            T("Add your own", gab(800, 22, 28))
            Field(
                name, { name = it }, "Ingredient or allergen", container = cs.surfaceContainerLow,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onAdd(name.trim(), level) }),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("How strict?", hg(700, 14), color = cs.onSurfaceVariant)
                val avoid = statusColors.avoid
                ChoiceGroup(
                    listOf(Choice("warn", "Warn me", "notifications"), Choice("avoid", "Always avoid", "block")),
                    level, { level = it }, Modifier.fillMaxWidth(),
                    selectedColor = { if (it == "avoid") avoid.color else Color.Unspecified },
                    selectedContent = { if (it == "avoid") avoid.onColor else Color.Unspecified },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextAction("Cancel", onDismiss, height = 40.dp)
                FilledButton("Add", { onAdd(name.trim(), level) }, enabled = name.isNotBlank(), height = 40.dp, style = hg(700, 14), horizontalPadding = 20.dp)
            }
        }
    }
}

@Composable
private fun DietStep() {
    val store = LocalStore.current
    val diets = store.options.diets
    SegList {
        diets.forEachIndexed { i, diet ->
            val selected = store.draft.diet == diet.id
            SegRow(
                i, diets.size, minHeight = 64.dp,
                color = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
                padding = PaddingValues(start = 16.dp, end = 8.dp),
                onClick = { store.draft = store.draft.copy(diet = diet.id) },
            ) {
                Sym(diet.icon, tint = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant)
                T(diet.label, hg(700, 16), Modifier.weight(1f), color = if (selected) cs.onPrimaryContainer else cs.onSurface)
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { RadioMark(selected) }
            }
        }
    }
    InfoNote(
        "Origin is not always certain. If an additive can be plant or animal based, we'll show Caution rather than Avoid.",
        Modifier.padding(top = 16.dp),
    )
}

@Composable
fun LevelPill(level: String) {
    when (level) {
        "avoid" -> Row(
            Modifier.height(32.dp).clip(CircleShape).background(statusColors.avoid.color).padding(start = 8.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym("block", size = 16.dp, tint = statusColors.avoid.onColor, fill = true)
            T("Avoid", hg(700, 12), color = statusColors.avoid.onColor)
        }
        "warn" -> Row(
            Modifier.height(32.dp).clip(CircleShape).background(cs.secondaryContainer).padding(start = 8.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym("notifications", size = 16.dp, tint = cs.onSecondaryContainer)
            T("Warn", hg(700, 12), color = cs.onSecondaryContainer)
        }
        else -> Box(
            Modifier.height(32.dp).border(BorderStroke(1.dp, cs.outlineVariant), CircleShape).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { T("Off", hg(700, 12), color = cs.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvoidStep() {
    val store = LocalStore.current
    val draft = store.draft
    val groups = store.options.groups
    var adding by remember { mutableStateOf(false) }
    SegList {
        groups.forEachIndexed { i, group ->
            val level = draft.groups[group.id] ?: "off"
            // Tapping cycles Off, Warn, Avoid.
            val nextLevel = when (level) {
                "off" -> "warn"
                "warn" -> "avoid"
                else -> "off"
            }
            SegRow(i, groups.size, minHeight = 64.dp, padding = PaddingValues(horizontal = 16.dp), onClick = { store.draft = draft.copy(groups = draft.groups + (group.id to nextLevel)) }) {
                Sym(group.icon, tint = cs.onSurfaceVariant)
                T(group.label, hg(700, 16), Modifier.weight(1f))
                LevelPill(level)
            }
        }
    }
    T("Specific additives", hg(700, 14), Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 10.dp), color = cs.onSurfaceVariant)
    FlowRow(
        Modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        draft.additives.forEach { code ->
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh).padding(start = 8.dp, end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.height(24.dp).background(cs.primaryContainer, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    T(code, gab(800, 12), color = cs.onPrimaryContainer)
                }
                T(store.additive(code)?.name ?: "Additive", hg(600, 14))
                Box(
                    Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button) { store.draft = draft.copy(additives = draft.additives - code) }
                        .semantics { contentDescription = "Remove $code" },
                    contentAlignment = Alignment.Center,
                ) { Sym("close", size = 18.dp) }
            }
        }
        DashedChip("Add by code") { adding = true }
    }
    if (adding) {
        AddCodeSheet(onDismiss = { adding = false }) { code ->
            adding = false
            if (code !in draft.additives) store.draft = draft.copy(additives = draft.additives + code)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCodeSheet(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val store = LocalStore.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        val compact = text.trim().replace(Regex("[\\s\\-.]"), "").lowercase().replace(Regex("^ins"), "e")
        val wanted = if (compact.firstOrNull()?.isDigit() == true) "e$compact" else compact
        val found = store.catalogue.firstOrNull { it.code.lowercase() == wanted } ?: store.catalogue.firstOrNull { it.name.equals(text.trim(), true) }
        if (found == null) error = "We don't have that one. Try a code like E951." else onAdd(found.code)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = cs.surfaceContainerLow,
        contentColor = cs.onSurface,
        scrimColor = statusColors.scrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            T("Add by code", gab(800, 22, 28))
            Field(
                text, { text = it; error = null }, "Code or name", error = error, container = cs.surfaceContainerLow,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextAction("Cancel", onDismiss, height = 40.dp)
                FilledButton("Add", { submit() }, enabled = text.isNotBlank(), height = 40.dp, style = hg(700, 14), horizontalPadding = 20.dp)
            }
        }
    }
}

@Composable
private fun SensitivityStep() {
    val store = LocalStore.current
    val draft = store.draft
    val options = store.options.sensitivities
    SegList {
        options.forEachIndexed { i, option ->
            val on = option.id in draft.sensitivities
            val set = { value: Boolean -> store.draft = draft.copy(sensitivities = if (value) draft.sensitivities + option.id else draft.sensitivities - option.id) }
            SegRow(i, options.size, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), onClick = { set(!on) }) {
                Box(Modifier.size(48.dp).background(cs.tertiaryContainer, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Sym(option.icon, size = 26.dp, tint = cs.onTertiaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    T(option.label, hg(700, 16, 22))
                    T(option.description, hg(400, 14, 20), color = cs.onSurfaceVariant)
                }
                ToggleSwitch(on, set, option.label)
            }
        }
    }
    InfoNote("Label Lens is not medical advice. Talk to a doctor about allergies and pregnancy.", Modifier.padding(top = 16.dp))
}

/** Warn / Avoid pair: the chosen side grows to show its label. */
@Composable
private fun LevelToggle(level: String, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val left = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 6.dp, bottomEnd = 6.dp)
        val right = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp)
        listOf(Triple("warn", "Warn", "notifications"), Triple("avoid", "Avoid", "block")).forEach { (id, label, icon) ->
            val selected = level == id
            val container = when {
                !selected -> cs.surfaceContainerLow
                id == "avoid" -> statusColors.avoid.color
                else -> cs.secondary
            }
            val content = when {
                !selected -> cs.onSurfaceVariant
                id == "avoid" -> statusColors.avoid.onColor
                else -> cs.onSecondary
            }
            Row(
                Modifier.height(40.dp).clip(if (id == "warn") left else right).background(container)
                    .clickable(role = Role.RadioButton) { onChange(id) }.semantics { contentDescription = label }
                    .then(if (selected) Modifier.padding(start = 10.dp, end = 12.dp) else Modifier.width(48.dp)),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym(icon, size = if (selected) 18.dp else 20.dp, tint = content, fill = selected && id == "avoid")
                if (selected) T(label, hg(700, 13), color = content)
            }
        }
    }
}

private data class ReviewRow(val label: String, val level: String, val onChange: (String) -> Unit)

/** A18: set how strict each choice is, then save. */
@Composable
fun PrefsReviewScreen() {
    val store = LocalStore.current
    val draft = store.draft
    val rows = buildList {
        draft.allergies.forEach { allergy ->
            add(ReviewRow(allergy.name, allergy.level) { level -> store.draft = draft.copy(allergies = draft.allergies.map { if (it == allergy) it.copy(level = level) else it }) })
        }
        store.options.diets.firstOrNull { it.id == draft.diet && it.strictness != "none" }?.let { diet ->
            add(ReviewRow("${diet.label} diet", draft.dietLevel) { store.draft = draft.copy(dietLevel = it) })
        }
        store.options.groups.forEach { group ->
            val level = draft.groups[group.id] ?: "off"
            if (level != "off") add(ReviewRow(group.label, level) { store.draft = draft.copy(groups = draft.groups + (group.id to it)) })
        }
    }
    val extras = store.options.sensitivities.filter { it.id in draft.sensitivities }.map { "${it.label} (stricter)" } + draft.additives

    Page(
        bottomBar = {
            BottomBar(color = cs.background, modifier = Modifier.padding(horizontal = 8.dp)) {
                FilledButton("Save and start scanning", {
                    store.savePreferences(draft)
                    store.tab = Tab.HOME
                    store.nav.popTo(Screen.Main)
                    store.snack("Preferences saved")
                }, Modifier.fillMaxWidth())
            }
        },
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TonalIconButton("arrow_back", "Back", { store.nav.pop() })
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("Your profile", gab(900, 32, 40))
            T("Avoid makes a product's verdict Avoid. Warn makes it Caution.", hg(400, 15, 22), color = cs.onSurfaceVariant)
        }
        if (rows.isEmpty() && extras.isEmpty()) {
            InfoNote("Nothing set yet. Go back to add allergies, a diet or things to avoid, or save and set them up later in Settings.")
        }
        SegList {
            rows.forEachIndexed { i, row ->
                SegRow(i, rows.size, gap = 10.dp, padding = PaddingValues(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
                    T(row.label, hg(700, 15), Modifier.weight(1f))
                    LevelToggle(row.level, row.onChange)
                }
            }
        }
        if (extras.isNotEmpty()) {
            T("+ ${extras.joinToString(" · ")}", hg(600, 13, 18), Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp), color = cs.onSurfaceVariant)
        }
    }
}
