package com.hirusha.labellens.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hirusha.labellens.R
import com.hirusha.labellens.data.Store
import com.hirusha.labellens.data.Verdict
import com.hirusha.labellens.ui.theme.CodeBadgeStyle
import com.hirusha.labellens.ui.theme.ExpressiveShapes
import com.hirusha.labellens.ui.theme.LocalStatus
import com.hirusha.labellens.ui.theme.StatusColors
import com.hirusha.labellens.ui.theme.StatusPalette
import com.hirusha.labellens.ui.theme.SymbolGlyphs
import com.hirusha.labellens.ui.theme.gab
import com.hirusha.labellens.ui.theme.hg
import com.hirusha.labellens.ui.theme.segShape

val LocalStore = staticCompositionLocalOf<Store> { error("Store not provided") }

val cs
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme

val statusColors
    @Composable @ReadOnlyComposable
    get() = LocalStatus.current

fun StatusPalette.of(verdict: Verdict): StatusColors = when (verdict) {
    Verdict.SAFE -> safe
    Verdict.CAUTION -> caution
    Verdict.AVOID -> avoid
}

// ---------- Icons ----------

private fun symbolFamily(fill: Float) = FontFamily(
    Font(
        R.font.material_symbols_rounded,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("FILL", fill),
            FontVariation.Setting("opsz", 24f),
            FontVariation.weight(400),
        ),
    ),
)

private val SymbolsOutlined = symbolFamily(0f)
private val SymbolsFilled = symbolFamily(1f)

/** A Material Symbols Rounded icon by name, from the font subset in res/font. */
@Composable
fun Sym(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    fill: Boolean = false,
) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        BasicText(
            text = SymbolGlyphs[name].orEmpty(),
            modifier = Modifier.wrapContentSize(unbounded = true),
            style = TextStyle(
                fontFamily = if (fill) SymbolsFilled else SymbolsOutlined,
                fontSize = fontSize,
                lineHeight = fontSize,
                color = tint,
            ),
            softWrap = false,
            maxLines = 1,
        )
    }
}

/** The lens-and-leaf logo mark. */
@Composable
fun LensMark(size: Dp, modifier: Modifier = Modifier, stroke: Color = cs.primary, leaf: Color = statusColors.leaf, vein: Color? = null) {
    Canvas(modifier.size(size)) {
        val unit = this.size.width / 48f
        scale(unit, pivot = Offset.Zero) {
            translate(-30f, -30f) {
                drawCircle(stroke, radius = 15f, center = Offset(49f, 49f), style = Stroke(5.5f))
                drawLine(stroke, Offset(60.5f, 60.5f), Offset(70f, 70f), strokeWidth = 7f, cap = StrokeCap.Round)
                val path = Path().apply {
                    moveTo(41.5f, 56.5f)
                    cubicTo(41.5f, 47f, 47f, 41.5f, 56.5f, 41.5f)
                    cubicTo(56.5f, 51f, 51f, 56.5f, 41.5f, 56.5f)
                    close()
                }
                drawPath(path, leaf)
                if (vein != null) drawLine(vein, Offset(43.5f, 54.5f), Offset(51.5f, 46.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
        }
    }
}

// ---------- Text ----------

@Composable
fun T(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) = Text(text, modifier, color = color, style = style, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

@Composable
fun T(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    align: TextAlign? = null,
) = Text(text, modifier, color = color, style = style, textAlign = align)

/** Turns `**bold**` markers into bold spans. */
fun bold(text: String, color: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) { append(part) } else append(part)
    }
}

// ---------- Page frame ----------

/**
 * A full screen: status bar inset, scrolling content, and an optional bar pinned to the bottom.
 */
@Composable
fun Page(
    modifier: Modifier = Modifier,
    background: Color = cs.background,
    contentColor: Color = cs.onBackground,
    scroll: Boolean = true,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(modifier.fillMaxSize().background(background).statusBarsPadding().imePadding()) {
            val body = Modifier.weight(1f).fillMaxWidth()
            Column(if (scroll) body.verticalScroll(rememberScrollState()) else body) {
                content()
                if (scroll) Spacer(Modifier.height(24.dp))
                if (bottomBar == null) Spacer(Modifier.navigationBarsPadding())
            }
            bottomBar?.invoke()
        }
    }
}

/** The bar that holds a screen's main action, on surfaceContainerLow. */
@Composable
fun BottomBar(modifier: Modifier = Modifier, color: Color = cs.surfaceContainerLow, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(color).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Top row with a tonal back button on the left and optional actions on the right. */
@Composable
fun TopRow(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backIcon: String = "arrow_back",
    backLabel: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) TonalIconButton(backIcon, backLabel, onBack)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

// ---------- Buttons ----------

@Composable
fun TonalIconButton(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, container: Color = cs.surfaceContainerHighest, tint: Color = cs.onSurface) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(container).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Sym(icon, tint = tint) }
}

