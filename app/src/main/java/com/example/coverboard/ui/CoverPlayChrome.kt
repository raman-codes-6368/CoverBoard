package com.example.coverboard.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

// ═════════════════════════════════════════════════════════════════════════════
//  COVERBOARD UI KIT
//
//  One calm, flat design language for the whole settings app:
//   • plain background, tonal rounded cards, no glow, no gradients
//   • a single accent colour (the brand rose) used only for state and primary actions
//   • everything reads from MaterialTheme.colorScheme, so light and dark both work
//
//  The widgets keep the Material names (Switch, Slider, Button …) so SettingsUi.kt
//  only has to import them from this package.
// ═════════════════════════════════════════════════════════════════════════════

val TopBarHeight: Dp = 64.dp

// ── Background ───────────────────────────────────────────────────────────────

/** Plain themed background (the name is kept for compatibility). */
@Composable
fun CinematicBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(MaterialTheme.colorScheme.background), content = content)
}

// ── Scroll awareness (drives the top-bar divider) ────────────────────────────

class ChromeScrollState {
    var scrolled by mutableStateOf(false)
        private set
    var collapsed by mutableStateOf(false)
        private set
    private var offset = 0f

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            offset = (offset - consumed.y).coerceAtLeast(0f)
            scrolled = offset > 6f
            if (consumed.y < -1f) collapsed = true else if (consumed.y > 1f) collapsed = false
            return Offset.Zero
        }
    }

    fun reset() {
        offset = 0f
        scrolled = false
        collapsed = false
    }
}

@Composable
fun rememberChromeScroll(): ChromeScrollState = remember { ChromeScrollState() }

// ── Top bar ──────────────────────────────────────────────────────────────────

/** Round tonal icon button used for Back and top-bar actions. */
@Composable
fun GlassCircleButton(
    painter: Painter,
    description: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    selected: Boolean = false,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val container = if (selected) cs.primaryContainer else cs.surfaceContainerHigh
    val content = if (tint != Color.Unspecified) tint else if (selected) cs.onPrimaryContainer else cs.onSurface
    Box(
        modifier.size(size).clip(CircleShape).background(container).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter, description, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun GlassCircleButton(
    icon: ImageVector,
    description: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    selected: Boolean = false,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit
) = GlassCircleButton(rememberVectorPainter(icon), description, modifier, size, selected, tint, onClick)

@Composable
fun SignatureTitle(title: String) {
    AnimatedContent(
        targetState = title,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) },
        label = "title",
    ) { t ->
        Text(
            t,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Simple top bar: back button + title. A hairline divider fades in once the page has scrolled.
 * Pad the content by [TopBarHeight] + the status bar.
 */
@Composable
fun GlassTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val line by animateFloatAsState(if (scrolled) 1f else 0f, tween(200), label = "barLine")
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(cs.background)
                drawRect(
                    cs.outlineVariant.copy(alpha = line),
                    topLeft = Offset(0f, size.height - 1.dp.toPx()),
                    size = Size(size.width, 1.dp.toPx()),
                )
            }
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(TopBarHeight).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = showBack,
                enter = fadeIn(tween(150)) + expandHorizontally(tween(200)),
                exit = fadeOut(tween(100)) + shrinkHorizontally(tween(160))
            ) {
                Row {
                    GlassCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", size = 40.dp, onClick = onBack)
                    Spacer(Modifier.width(12.dp))
                }
            }
            Box(Modifier.weight(1f).padding(start = if (showBack) 0.dp else 4.dp)) { SignatureTitle(title) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), content = actions)
        }
    }
}

// ── Controls ─────────────────────────────────────────────────────────────────

