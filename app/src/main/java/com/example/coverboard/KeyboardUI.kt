package com.example.coverboard.ime

import android.content.res.Configuration
import android.graphics.PointF
import android.os.SystemClock
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coverboard.Graph
import com.example.coverboard.R
import com.example.coverboard.data.P
import com.example.coverboard.data.Snapshot
import com.example.coverboard.ui.theme.AppFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// ═════════════════════════════════════════════════════════════════════════════
//  COVERBOARD KEYBOARD ROOT & SURFACE UI
// ═════════════════════════════════════════════════════════════════════════════

fun surfaceConfig(s: Snapshot, split: Boolean) = SurfaceConfig(
    spacingDp = s[P.KEY_SPACING].toFloat(),
    cornerDp = s[P.KEY_CORNER].toFloat(),
    borders = s[P.KEY_BORDERS],
    hints = s[P.HINTS],
    popups = s[P.KEY_POPUP],
    longPressMs = s[P.LONG_PRESS_MS],
    swipeTyping = s[P.SWIPE_TYPING],
    trail = s[P.GESTURE_TRAIL],
    trailWidthDp = s[P.TRAIL_WIDTH].toFloat(),
    flowSpace = s[P.FLOW_SPACE],
    swipeDelete = s[P.SWIPE_DELETE],
    spaceCursor = s[P.SPACE_CURSOR],
    cursorSpeed = s[P.CURSOR_SPEED],
    holdDeleteWords = s[P.HOLD_DELETE_WORDS],
    lowercase = s[P.LOWERCASE_KEYS],
    splitGapDp = if (split) s[P.SPLIT_GAP].toFloat() else 0f,
)

fun layoutOptions(s: Snapshot, lang: String, field: FieldKind) = LayoutOptions(
    layout = Layouts.resolveLayout(s[P.LAYOUT], lang),
    lang = lang,
    numberRow = s[P.NUMBER_ROW],
    emojiKey = s[P.EMOJI_KEY],
    globeKey = s[P.GLOBE_KEY],
    arrowKeys = s[P.ARROW_KEYS],
    hints = s[P.HINTS],
    field = field,
    letterSymbols = s[P.LETTER_SYMBOLS],
)