@Composable
fun PlainIconButton(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = cs.onSurfaceVariant, fill: Boolean = false, size: Dp = 24.dp) {
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Sym(icon, tint = tint, fill = fill, size = size) }
}

@Composable
fun FilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    trailingIcon: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    container: Color = cs.primary,
    content: Color = cs.onPrimary,
    height: Dp = 56.dp,
    style: TextStyle = hg(700, 16),
    horizontalPadding: Dp = 28.dp,
) {
    val background = if (enabled) container else cs.onSurface.copy(alpha = 0.12f)
    val foreground = if (enabled) content else cs.onSurface.copy(alpha = 0.38f)
    Row(
        modifier.height(height).clip(CircleShape).background(background)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            // The button keeps its width and colour; only the label is swapped.
            LoadingShape(28.dp, foreground, Modifier.semantics { contentDescription = "Working" })
        } else {
            if (icon != null) Sym(icon, size = 22.dp, tint = foreground)
            T(text, style, color = foreground, maxLines = 1)
            if (trailingIcon != null) Sym(trailingIcon, size = 22.dp, tint = foreground)
        }
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    style: TextStyle = hg(700, 16),
    borderColor: Color = cs.outline,
    content: Color = cs.onSurface,
    horizontalPadding: Dp = 16.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.height(height).clip(CircleShape).border(BorderStroke(1.dp, borderColor), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        if (icon != null) Sym(icon, size = 20.dp, tint = content)
        T(text, style, color = content, maxLines = 1)
    }
}

@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = cs.primary,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    style: TextStyle = hg(700, 14),
    trailingIcon: String? = null,
) {
    Row(
        modifier.height(height).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(text, style, color = if (enabled) color else cs.onSurfaceVariant, maxLines = 1)
        if (trailingIcon != null) Sym(trailingIcon, size = 18.dp, tint = color)
    }
}

// ---------- Status ----------

@Composable
fun StatusPill(verdict: Verdict, modifier: Modifier = Modifier) {
    val colors = statusColors.of(verdict)
    Row(
        modifier.height(28.dp).clip(CircleShape).background(colors.container).padding(start = 6.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym(verdict.icon, size = 18.dp, tint = colors.onContainer, fill = true)
        T(verdict.label, hg(700, 12), color = colors.onContainer)
    }
}

@Composable
fun StatusTile(verdict: Verdict, modifier: Modifier = Modifier, size: Dp = 48.dp, radius: Dp = 16.dp, iconSize: Dp = 26.dp) {
    val colors = statusColors.of(verdict)
    Box(modifier.size(size).clip(RoundedCornerShape(radius)).background(colors.color), contentAlignment = Alignment.Center) {
        Sym(verdict.icon, size = iconSize, tint = colors.onColor, fill = true)
    }
}

@Composable
fun CodeBadge(code: String, modifier: Modifier = Modifier, selected: Boolean = false) {
    Box(
        modifier.defaultMinSize(minWidth = 56.dp).height(28.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) cs.primary else cs.primaryContainer).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { T(code, CodeBadgeStyle, color = if (selected) cs.onPrimary else cs.onPrimaryContainer) }
}

// ---------- Selection controls ----------

@Composable
fun CheckMark(checked: Boolean, modifier: Modifier = Modifier, color: Color = cs.primary, onColor: Color = cs.onPrimary, error: Boolean = false) {
    if (checked) {
        Box(modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(color), contentAlignment = Alignment.Center) {
            Sym("check", size = 16.dp, tint = onColor)
        }
    } else {
        Box(modifier.size(18.dp).border(2.dp, if (error) cs.error else cs.onSurfaceVariant, RoundedCornerShape(4.dp)))
    }
}

@Composable
fun RadioMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(20.dp).border(2.dp, if (selected) cs.primary else cs.onSurfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (selected) Box(Modifier.size(10.dp).background(cs.primary, CircleShape)) }
}

@Composable
fun ToggleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier) {
    val track = Modifier.width(52.dp).height(32.dp).clip(CircleShape)
    Box(
        modifier.clip(CircleShape).clickable(role = Role.Switch) { onCheckedChange(!checked) }.semantics { contentDescription = label },
    ) {
        if (checked) {
            Box(track.background(cs.primary).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.size(24.dp).background(cs.onPrimary, CircleShape), contentAlignment = Alignment.Center) {
                    Sym("check", size = 16.dp, tint = cs.primary)
                }
            }
        } else {
            Box(
                track.background(cs.surfaceContainerHighest).border(2.dp, cs.outline, CircleShape).padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Box(Modifier.size(16.dp).background(cs.outline, CircleShape)) }
        }
    }
}