/** Pass `onCheckedChange = null` when a parent row handles the tap. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    @Suppress("UNUSED_PARAMETER") compact: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.Switch(
        checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled,
        interactionSource = interactionSource,
        colors = SwitchDefaults.colors(
            checkedThumbColor = cs.onPrimary, checkedTrackColor = cs.primary, checkedBorderColor = cs.primary,
            uncheckedThumbColor = cs.outline, uncheckedTrackColor = cs.surfaceContainerHighest, uncheckedBorderColor = cs.outline,
        ),
    )
}

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.Slider(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        valueRange = valueRange, onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = cs.primary, activeTrackColor = cs.primary, inactiveTrackColor = cs.surfaceContainerHighest,
        ),
    )
}

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.Checkbox(
        checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled,
        colors = CheckboxDefaults.colors(checkedColor = cs.primary, uncheckedColor = cs.outline, checkmarkColor = cs.onPrimary),
    )
}

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.RadioButton(
        selected = selected, onClick = onClick, modifier = modifier, enabled = enabled,
        colors = RadioButtonDefaults.colors(selectedColor = cs.primary, unselectedColor = cs.outline),
    )
}

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        singleLine = singleLine, label = label, shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceContainer, unfocusedContainerColor = cs.surfaceContainer,
            focusedBorderColor = cs.primary, unfocusedBorderColor = cs.outlineVariant,
            focusedLabelColor = cs.primary, cursorColor = cs.primary,
        ),
    )
}

/** Primary action: a solid accent pill. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.Button(
        onClick = onClick, modifier = modifier.defaultMinSize(minHeight = 46.dp), enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
        content = content,
    )
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.TextButton(
        onClick = onClick, modifier = modifier, enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        content = content,
    )
}

@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = Color.Unspecified
) {
    val c = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.outlineVariant
    Box(modifier.fillMaxWidth().height(thickness).background(c))
}

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier,
        dismissButton = dismissButton, title = title, text = text,
        shape = RoundedCornerShape(28.dp), containerColor = cs.surfaceContainerHigh,
        titleContentColor = cs.onSurface, textContentColor = cs.onSurfaceVariant,
    )
}

// ── Scroll thumb ─────────────────────────────────────────────────────────────

private fun DrawScope.drawThumb(viewport: Float, content: Float, scrolled: Float, alpha: Float, color: Color) {
    if (alpha <= 0.01f || viewport <= 0f || content <= viewport + 1f) return
    val thumbW = 3.dp.toPx()
    val inset = 3.dp.toPx()
    val thumbH = (viewport * viewport / content).coerceIn(40.dp.toPx(), viewport)
    val travel = viewport - thumbH
    val y = (scrolled / (content - viewport)).coerceIn(0f, 1f) * travel
    drawRoundRect(
        color.copy(alpha = 0.35f * alpha),
        topLeft = Offset(size.width - thumbW - inset, y), size = Size(thumbW, thumbH),
        cornerRadius = CornerRadius(thumbW / 2f),
    )
}

/** Slim scroll thumb that appears while scrolling and fades away afterwards. */
@Composable
fun Modifier.glassScrollbar(state: LazyListState): Modifier {
    val color = MaterialTheme.colorScheme.onSurface
    val moving = state.isScrollInProgress
    val a by animateFloatAsState(
        if (moving) 1f else 0f,
        if (moving) tween(120) else tween(500, delayMillis = 700),
        label = "sbAlpha"
    )
    return this.drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val items = info.visibleItemsInfo
        if (items.isEmpty() || info.totalItemsCount == 0) return@drawWithContent
        val avg = items.sumOf { it.size }.toFloat() / items.size
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        val total = avg * info.totalItemsCount
        val scrolled = state.firstVisibleItemIndex * avg + state.firstVisibleItemScrollOffset
        drawThumb(viewport, total, scrolled, a, color)
    }
}

@Composable
fun Modifier.glassScrollbar(state: LazyGridState): Modifier {
    val color = MaterialTheme.colorScheme.onSurface
    val moving = state.isScrollInProgress
    val a by animateFloatAsState(
        if (moving) 1f else 0f,
        if (moving) tween(120) else tween(500, delayMillis = 700),
        label = "sbAlphaGrid"
    )
    return this.drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val items = info.visibleItemsInfo
        if (items.isEmpty() || info.totalItemsCount == 0) return@drawWithContent
        val rows = items.groupBy { it.offset.y }
        val avgRowH = rows.values.map { r -> r.maxOf { it.size.height } }.average().toFloat()
        val perRow = items.size.toFloat() / rows.size
        val totalRows = ceil(info.totalItemsCount / perRow)
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        val total = avgRowH * totalRows
        val firstRow = state.firstVisibleItemIndex / perRow
        val scrolled = firstRow * avgRowH + state.firstVisibleItemScrollOffset
        drawThumb(viewport, total, scrolled, a, color)
    }
}