@Composable
fun KeyboardRoot(ctl: InputController, ks: KeyboardState, s: Snapshot, theme: KeyboardTheme) {
    val conf = LocalConfiguration.current
    val landscape = conf.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scale = (if (landscape) s[P.HEIGHT_LANDSCAPE] else s[P.HEIGHT_PORTRAIT]) / 100f
    val mode = s[P.KB_MODE]
    val opts = layoutOptions(s, ks.lang, ks.field)
    val rows = remember(opts, ks.symbolsPage, ks.field) {
        when {
            ks.field == FieldKind.NUMBER -> Layouts.numpad()
            ks.symbolsPage == 0 -> Layouts.letters(opts)
            ks.symbolsPage == 3 -> Layouts.numpadPage()
            else -> Layouts.symbols(opts, ks.symbolsPage)
        }
    }
    val hasNumberRow = ks.field != FieldKind.NUMBER && opts.numberRow
    // Every page has exactly the letters page's total height, so switching pages never resizes the keyboard window.
    val unitRows = if (ks.field == FieldKind.NUMBER) rows.sumOf { it.heightWeight.toDouble() }.toFloat()
    else 4f + (if (opts.numberRow) Layouts.NUMBER_ROW_WEIGHT else 0f)
    val baseRowH = if (landscape) 36.dp else 45.dp
    val rowH = baseRowH * scale
    val keysH = rowH * unitRows + 8.dp
    val stripH = 34.dp
    val toolbarOn = s[P.TOOLBAR_ENABLED]
    val bg = lerp(theme.bgTop, theme.bgBottom, 0.5f) // flat, solid background

    Box(
        Modifier.fillMaxWidth().background(bg).drawBehind {
            drawLine(theme.text.copy(alpha = 0.06f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f * density)
        }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            ToolbarHost(ctl, ks, s, theme, toolbarOn && ks.toolbarOpen)
            Strip(ctl, ks, s, theme, stripH)
            Box(Modifier.fillMaxWidth().height(keysH)) {
                when (ks.panel) {
                    Panel.KEYS -> KeysArea(ctl, ks, s, theme, rows, mode, stripH)
                    Panel.EMOJI -> EmojiPanel(ctl, theme, s) { ks.panel = Panel.KEYS }
                    Panel.CLIPBOARD -> ClipboardPanel(ctl, theme, s) { ks.panel = Panel.KEYS }
                    Panel.EDIT -> EditPanel(ctl, ks, theme) { ks.panel = Panel.KEYS }
                    Panel.THEMES -> ThemesPanel(ctl, theme, s) { ks.panel = Panel.KEYS }
                    Panel.VOICE -> VoicePanel(ctl, theme) { ctl.voice.stop(); ks.panel = Panel.KEYS }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun KeysArea(
    ctl: InputController, ks: KeyboardState, s: Snapshot, theme: KeyboardTheme,
    rows: List<KeyRow>, mode: String, stripH: Dp,
) {
    KeysAreaInner(ctl, ks, s, theme, rows, mode, stripH)
}

@Composable
private fun KeysAreaInner(
    ctl: InputController, ks: KeyboardState, s: Snapshot, theme: KeyboardTheme,
    rows: List<KeyRow>, mode: String, stripH: Dp,
) {
    val surface: @Composable (Modifier) -> Unit = { m ->
        KeyboardSurface(
            rows = rows, theme = theme, cfg = surfaceConfig(s, mode == "split" && ks.field != FieldKind.NUMBER),
            shift = ks.shift, action = ks.action, spaceLabel = "CoverBoard",
            haptics = Graph.haptics, sound = Graph.sound, actions = ctl,
            modifier = m.padding(horizontal = 6.dp, vertical = 4.dp), topOverflow = stripH,
        )
    }
    if (mode == "left" || mode == "right") {
        val pct = s[P.ONEHAND_WIDTH].coerceIn(55, 95)
        val side: @Composable (Modifier) -> Unit = { m ->
            Column(m.fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                SideButton(R.drawable.ic_dl_resize, theme) { Graph.settings.set(P.KB_MODE, "standard") }
                Spacer(Modifier.height(10.dp))
                SideButton(R.drawable.ic_dl_keyboard, theme) { Graph.settings.set(P.KB_MODE, if (mode == "left") "right" else "left") }
            }
        }
        Row(Modifier.fillMaxSize()) {
            if (mode == "right") side(Modifier.weight((100 - pct).toFloat()))
            surface(Modifier.weight(pct.toFloat()))
            if (mode == "left") side(Modifier.weight((100 - pct).toFloat()))
        }
    } else {
        surface(Modifier)
    }
}

@Composable
private fun SideButton(@DrawableRes iconRes: Int, theme: KeyboardTheme, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(theme.text.copy(alpha = 0.08f)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(iconRes), null, tint = theme.text, modifier = Modifier.size(20.dp)) }
}

class ToolItem(val id: String, @DrawableRes val iconRes: Int, val label: String)

val toolItems = listOf(
    ToolItem("clipboard", R.drawable.ic_dl_clipboard_text, "Clipboard"),
    ToolItem("emoji", R.drawable.ic_dl_mood_smile, "Emoji"),
    ToolItem("edit", R.drawable.ic_dl_pencil, "Edit"),
    ToolItem("theme", R.drawable.ic_dl_palette, "Themes"),
    ToolItem("voice", R.drawable.ic_dl_microphone, "Voice"),
    ToolItem("incognito", R.drawable.ic_dl_eye_off, "Incognito"),
    ToolItem("onehanded", R.drawable.ic_dl_resize, "Resize"),
    ToolItem("layout", R.drawable.ic_dl_keyboard, "Layout"),
    ToolItem("settings", R.drawable.ic_dl_settings, "Settings"),
)

fun toolbarOrder(s: Snapshot): List<String> {
    val hidden = s[P.TOOLBAR_HIDDEN].split(",").filter { it.isNotBlank() }.toSet()
    val order = s[P.TOOLBAR_ORDER].split(",").filter { it.isNotBlank() }.toMutableList()
    toolItems.forEach { if (it.id !in order) order.add(it.id) }
    return order.filter { it !in hidden && toolItems.any { t -> t.id == it } }
}

fun toolbarLabel(id: String) = toolItems.firstOrNull { it.id == id }?.label ?: id
fun toolbarIcon(id: String) = toolItems.first { it.id == id }.iconRes
fun allToolbarIds(): List<String> = toolItems.map { it.id }

/**
 * Resizing the IME window every frame is what makes an expanding toolbar stutter, so the height changes
 * once (instantly) and only the content fades/slides, which runs entirely on the GPU layer.
 */
@Composable
private fun ToolbarHost(ctl: InputController, ks: KeyboardState, s: Snapshot, theme: KeyboardTheme, open: Boolean) {
    var mounted by remember { mutableStateOf(open) }
    val progress = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open) {
        if (open) {
            mounted = true
            progress.animateTo(1f, tween(200))
        } else {
            progress.animateTo(0f, tween(110))
            mounted = false
        }
    }
    if (mounted) {
        val lift = with(LocalDensity.current) { 8.dp.toPx() }
        ToolbarRow(ctl, ks, s, theme, Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * lift
        })
    }
}

@Composable
private fun ToolbarRow(ctl: InputController, ks: KeyboardState, s: Snapshot, theme: KeyboardTheme, modifier: Modifier = Modifier) {
    val ids = toolbarOrder(s)
    Row(
        modifier.fillMaxWidth().height(44.dp).padding(horizontal = 6.dp).drawBehind {
            drawLine(theme.text.copy(alpha = 0.06f), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f * density)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ids.forEach { id ->
            val active = id == "incognito" && ks.incognito
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.height(36.dp).widthIn(max = 42.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (active) theme.accent else Color.Transparent)
                        .clickable { runTool(id, ctl, ks, s) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(toolbarIcon(id)), toolbarLabel(id),
                        tint = if (active) theme.onAccent else theme.text.copy(alpha = 0.82f), modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Strip(ctl: InputController, ks: KeyboardState, s: Snapshot, theme: KeyboardTheme, h: Dp) {
    val toolbarOn = s[P.TOOLBAR_ENABLED]
    val open = toolbarOn && ks.toolbarOpen
    Row(Modifier.fillMaxWidth().height(h).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (toolbarOn) {
            val rot by animateFloatAsState(if (open) -90f else 90f, tween(220), label = "chev")
            val tint by animateColorAsState(if (open) theme.accent else theme.textDim, tween(180), label = "tb")
            val bgc by animateColorAsState(
                if (open) theme.accent.copy(alpha = 0.16f) else theme.text.copy(alpha = 0.06f), tween(180), label = "tbbg"
            )
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(bgc).clickable {
                    Graph.haptics.play(HapticEvent.SPECIAL, null)
                    ks.toolbarOpen = !ks.toolbarOpen
                },
                contentAlignment = Alignment.Center,
            ) {
                // Chevron points down when the toolbar is hidden, up when it is showing above the keyboard.
                Icon(
                    painterResource(R.drawable.ic_chevron_right_custom), if (open) "Hide toolbar" else "Show toolbar",
                    tint = tint, modifier = Modifier.size(15.dp).graphicsLayer { rotationZ = rot },
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            SuggestionRow(ctl, ks, theme)
        }
        if (ks.incognito) {
            Spacer(Modifier.width(6.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(theme.accent.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_dl_eye_off), null, tint = theme.accent, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Incognito", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = AppFont)
            }
        } else if (toolbarOn) {
            Spacer(Modifier.width(34.dp))
        }
    }
}

private fun runTool(id: String, ctl: InputController, ks: KeyboardState, s: Snapshot) {
    Graph.haptics.play(HapticEvent.SPECIAL, null)
    when (id) {
        "clipboard" -> ks.panel = Panel.CLIPBOARD
        "emoji" -> ks.panel = Panel.EMOJI
        "edit" -> ks.panel = Panel.EDIT
        "theme" -> ks.panel = Panel.THEMES
        "voice" -> ctl.startVoice()
        "incognito" -> ctl.toggleIncognito()
        "onehanded" -> ctl.cycleMode()
        "layout" -> {
            val ids = Layouts.layoutIds
            Graph.settings.set(P.LAYOUT, ids[(ids.indexOf(s[P.LAYOUT]) + 1).mod(ids.size)])
        }
        "settings" -> ctl.openSettings()
    }
}

@Composable
private fun SuggestionRow(ctl: InputController, ks: KeyboardState, theme: KeyboardTheme) {
    val list = ks.suggestions
    Row(Modifier.fillMaxSize().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        list.forEachIndexed { index, sug ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.50f)
                        .background(theme.text.copy(alpha = 0.12f))
                )
            }
            val label = when (sug.kind) {
                SugKind.TYPED -> "“${sug.text}”"
                SugKind.CLIPBOARD -> "Paste: " + sug.text.replace("\n", " ")
                else -> sug.text
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { ctl.onSuggestion(sug) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    color = if (sug.primary) theme.text else theme.textDim.copy(alpha = 0.85f),
                    fontSize = if (sug.kind == SugKind.EMOJI) 20.sp else 15.sp,
                    fontFamily = AppFont,
                    fontWeight = if (sug.primary) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PanelHeader(theme: KeyboardTheme, title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(theme.text.copy(alpha = 0.10f))
                .clickable {
                    Graph.haptics.play(HapticEvent.MODE, null)
                    onBack()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left_custom),
                contentDescription = "Back to keyboard",
                tint = theme.text,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = theme.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = AppFont, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun VoicePanel(ctl: InputController, theme: KeyboardTheme, onBack: () -> Unit) {
    val v = ctl.voice
    val listening = v.state == VoiceEngine.State.LISTENING
    val busy = v.state == VoiceEngine.State.PROCESSING
    val lvl by animateFloatAsState(if (listening) v.level else 0f, tween(100), label = "lvl")
    val breathe = rememberInfiniteTransition(label = "breathe")
    val pulse by breathe.animateFloat(
        1f, 1.06f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    val micBg by animateColorAsState(if (listening) theme.accent else theme.text.copy(alpha = 0.10f), tween(200), label = "micbg")
    val err = v.error
    val msg = when {
        err != null -> err
        v.partial.isNotBlank() -> v.partial
        listening -> "Listening…"
        busy -> "Typing…"
        else -> "Tap the mic to speak"
    }
    Column(Modifier.fillMaxSize()) {
        PanelHeader(theme, "Voice typing", onBack)
        Column(
            Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                if (listening) {
                    Box(
                        Modifier.size(64.dp).graphicsLayer { val sc = 1f + lvl * 0.55f; scaleX = sc; scaleY = sc }
                            .clip(CircleShape).background(theme.accent.copy(alpha = 0.16f))
                    )
                }
                Box(
                    Modifier.size(64.dp).graphicsLayer { val sc = if (listening) pulse else 1f; scaleX = sc; scaleY = sc }
                        .clip(CircleShape).background(micBg)
                        .clickable {
                            Graph.haptics.play(HapticEvent.SPECIAL, null)
                            if (listening) v.stop() else v.start()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_dl_microphone), "Microphone",
                        tint = if (listening) theme.onAccent else theme.text, modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                msg, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                color = if (v.partial.isNotBlank() && err == null) theme.text else theme.textDim,
                fontSize = 15.sp, fontFamily = AppFont, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun EmojiPanel(ctl: InputController, theme: KeyboardTheme, s: Snapshot, onBack: () -> Unit) {
    var cat by remember { mutableStateOf("recent") }
    val recents = s[P.EMOJI_RECENTS].split(" ").filter { it.isNotBlank() }
    val items = if (cat == "recent") recents else EmojiData.categories.firstOrNull { it.id == cat }?.items ?: emptyList()
    Column(Modifier.fillMaxSize()) {
        PanelHeader(theme, "Emoji", onBack) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { ctl.onDelete() }, contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_dl_pencil), null, tint = theme.text, modifier = Modifier.size(18.dp))
            }
        }
        LazyRow(Modifier.fillMaxWidth().height(38.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
            item {
                CatTab("🕘", cat == "recent", theme) { cat = "recent" }
            }
            items(EmojiData.categories) { c -> CatTab(c.icon, cat == c.id, theme) { cat = c.id } }
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Emoji you use show up here", color = theme.textDim, fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                items(items) { e ->
                    Box(
                        Modifier.height(46.dp).clickable {
                            Graph.haptics.play(HapticEvent.KEY, null)
                            val out = EmojiData.withTone(e, s[P.SKIN_TONE])
                            ctl.commitText(out)
                            val updated = (listOf(e) + recents.filter { it != e }).take(32).joinToString(" ")
                            Graph.settings.set(P.EMOJI_RECENTS, updated)
                        },
                        contentAlignment = Alignment.Center,
                    ) { Text(EmojiData.withTone(e, s[P.SKIN_TONE]), fontSize = 26.sp) }
                }
            }
        }
    }
}

@Composable
private fun CatTab(icon: String, selected: Boolean, theme: KeyboardTheme, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 2.dp).height(34.dp).width(40.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) theme.accent.copy(alpha = 0.25f) else Color.Transparent).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(icon, fontSize = 18.sp) }
}

@Composable
private fun ClipboardPanel(ctl: InputController, theme: KeyboardTheme, s: Snapshot, onBack: () -> Unit) {
    val rev by Graph.clipboard.revision.collectAsState()
    val userRev by Graph.user.revision.collectAsState()
    val list = remember(rev, userRev) { Graph.clipboard.items(s[P.CLIP_EXPIRE_MIN]) }
    Column(Modifier.fillMaxSize()) {
        PanelHeader(theme, "Clipboard", onBack) {
            Text(
                "Clear", color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { Graph.clipboard.clearUnpinned() }.padding(8.dp),
            )
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (s[P.CLIP_ENABLED]) "Copied text shows up here.\nIt stays on this phone and is wiped automatically."
                    else "Clipboard history is turned off in Settings.",
                    color = theme.textDim, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list) { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(theme.key)
                            .clickable { ctl.commitText(item.text) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.text, color = theme.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Box(Modifier.size(32.dp).clip(CircleShape).clickable { Graph.clipboard.togglePin(item.text) }, contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_dl_link), "Pin", tint = if (item.pinned) theme.accent else theme.textDim, modifier = Modifier.size(18.dp))
                        }
                        Box(Modifier.size(32.dp).clip(CircleShape).clickable { Graph.clipboard.remove(item.text) }, contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_close_custom), "Delete", tint = theme.textDim, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditKey(label: String, theme: KeyboardTheme, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.padding(3.dp).clip(RoundedCornerShape(14.dp))
            .background(if (active) theme.accent else theme.key)
            .clickable { Graph.haptics.play(HapticEvent.KEY, null); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) theme.onAccent else theme.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = AppFont)
    }
}

@Composable
private fun EditPanel(ctl: InputController, ks: KeyboardState, theme: KeyboardTheme, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader(theme, "Edit", onBack)
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            Column(Modifier.weight(1.1f).fillMaxHeight()) {
                Row(Modifier.weight(1f)) {
                    EditKey("Home", theme, Modifier.weight(1f).fillMaxHeight()) { ctl.editAction("home") }
                    EditKey("▲", theme, Modifier.weight(1f).fillMaxHeight()) { ctl.editAction("up") }
                    EditKey("End", theme, Modifier.weight(1f).fillMaxHeight()) { ctl.editAction("end") }
                }
                Row(Modifier.weight(1f)) {
                    EditKey("◀", theme, Modifier.weight(1f).fillMaxHeight()) { ctl.editAction("left") }
                    EditKey("Select", theme, Modifier.weight(1f).fillMaxHeight(), ks.selectMode) { ctl.editAction("select") }
                    EditKey("▶", theme, Modifier.weight(1f).fillMaxHeight()) { ctl.editAction("right") }
                }
                Row(Modifier.weight(1f)) {
                    EditKey("◀ word", theme, Modifier.weight(1f).fillMaxHeight()) { ctl.editAction("word_left") }
                    EditKey("▼", theme, Modifier.weight(1f).fillMaxHeight()) { ctl.editAction("down") }
                    EditKey("word ▶", theme, Modifier.weight(1f).fillMaxHeight()) { ctl.editAction("word_right") }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.weight(1f)) {
                    EditKey("Select all", theme, Modifier.weight(1f).fillMaxHeight()) { ctl.editAction("all") }
                    EditKey("Paste", theme, Modifier.weight(1f).fillMaxHeight()) { ctl.editAction("paste") }
                }
                Row(Modifier.weight(1f)) {
                    EditKey("Cut", theme, Modifier.weight(1f).fillMaxHeight()) { ctl.editAction("cut") }
                    EditKey("Copy", theme, Modifier.weight(1f).fillMaxHeight()) { ctl.editAction("copy") }
                }
                Row(Modifier.weight(1f)) {
                    EditKey("Undo", theme, Modifier.weight(1f).fillMaxHeight()) { ctl.editAction("undo") }
                    EditKey("Redo", theme, Modifier.weight(1f).fillMaxHeight()) { ctl.editAction("redo") }
                }
            }
        }
    }
}

@Composable
fun ThemeSwatch(theme: KeyboardTheme, selected: Boolean, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.width(92.dp).height(62.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(theme.bgTop, theme.bgBottom)))
                .border(if (selected) 2.dp else 1.dp, if (selected) theme.accent else theme.text.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                .padding(7.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(if (r == 1) 6 else 7) { Box(Modifier.size(width = 8.dp, height = 9.dp).clip(RoundedCornerShape(2.dp)).background(theme.key)) }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).size(width = 18.dp, height = 9.dp).clip(RoundedCornerShape(3.dp)).background(theme.accent))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = theme.text, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun ThemesPanel(ctl: InputController, theme: KeyboardTheme, s: Snapshot, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader(theme, "Themes", onBack) {
            Text(
                "More…", color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { ctl.openSettings() }.padding(8.dp),
            )
        }
        LazyRow(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Themes.ids) { id ->
                val t = when (id) {
                    "auto" -> Themes.builtIn[0].copy(id = "auto", name = "System")
                    "custom" -> Themes.custom(s)
                    else -> Themes.builtIn.first { it.id == id }
                }
                ThemeSwatch(t, s[P.THEME] == id, Themes.displayName(id).substringBefore(" (")) {
                    Graph.haptics.play(HapticEvent.MODE, null)
                    Graph.settings.set(P.THEME, id)
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  KEYBOARD SURFACE DRAWING
// ═════════════════════════════════════════════════════════════════════════════

data class SurfaceConfig(
    val spacingDp: Float = 4f,
    val cornerDp: Float = 12f,
    val borders: Boolean = true,
    val hints: Boolean = true,
    val popups: Boolean = true,
    val longPressMs: Int = 300,
    val swipeTyping: Boolean = true,
    val trail: Boolean = true,
    val trailWidthDp: Float = 6f,
    val flowSpace: Boolean = true,
    val swipeDelete: Boolean = true,
    val spaceCursor: Boolean = true,
    val cursorSpeed: Int = 50,
    val holdDeleteWords: Boolean = true,
    val lowercase: Boolean = true,
    val splitGapDp: Float = 0f,
)

class KeyRect(val spec: KeySpec, val rect: Rect, val row: Int)

private fun computeKeys(rows: List<KeyRow>, w: Float, h: Float, splitGap: Float): List<KeyRect> {
    if (w <= 0f || h <= 0f || rows.isEmpty()) return emptyList()
    val out = ArrayList<KeyRect>()
    val totalWeight = rows.sumOf { it.heightWeight.toDouble() }.toFloat().coerceAtLeast(0.01f)
    val usable = w - splitGap
    val mid = usable / 2f
    var y = 0f
    rows.forEachIndexed { r, row ->
        val rowH = h * row.heightWeight / totalWeight
        val sum = row.keys.sumOf { it.weight.toDouble() }.toFloat()
        val unit = usable / max(10f, sum + 2 * row.inset)
        var x = (usable - unit * sum) / 2f
        for (k in row.keys) {
            val kw = unit * k.weight
            var left = x
            if (splitGap > 0f && left >= mid) left += splitGap
            out.add(KeyRect(k, Rect(left, y, left + kw, y + rowH), r))
            x += kw
        }
        y += rowH
    }
    return out
}

private class TouchPt(val x: Float, val y: Float, val t: Long)

private class AltBox(val rect: Rect, val cellW: Float, val pad: Float)

/** Compact accent strip: slim cells hugging the key. Shared by drawing and touch hit-testing. */
private fun altBox(k: KeyRect, alts: List<String>, d: Float, totalW: Float, overflow: Float): AltBox {
    val n = alts.size.coerceAtLeast(1)
    val longest = alts.maxOfOrNull { it.length } ?: 1
    val pad = 4f * d
    var cellW = (k.rect.width * 0.84f).coerceIn(29f * d, 35f * d) + (longest - 1).coerceAtLeast(0) * 8f * d
    cellW = min(cellW, (totalW - 2f * pad - 8f * d) / n)
    val bw = cellW * n + pad * 2f
    val bh = (k.rect.height * 0.84f).coerceIn(38f * d, 43f * d)
    val cx = k.rect.center.x.coerceIn(bw / 2f + 3f * d, max(bw / 2f + 3f * d, totalW - bw / 2f - 3f * d))
    val top = max(k.rect.top - bh - 5f * d, -overflow + 2f * d)
    return AltBox(Rect(cx - bw / 2f, top, cx + bw / 2f, top + bh), cellW, pad)
}

private class SurfaceUi {
    var pressedId: String? = null
    val held = HashSet<String>()
    var levelMap = HashMap<String, Float>()
    var popup: KeyRect? by mutableStateOf(null)
    var alt: KeyRect? by mutableStateOf(null)
    var altIndex by mutableIntStateOf(0)
    var altT by mutableFloatStateOf(1f)
    var altHi by mutableFloatStateOf(0f)
    var altJob: Job? = null
    var hiJob: Job? = null
    var prevKeys: List<KeyRect>? by mutableStateOf(null)
    var xfade by mutableFloatStateOf(1f)
    var stable: Set<String> = emptySet()
    val trail = ArrayList<TouchPt>()
    var frame by mutableStateOf(0L)
    var animating by mutableStateOf(false)

    fun level(id: String, now: Long): Float = levelMap[id] ?: 0f

    fun busy(now: Long): Boolean = levelMap.isNotEmpty() || trail.isNotEmpty()

    fun poke() { frame = System.nanoTime() }
}

private class TouchHandler(
    val keys: List<KeyRect>,
    val ui: SurfaceUi,
    val cfg: () -> SurfaceConfig,
    val actions: () -> KeyActions,
    val haptics: HapticEngine?,
    val sound: SoundEngine?,
    val view: View,
    val scope: CoroutineScope,
    val density: Float,
    val totalW: Float,
    val topOverflow: Float,
) {
    private var activeId: Any? = null
    private var key: KeyRect? = null
    private var startP = PointF()
    private var lastP = PointF()
    private var downAt = 0L
    private var longTriggered = false
    private var gestureMode = false
    private var cursorMode = false
    private var deleteMode = false
    private var gesturePath = ArrayList<PointF>()
    private var longJob: Job? = null
    private var cursorAccum = 0f

    fun down(id: Any, p: Offset) {
        if (activeId != null) return
        activeId = id
        downAt = SystemClock.uptimeMillis()
        startP = PointF(p.x, p.y)
        lastP = PointF(p.x, p.y)
        longTriggered = false
        gestureMode = false
        cursorMode = false
        deleteMode = false
        gesturePath.clear()
        cursorAccum = 0f

        val k = findKey(p.x, p.y)
        key = k
        if (k != null) {
            ui.pressedId = k.spec.id
            ui.held.add(k.spec.id)
            ui.levelMap[k.spec.id] = 1f
            ui.animating = true
            ui.poke()
            if (cfg().popups && (k.spec.type == KeyType.LETTER || k.spec.type == KeyType.CHAR)) ui.popup = k
            haptics?.play(HapticEvent.KEY, view)
            sound?.play(k.spec.type)

            longJob?.cancel()
            val ms = cfg().longPressMs.toLong()
            longJob = scope.launch {
                delay(ms)
                if (activeId == id && !gestureMode && !cursorMode && !deleteMode) {
                    longTriggered = true
                    haptics?.play(HapticEvent.LONG_PRESS, view)
                    when (k.spec.type) {
                        KeyType.SPACE -> actions().onSpaceLong()
                        KeyType.GLOBE -> actions().onGlobe(true)
                        KeyType.SHIFT -> actions().onShiftLock()
                        KeyType.DELETE -> {
                            if (cfg().holdDeleteWords) {
                                actions().onDeleteWord()
                                haptics?.play(HapticEvent.DELETE_TICK, view)
                                sound?.play(KeyType.DELETE)
                                delay(380L)
                                var pause = 280L
                                while (activeId == id && !gestureMode && !cursorMode) {
                                    actions().onDeleteWord()
                                    haptics?.play(HapticEvent.DELETE_TICK, view)
                                    sound?.play(KeyType.DELETE)
                                    delay(pause)
                                    pause = (pause * 0.88f).toLong().coerceAtLeast(160L)
                                }
                            } else {
                                var delayMs = 120L
                                while (activeId == id && !gestureMode && !cursorMode) {
                                    actions().onDelete()
                                    haptics?.play(HapticEvent.DELETE_TICK, view)
                                    sound?.play(KeyType.DELETE)
                                    delay(delayMs)
                                    delayMs = (delayMs * 0.9f).toLong().coerceAtLeast(45L)
                                }
                            }
                        }
                        else -> {
                            if (k.spec.label == "," || k.spec.id.contains("comma")) {
                                actions().onEmojiKey()
                            } else if (k.spec.alts.isNotEmpty()) {
                                showAlt(k)
                            } else if (k.spec.hint != null) {
                                actions().onText(k.spec.hint, k.spec)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showAlt(k: KeyRect) {
        ui.alt = k
        ui.altIndex = 0
        ui.popup = null
        ui.altT = 0f
        ui.altHi = 0f
        ui.altJob?.cancel()
        ui.hiJob?.cancel()
        ui.altJob = scope.launch {
            Animatable(0f).animateTo(1f, tween(120, easing = FastOutSlowInEasing)) { ui.altT = value }
        }
    }

    fun move(id: Any, p: Offset) {
        if (activeId != id) return
        if (ui.alt != null) {
            val altKey = ui.alt!!
            val alts = altKey.spec.alts
            if (alts.isNotEmpty()) {
                val b = altBox(altKey, alts, density, totalW, topOverflow)
                val idx = ((p.x - (b.rect.left + b.pad)) / b.cellW).toInt().coerceIn(0, alts.size - 1)
                if (idx != ui.altIndex) {
                    ui.altIndex = idx
                    haptics?.play(HapticEvent.CURSOR_TICK, view)
                    ui.hiJob?.cancel()
                    ui.hiJob = scope.launch {
                        Animatable(ui.altHi).animateTo(idx.toFloat(), tween(90, easing = FastOutSlowInEasing)) { ui.altHi = value }
                    }
                }
            }
            return
        }
        val k = key ?: return
        val dx = p.x - startP.x
        val dy = p.y - startP.y
        val dist = hypot(dx, dy)

        if (!longTriggered && !gestureMode && !cursorMode && !deleteMode && dist > 18f * density) {
            longJob?.cancel()
            if (k.spec.type == KeyType.SPACE && cfg().spaceCursor) {
                cursorMode = true
                ui.popup = null
            } else if (k.spec.type == KeyType.DELETE && cfg().swipeDelete && dx < -20f * density) {
                deleteMode = true
                ui.popup = null
            } else if (k.spec.type == KeyType.LETTER && cfg().swipeTyping) {
                gestureMode = true
                ui.popup = null
                actions().onGestureStart()
                gesturePath.add(PointF(p.x, p.y))
            }
        }

        if (gestureMode) {
            gesturePath.add(PointF(p.x, p.y))
            if (cfg().trail) {
                ui.trail.add(TouchPt(p.x, p.y, System.nanoTime()))
                ui.animating = true
            }
        } else if (cursorMode) {
            val step = 14f * density / (cfg().cursorSpeed / 50f).coerceAtLeast(0.2f)
            cursorAccum += p.x - lastP.x
            while (cursorAccum > step) {
                actions().onCursorMove(1)
                haptics?.play(HapticEvent.CURSOR_TICK, view)
                cursorAccum -= step
            }
            while (cursorAccum < -step) {
                actions().onCursorMove(-1)
                haptics?.play(HapticEvent.CURSOR_TICK, view)
                cursorAccum += step
            }
        } else if (deleteMode) {
            val step = 28f * density
            if (p.x - lastP.x < -step) {
                actions().onDeleteWord()
                haptics?.play(HapticEvent.DELETE_TICK, view)
                lastP.x = p.x
                deleteMode = false
                longTriggered = true
            }
        }
        lastP = PointF(p.x, p.y)
    }

    fun up(id: Any) {
        if (activeId != id) return
        longJob?.cancel()
        ui.popup = null
        val k = key
        if (ui.alt != null && k != null) {
            val alts = k.spec.alts
            val idx = ui.altIndex.coerceIn(0, alts.size - 1)
            val selectedAlt = alts.getOrNull(idx) ?: k.spec.label
            actions().onText(selectedAlt, k.spec)
            haptics?.play(HapticEvent.KEY, view)
            sound?.play(k.spec.type)
            ui.held.remove(k.spec.id)
            ui.animating = true
            ui.poke()
            ui.popup = null
            ui.alt = null
            activeId = null
            key = null
            return
        }
        ui.alt = null
        if (k != null) {
            ui.held.remove(k.spec.id)
            ui.animating = true
            ui.poke()

            if (gestureMode) {
                val centers = HashMap<Char, PointF>()
                for (rk in keys) {
                    if (rk.spec.type == KeyType.LETTER && rk.spec.label.isNotEmpty()) {
                        centers[rk.spec.label[0].lowercaseChar()] = PointF(rk.rect.center.x, rk.rect.center.y)
                    }
                }
                val kw = keys.firstOrNull { it.spec.type == KeyType.LETTER }?.rect?.width ?: (40f * density)
                actions().onGestureWord(gesturePath, centers, kw)
            } else if (!longTriggered && !cursorMode && !deleteMode) {
                when (k.spec.type) {
                    KeyType.LETTER, KeyType.CHAR -> actions().onText(k.spec.label, k.spec)
                    KeyType.SPACE -> actions().onSpace()
                    KeyType.DELETE -> actions().onDelete()
                    KeyType.ENTER -> actions().onEnter()
                    KeyType.SHIFT -> actions().onShift()
                    KeyType.SYMBOLS, KeyType.LETTERS, KeyType.SYMBOLS2, KeyType.NUMPAD -> actions().onModeKey(k.spec.type)
                    KeyType.EMOJI -> actions().onEmojiKey()
                    KeyType.GLOBE -> actions().onGlobe(false)
                    KeyType.ARROW_LEFT -> actions().onArrow(true)
                    KeyType.ARROW_RIGHT -> actions().onArrow(false)
                }
            }
        }
        activeId = null
        key = null
    }

    fun cancelAll() {
        longJob?.cancel()
        ui.altJob?.cancel()
        ui.hiJob?.cancel()
        activeId = null
        key = null
        ui.popup = null
        ui.alt = null
    }

    private fun findKey(x: Float, y: Float): KeyRect? =
        keys.firstOrNull { it.rect.contains(Offset(x, y)) }
}

@Composable
fun KeyboardSurface(
    rows: List<KeyRow>,
    theme: KeyboardTheme,
    cfg: SurfaceConfig,
    shift: ShiftState,
    action: ActionKind,
    spaceLabel: String,
    haptics: HapticEngine?,
    sound: SoundEngine?,
    actions: KeyActions,
    modifier: Modifier = Modifier,
    topOverflow: Dp = 0.dp,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 200)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(density) { cfg.splitGapDp.dp.toPx() }
    val keys = remember(rows, size, gapPx) { computeKeys(rows, size.width.toFloat(), size.height.toFloat(), gapPx) }
    val cfgS = rememberUpdatedState(cfg)
    val actionsS = rememberUpdatedState(actions)
    val ui = remember { SurfaceUi() }
    val track = remember { KeyTrack() }
    if (track.rows !== rows) {
        val old = track.keys
        if (track.rows != null && old.isNotEmpty() && keys.isNotEmpty()) {
            val now = keys.associate { it.spec.id to it.rect }
            ui.stable = old.filter { now[it.spec.id] == it.rect }.mapTo(HashSet()) { it.spec.id }
            ui.prevKeys = old
            ui.xfade = 0f
        }
        track.rows = rows
    }
    track.keys = keys
    val overflowPx = with(density) { topOverflow.toPx() }
    val handler = remember(keys, haptics, sound) {
        TouchHandler(keys, ui, { cfgS.value }, { actionsS.value }, haptics, sound, view, scope,
            density.density, size.width.toFloat(), overflowPx)
    }

    val icBackspace = painterResource(R.drawable.ic_dl_backspace)
    val icCapsFilled = painterResource(R.drawable.ic_dl_capslock_filled)
    val icCapsUnfilled = painterResource(R.drawable.ic_dl_capslock_unfilled)
    val icEnter = painterResource(R.drawable.ic_dl_enter)
    val icSearch = painterResource(R.drawable.ic_search_custom)
    val icCheck = painterResource(R.drawable.ic_check_custom)
    val icRight = painterResource(R.drawable.ic_chevron_right_custom)
    val icLeft = painterResource(R.drawable.ic_chevron_left_custom)
    val icSmile = painterResource(R.drawable.ic_dl_mood_smile)
    val icGlobe = painterResource(R.drawable.ic_dl_language)

    LaunchedEffect(ui.animating) {
        if (!ui.animating) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dtMs = ((now - last) / 1_000_000f).coerceIn(1f, 48f)
            last = now
            val keep = 0.5f.pow(dtMs / 55f)
            val iter = ui.levelMap.entries.iterator()
            while (iter.hasNext()) {
                val e = iter.next()
                if (e.key !in ui.held) {
                    e.setValue(e.value * keep)
                    if (e.value < 0.01f) iter.remove()
                }
            }
            ui.frame = now
            val cutoff = now - 420_000_000L
            ui.trail.removeAll { it.t < cutoff }
            if (!ui.busy(now)) {
                ui.animating = false
                break
            }
        }
    }

    DisposableEffect(handler) {
        onDispose { handler.cancelAll() }
    }

    LaunchedEffect(rows) {
        ui.pressedId = null
        ui.popup = null
        ui.alt = null
        ui.levelMap.clear()
        ui.animating = false
        if (ui.prevKeys != null) {
            Animatable(0f).animateTo(1f, tween(150, easing = FastOutSlowInEasing)) { ui.xfade = value }
            ui.prevKeys = null
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(handler) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (ch in event.changes) {
                            if (ch.pressed && !ch.previousPressed) handler.down(ch.id, ch.position)
                            else if (ch.pressed) handler.move(ch.id, ch.position)
                            else if (ch.previousPressed) handler.up(ch.id)
                            ch.consume()
                        }
                    }
                }
            }
            .drawBehind {
                val now = ui.frame
                val c = cfg
                val meas = measurer
                val icons = Icons6(
                    backspace = icBackspace,
                    capsFilled = icCapsFilled,
                    capsUnfilled = icCapsUnfilled,
                    enter = icEnter,
                    search = icSearch,
                    done = icCheck,
                    right = icRight,
                    left = icLeft,
                    emoji = icSmile,
                    globe = icGlobe
                )
                val prev = ui.prevKeys
                val xt = ui.xfade
                if (prev != null && xt < 1f) {
                    val stable = ui.stable
                    for (k in keys) if (k.spec.id in stable) drawKey(k, theme, c, shift, action, spaceLabel, 0f, meas, icons)
                    val bounds = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                    val canvas = drawContext.canvas
                    canvas.saveLayer(bounds, Paint().apply { alpha = 1f - xt })
                    for (k in prev) if (k.spec.id !in stable) drawKey(k, theme, c, shift, action, spaceLabel, 0f, meas, icons)
                    canvas.restore()
                    canvas.saveLayer(bounds, Paint().apply { alpha = xt })
                    val sc = 0.95f + 0.05f * xt
                    withTransform({ scale(sc, sc, Offset(size.width / 2f, size.height / 2f)) }) {
                        for (k in keys) if (k.spec.id !in stable) drawKey(k, theme, c, shift, action, spaceLabel, 0f, meas, icons)
                    }
                    canvas.restore()
                } else {
                    for (k in keys) drawKey(k, theme, c, shift, action, spaceLabel, ui.level(k.spec.id, now), meas, icons)
                }
                ui.popup?.let { drawPopup(it, theme, meas, overflowPx, size.width.toFloat(), shift, c) }
                ui.alt?.let { drawAlt(it, theme, meas, overflowPx, size.width.toFloat(), shift, ui.altIndex, ui.altHi, ui.altT) }
                if (c.trail && ui.trail.size > 1) drawTrail(ui.trail, theme, c.trailWidthDp * density.density, now)
            }
    ) {}
}

private class KeyTrack {
    var rows: List<KeyRow>? = null
    var keys: List<KeyRect> = emptyList()
}

private class Icons6(
    val backspace: Painter,
    val capsFilled: Painter,
    val capsUnfilled: Painter,
    val enter: Painter,
    val search: Painter,
    val done: Painter,
    val right: Painter,
    val left: Painter,
    val emoji: Painter,
    val globe: Painter
)

private fun DrawScope.drawIcon(p: Painter, cx: Float, cy: Float, s: Float, color: Color) {
    translate(left = cx - s / 2f, top = cy - s / 2f) {
        with(p) { draw(size = Size(s, s), colorFilter = ColorFilter.tint(color)) }
    }
}

private fun DrawScope.drawKey(
    k: KeyRect, theme: KeyboardTheme, cfg: SurfaceConfig, shift: ShiftState, action: ActionKind,
    spaceLabel: String, level: Float, meas: TextMeasurer, icons: Icons6,
) {
    val d = density
    val sp = cfg.spacingDp * d
    val spV = sp * 1.5f
    val base = Rect(k.rect.left + sp / 2f, k.rect.top + spV / 2f, k.rect.right - sp / 2f, k.rect.bottom - spV / 2f)
    if (base.width <= 0f || base.height <= 0f) return
    val r = base.deflate(level * 0.9f * d)
    val type = k.spec.type
    val special = !(type == KeyType.LETTER || type == KeyType.CHAR || type == KeyType.SPACE)
    val corner = CornerRadius(minOf(cfg.cornerDp * (if (special) 1.25f else 1f), 14f) * d)
    val isChar = type == KeyType.LETTER || type == KeyType.CHAR || type == KeyType.SPACE
    val accentKey = type == KeyType.ENTER || (type == KeyType.SHIFT && shift != ShiftState.OFF)
    val fillBase = when {
        accentKey -> theme.accent
        isChar -> theme.key
        else -> theme.keySpecial
    }
    val fill = if (accentKey) lerp(fillBase, Color.White, 0.12f * level) else lerp(fillBase, theme.keyPressed, level)
    // Flat, fully opaque key: a single solid fill. No gradient, glow, highlight or drop shadow.
    drawRoundRect(fill, r.topLeft, r.size, corner)
    if (cfg.borders) {
        drawRoundRect(theme.text.copy(alpha = 0.06f), r.topLeft, r.size, corner, style = Stroke(1f * d))
    }
    val fg = if (accentKey) theme.onAccent else theme.text
    val cx = r.center.x
    val cy = r.center.y
    val iconS = min(r.height * 0.58f, r.width * 0.65f)
    when (type) {
        KeyType.LETTER, KeyType.CHAR -> {
            var label = k.spec.label
            if (type == KeyType.LETTER) {
                val up = shift != ShiftState.OFF || !cfg.lowercase
                label = if (up) label.uppercase() else label.lowercase()
            }
            val fs = (r.height * (if (label.length > 2) 0.32f else 0.52f)).toSp()
            val layout = meas.measure(label, TextStyle(color = fg, fontSize = fs, fontFamily = AppFont, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp))
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
            val hint = k.spec.hint
            if (hint != null && cfg.hints) {
                val hl = meas.measure(hint, TextStyle(color = theme.textDim.copy(alpha = 0.55f), fontSize = (r.height * 0.20f).toSp(), fontFamily = AppFont, fontWeight = FontWeight.SemiBold))
                drawText(hl, topLeft = Offset(r.right - hl.size.width - 5f * d, r.top + 3f * d))
            }
        }
        KeyType.SPACE -> {
            val layout = meas.measure(spaceLabel, TextStyle(color = theme.textDim.copy(alpha = 0.7f), fontSize = (r.height * 0.26f).toSp(), fontFamily = AppFont, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp))
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
        }
        KeyType.SHIFT -> {
            val p = if (shift != ShiftState.OFF) icons.capsFilled else icons.capsUnfilled
            drawIcon(p, cx, cy, iconS, fg)
        }
        KeyType.DELETE -> drawIcon(icons.backspace, cx, cy, iconS, fg)
        KeyType.ENTER -> {
            val p = when (action) {
                ActionKind.SEARCH -> icons.search
                ActionKind.DONE -> icons.done
                else -> icons.enter
            }
            drawIcon(p, cx, cy, iconS, fg)
        }
        KeyType.EMOJI -> drawIcon(icons.emoji, cx, cy, iconS, fg)
        KeyType.GLOBE -> drawIcon(icons.globe, cx, cy, iconS, fg)
        KeyType.ARROW_LEFT -> drawIcon(icons.left, cx, cy, iconS, fg)
        KeyType.ARROW_RIGHT -> drawIcon(icons.right, cx, cy, iconS, fg)
        KeyType.SYMBOLS, KeyType.LETTERS, KeyType.SYMBOLS2, KeyType.NUMPAD -> {
            val layout = meas.measure(k.spec.label, TextStyle(color = fg, fontSize = (r.height * 0.33f).toSp(), fontFamily = AppFont, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp))
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
        }
    }
}

private fun DrawScope.drawPopup(
    k: KeyRect, theme: KeyboardTheme, meas: TextMeasurer, overflow: Float,
    totalW: Float, shift: ShiftState, cfg: SurfaceConfig,
) {
    val d = density
    val r = k.rect
    // Slim, clean pill that floats just above the finger: barely wider than the key, no stem.
    val pw = (r.width + 6f * d).coerceIn(38f * d, 50f * d)
    val ph = (r.height * 0.92f).coerceIn(38f * d, 46f * d)
    val gap = 5f * d
    val top = max(r.top - ph - gap, -overflow + 2f * d)
    val pcx = r.center.x.coerceIn(pw / 2f + 2f * d, totalW - pw / 2f - 2f * d)
    val pr = Rect(pcx - pw / 2f, top, pcx + pw / 2f, top + ph)
    val corner = CornerRadius(10f * d)
    val bg = theme.popup

    // Soft, layered shadow instead of one hard offset.
    drawRoundRect(Color.Black.copy(alpha = 0.05f), pr.topLeft + Offset(0f, 3.5f * d) - Offset(1.5f * d, 1.5f * d), androidx.compose.ui.geometry.Size(pr.width + 3f * d, pr.height + 3f * d), CornerRadius(12f * d))
    drawRoundRect(Color.Black.copy(alpha = 0.09f), pr.topLeft + Offset(0f, 1.5f * d), pr.size, corner)
    drawRoundRect(bg, pr.topLeft, pr.size, corner)
    drawRoundRect(theme.text.copy(alpha = if (theme.dark) 0.08f else 0.06f), pr.topLeft, pr.size, corner, style = Stroke(0.8f * d))

    var label = k.spec.label
    if (k.spec.type == KeyType.LETTER) {
        val up = shift != ShiftState.OFF || !cfg.lowercase
        label = if (up) label.uppercase() else label.lowercase()
    }
    var fsPx = min(ph * 0.5f, 26f * d)
    var layout = meas.measure(label, TextStyle(color = theme.text, fontSize = fsPx.toSp(), fontFamily = AppFont, fontWeight = FontWeight.Normal))
    val maxW = pw * 0.8f
    if (layout.size.width > maxW) {
        fsPx *= maxW / layout.size.width
        layout = meas.measure(label, TextStyle(color = theme.text, fontSize = fsPx.toSp(), fontFamily = AppFont, fontWeight = FontWeight.Normal))
    }
    drawText(layout, topLeft = Offset(pr.center.x - layout.size.width / 2f, pr.center.y - layout.size.height / 2f))
}

private fun DrawScope.drawAlt(
    k: KeyRect, theme: KeyboardTheme, meas: TextMeasurer, overflow: Float,
    totalW: Float, shift: ShiftState, selectedIndex: Int, hiPos: Float, t: Float,
) {
    val alts = k.spec.alts
    if (alts.isEmpty()) return
    val d = density
    val b = altBox(k, alts, d, totalW, overflow)
    val rect = b.rect
    val corner = CornerRadius(12f * d)
    val s = 0.90f + 0.10f * t
    withTransform({ scale(s, s, Offset(rect.center.x, rect.bottom)) }) {
        drawRoundRect(Color.Black.copy(alpha = 0.07f * t), rect.topLeft + Offset(0f, 3f * d), rect.size, corner)
        drawRoundRect(Color.Black.copy(alpha = 0.14f * t), rect.topLeft + Offset(0f, 1.2f * d), rect.size, corner)
        drawRoundRect(theme.popup.copy(alpha = t), rect.topLeft, rect.size, corner)
        drawRoundRect(theme.text.copy(alpha = (if (theme.dark) 0.10f else 0.07f) * t), rect.topLeft, rect.size, corner, style = Stroke(0.8f * d))

        val ph = rect.height - 2f * b.pad
        val hiLeft = rect.left + b.pad + hiPos * b.cellW
        drawRoundRect(theme.accent.copy(alpha = t), Offset(hiLeft + 1f * d, rect.top + b.pad), Size(b.cellW - 2f * d, ph), CornerRadius(8.5f * d))

        val isUp = shift != ShiftState.OFF
        alts.forEachIndexed { i, a ->
            val cx = rect.left + b.pad + i * b.cellW + b.cellW / 2f
            val raw = if (isUp) a.uppercase() else a.lowercase()
            val sel = (1f - abs(i - hiPos)).coerceIn(0f, 1f)
            val color = lerp(theme.text, theme.onAccent, sel).copy(alpha = t)
            val layout = meas.measure(raw, TextStyle(color = color, fontSize = (rect.height * 0.46f).toSp(), fontFamily = AppFont, fontWeight = FontWeight.Medium))
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, rect.center.y - layout.size.height / 2f))
        }
    }
}

private fun DrawScope.drawTrail(pts: List<TouchPt>, theme: KeyboardTheme, widthPx: Float, now: Long) {
    if (pts.size < 2) return
    val life = 420_000_000f
    for (pass in 0..1) {
        val glow = pass == 0
        for (i in 1 until pts.size) {
            val age = ((now - pts[i].t) / life).coerceIn(0f, 1f)
            val fade = (1f - age)
            if (fade <= 0.02f) continue
            val taper = 0.35f + 0.65f * (i / pts.size.toFloat())
            drawLine(
                color = theme.trail.copy(alpha = (if (glow) 0.22f else 0.92f) * fade),
                start = Offset(pts[i - 1].x, pts[i - 1].y),
                end = Offset(pts[i].x, pts[i].y),
                strokeWidth = widthPx * taper * (if (glow) 2.2f else 1f),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun Rect.deflate(d: Float): Rect = Rect(left + d, top + d, right - d, bottom - d)