data class Choice(val id: String, val label: String, val icon: String? = null)

/** Connected button group: outer corners fully rounded, 6dp corners between buttons. */
@Composable
fun ChoiceGroup(
    choices: List<Choice>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    fill: Boolean = true,
    checkOnSelected: Boolean = false,
    selectedColor: (String) -> Color = { Color.Unspecified },
    selectedContent: (String) -> Color = { Color.Unspecified },
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        choices.forEachIndexed { i, choice ->
            val isSelected = choice.id == selected
            val outer = height / 2
            val shape = RoundedCornerShape(
                topStart = if (i == 0) outer else 6.dp, bottomStart = if (i == 0) outer else 6.dp,
                topEnd = if (i == choices.lastIndex) outer else 6.dp, bottomEnd = if (i == choices.lastIndex) outer else 6.dp,
            )
            val container = if (isSelected) selectedColor(choice.id).takeOrElse(cs.primary) else cs.surfaceContainerHighest
            val content = if (isSelected) selectedContent(choice.id).takeOrElse(cs.onPrimary) else cs.onSurface
            Row(
                (if (fill) Modifier.weight(1f) else Modifier).height(height).clip(shape).background(container)
                    .clickable(role = Role.RadioButton) { onSelect(choice.id) }.padding(horizontal = if (fill) 4.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = if (isSelected && checkOnSelected) "check" else choice.icon
                if (icon != null) Sym(icon, size = 18.dp, tint = content, fill = isSelected && icon == "block")
                T(choice.label, hg(700, 14), color = content, maxLines = 1)
            }
        }
    }
}

private fun Color.takeOrElse(fallback: Color) = if (this == Color.Unspecified) fallback else this

// ---------- Segmented list ----------

@Composable
fun SegList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SegRow(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = cs.surfaceContainerHigh,
    minHeight: Dp = 56.dp,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    gap: Dp = 14.dp,
    shape: Shape = segShape(index, count),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val clickable = when {
        onClick != null && onLongClick != null -> Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        onClick != null -> Modifier.clickable(onClick = onClick)
        else -> Modifier
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight).clip(shape).background(color).then(clickable).padding(padding),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Small heading above a group, in onSurfaceVariant or primary. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, color: Color = cs.onSurfaceVariant) {
    T(text, hg(700, 14, 20), modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp), color = color)
}

// ---------- Text field ----------

@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    isError: Boolean = error != null,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    container: Color = Color.Transparent,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        isError = isError,
        singleLine = true,
        textStyle = hg(400, 16),
        shape = RoundedCornerShape(16.dp),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailing ?: if (isError) ({ Sym("error", tint = cs.error, fill = true) }) else null,
        supportingText = if (error != null) ({ Text(error, style = hg(500, 12, 16)) }) else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            errorContainerColor = container,
            disabledContainerColor = container,
            unfocusedBorderColor = cs.outline,
            focusedBorderColor = cs.primary,
            unfocusedLabelColor = cs.onSurfaceVariant,
            cursorColor = cs.primary,
        ),
    )
}

// ---------- Loading and empty states ----------

/** The Expressive loading indicator: a cookie shape that keeps turning. */
@Composable
fun LoadingShape(size: Dp, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading")
    val angle by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "angle",
    )
    Box(modifier.size(size).rotate(angle).background(color, ExpressiveShapes.Cookie9))
}

/** Alpha for skeleton blocks, pulsing 0.6 to 1 over one second. */
@Composable
fun skeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "alpha")
    return alpha
}

@Composable
fun ShapeHero(
    icon: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    iconSize: Dp = 48.dp,
    container: Color = cs.primaryContainer,
    content: Color = cs.onPrimaryContainer,
    fill: Boolean = false,
) {
    Box(modifier.size(size).background(container, shape), contentAlignment = Alignment.Center) {
        Sym(icon, size = iconSize, tint = content, fill = fill)
    }
}

/** Centered illustration, title and message, with optional actions underneath. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    hero: @Composable () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        hero()
        T(title, gab(800, 22, 28), align = TextAlign.Center)
        T(message, hg(400, 15, 22), color = cs.onSurfaceVariant, align = TextAlign.Center)
        actions()
    }
}

/** Big Gabarito screen title. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, size: Int = 36) {
    T(text, gab(900, size, size + 8, -0.36), modifier)
}

/** Info line with a leading icon, used for footnotes under lists. */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier, icon: String? = "info") {
    Row(modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Sym(icon, size = 18.dp, tint = cs.onSurfaceVariant)
        T(text, hg(400, 13, 18), color = cs.onSurfaceVariant)
    }
}
