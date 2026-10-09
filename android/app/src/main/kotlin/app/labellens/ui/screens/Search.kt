package app.labellens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.labellens.data.Matcher
import app.labellens.data.Screen
import app.labellens.ui.BottomBar
import app.labellens.ui.CheckMark
import app.labellens.ui.CodeBadge
import app.labellens.ui.EmptyState
import app.labellens.ui.FilledButton
import app.labellens.ui.LocalStore
import app.labellens.ui.OutlineButton
import app.labellens.ui.Page
import app.labellens.ui.PlainIconButton
import app.labellens.ui.SegRow
import app.labellens.ui.ShapeHero
import app.labellens.ui.T
import app.labellens.ui.cs
import app.labellens.ui.theme.ExpressiveShapes
import app.labellens.ui.theme.hg

@Composable
fun SearchScreen() {
    val store = LocalStore.current
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    val results = remember(query, store.catalogue) { Matcher.search(query, store.catalogue) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Page(
        scroll = false,
        bottomBar = if (selected.isEmpty()) null else ({
            BottomBar {
                FilledButton(
                    if (selected.size == 1) "Show 1 additive" else "Show ${selected.size} additives",
                    onClick = {
                        store.addToList(selected)
                        if (selected.size == 1) store.nav.replace(Screen.Detail(selected.first())) else store.nav.popTo(Screen.Main)
                    },
                    Modifier.fillMaxWidth(),
                )
            }
        }),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().height(56.dp).clip(CircleShape)
                .background(cs.surfaceContainerHigh).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlainIconButton("arrow_back", "Back", { store.nav.pop() }, tint = cs.onSurface)
            Box(Modifier.weight(1f).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) T(searchHint(store), hg(500, 16), color = cs.onSurfaceVariant, maxLines = 1)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    textStyle = hg(500, 16).copy(color = cs.onSurface),
                    singleLine = true,
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
            if (query.isNotEmpty()) PlainIconButton("close", "Clear search", { query = "" })
        }
        when {
            store.catalogue.isEmpty() -> EmptyState(
                "The additive list isn't here yet",
                "Connect to the internet once and the full list is saved on this phone.",
                Modifier.padding(top = 96.dp),
                hero = { ShapeHero("cloud_off", ExpressiveShapes.Clover4) },
                actions = { OutlineButton("Try again", { store.refresh() }, Modifier.padding(top = 8.dp), height = 40.dp, style = hg(700, 14), borderColor = cs.outlineVariant, content = cs.onSurfaceVariant) },
            )
            results.isEmpty() -> EmptyState(
                "No additives match \"${query.trim()}\"",
                "Try the name, or a code like E330 or INS 330. Spaces and dashes don't matter.",
                Modifier.padding(top = 96.dp),
                hero = { ShapeHero("search_off", ExpressiveShapes.Clover4) },
                actions = { OutlineButton("Clear search", { query = "" }, Modifier.padding(top = 8.dp), height = 40.dp, style = hg(700, 14), borderColor = cs.outlineVariant, content = cs.onSurfaceVariant) },
            )
            else -> {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                    T(if (results.size == 1) "1 result" else "${results.size} results", hg(700, 14, 20), Modifier.weight(1f), color = cs.onSurfaceVariant)
                    if (selected.isNotEmpty()) T("${selected.size} selected", hg(700, 14, 20), color = cs.primary)
                }
                LazyColumn(
                    Modifier.weight(1f).padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(results, key = { _, a -> a.code }) { i, additive ->
                        val isSelected = additive.code in selected
                        val text = if (isSelected) cs.onPrimaryContainer else cs.onSurface
                        SegRow(
                            i, results.size,
                            color = if (isSelected) cs.primaryContainer else cs.surfaceContainerHigh,
                            minHeight = 72.dp,
                            padding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            onClick = { selected = if (isSelected) selected - additive.code else selected + additive.code },
                            onLongClick = { store.nav.push(Screen.Detail(additive.code)) },
                        ) {
                            CodeBadge(additive.code, selected = isSelected)
                            Column(Modifier.weight(1f)) {
                                T(additive.name, hg(700, 16, 22), color = text, maxLines = 2)
                                T(additive.categoryLine, hg(400, 14, 20), color = if (isSelected) text else cs.onSurfaceVariant, maxLines = 1)
                            }
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CheckMark(isSelected) }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}
