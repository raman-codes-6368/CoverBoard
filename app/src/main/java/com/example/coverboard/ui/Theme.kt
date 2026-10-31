package com.example.coverboard.ui.theme

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.coverboard.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════════════════
//  COVERBOARD BRAND COLOR SYSTEM — Derived from CoverBoard's New App Icon
// ═════════════════════════════════════════════════════════════════════════════

val IconRoseAccent = Color(0xFFFF3F68)       // Crimson Rose Badge Accent
val IconRoseLight = Color(0xFFF02A55)        // Mid Rose Gradient
val IconRoseDeep = Color(0xFFCC1642)         // Deep Rose Shadow
val IconSteelBlue = Color(0xFF5E7099)        // Keycap Side Steel Blue
val IconSteelLight = Color(0xFF627396)       // Steel Blue Sheen
val IconKeySheen = Color(0xFFCFE0FF)         // Top Light Key Highlight

val BgBase = Color(0xFF080D1A)               // Dark Navy Deck Background
val SurfaceNavy = Color(0xFF0D152A)          // Surface Base
val SurfaceRaised = Color(0xFF131E3B)         // Raised Surface
val SurfaceRaisedAlt = Color(0xFF1B2A50)      // Alternate Raised Surface
val ContainerLowest = Color(0xFF050812)
val ContainerLow = Color(0xFF090E1F)
val Container = Color(0xFF111B35)
val ContainerHigh = Color(0xFF18264A)
val ContainerHighest = Color(0xFF1E305C)

val BrandPrimary = IconRoseAccent
val BrandSecondary = IconSteelBlue
val BrandAccent = IconKeySheen
val BrandMagenta = IconRoseLight
val BrandOrange = Color(0xFFFF6B3B)
val BrandViolet = Color(0xFF8A6BFF)
val BrandRed = Color(0xFFFF3F55)
val BrandGold = Color(0xFFFFC64D)
val BrandCyan = Color(0xFF00E5FF)

val TextPrimary = Color(0xFFF0F4FF)
val TextSecondary = Color(0xFFB8C5E2)
val TextTertiary = Color(0xFF7B8BAE)

val BrandGradientStart = IconRoseAccent
val BrandGradientEnd = IconRoseLight
val BrandGradient = Brush.linearGradient(listOf(IconSteelBlue, IconRoseAccent, IconKeySheen))
val BrandGradientRose = Brush.linearGradient(listOf(IconRoseAccent, IconRoseLight))

// Backward compatibility aliases
val IrisPrimary = IconRoseAccent
val IrisLightAccent = IconRoseLight
val IrisHighlight = IconKeySheen
val IrisSecondary = IconSteelBlue
val IrisBgDark = BgBase
val IrisBgLight = Color(0xFFF4F4FD)
val IrisTone = IconSteelBlue

val Indigo90 = Color(0xFFE0E6F2)
val Indigo80 = IconKeySheen
val Indigo70 = IconSteelBlue
val Indigo60 = IconRoseAccent
val Indigo50 = IconRoseAccent
val Indigo40 = Color(0xFF1B2A50)
val Indigo20 = Color(0xFF090E1F)
val Mint80 = IconKeySheen
val Mint40 = Color(0xFF00929E)
val Violet80 = IconKeySheen
val Violet40 = Color(0xFF8A6BFF)

val InkBg = BgBase
val InkSurface = SurfaceNavy
val InkSurfaceHigh = SurfaceRaised
val PaperBg = Color(0xFFF4F4FD)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSurfaceHigh = Color(0xFFE6EDFC)

// ═════════════════════════════════════════════════════════════════════════════
//  TYPOGRAPHY — Inter
//  One neutral, precise, highly legible family for the whole product. It is the
//  closest match to the system fonts of the best premium keyboards (clean
//  geometry, open apertures, even rhythm at small sizes).
//
//  Add these static files to app/src/main/res/font/ (lowercase, underscores):
//    inter_light.ttf  inter_regular.ttf  inter_medium.ttf
//    inter_semibold.ttf  inter_bold.ttf
// ═════════════════════════════════════════════════════════════════════════════

val AppFont = FontFamily(
    Font(R.font.inter_light,    weight = FontWeight.Light),
    Font(R.font.inter_regular,  weight = FontWeight.Normal),
    Font(R.font.inter_medium,   weight = FontWeight.Medium),
    Font(R.font.inter_semibold, weight = FontWeight.SemiBold),
    Font(R.font.inter_bold,     weight = FontWeight.Bold),
)

val CinematicTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Bold,
        fontSize = 52.sp, lineHeight = 60.sp, letterSpacing = (-1.4).sp
    ),
    displayMedium = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-1.0).sp
    ),
    displaySmall = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.7).sp
    ),

    headlineLarge = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.25).sp
    ),

    titleLarge = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.05).sp
    ),
    titleSmall = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp
    ),

    bodyLarge = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.12.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp
    ),

    labelLarge = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.7.sp
    )
)

val Typography = CinematicTypography

// ═════════════════════════════════════════════════════════════════════════════
//  LIQUID GLASS & DESIGN SYSTEM MECHANICS (CoverPlay Standard)
// ═════════════════════════════════════════════════════════════════════════════

object GlassTokens {
    val SpringPress = spring<Float>(dampingRatio = 0.55f, stiffness = 520f)
}

@Immutable
data class GlassLook(
    val frostedFill: Color,
    val clearTint: Color,
    val sheen: Color,
    val rimHi: Color,
    val rimLo: Color,
    val topLight: Color,
    val edgeGlow: Color,
    val depth: Color,
)

internal val DefaultGlassLook: GlassLook
    get() = GlassLook(
        frostedFill = SurfaceNavy.copy(alpha = 0.88f),
        clearTint = SurfaceNavy.copy(alpha = 0.72f),
        sheen = Color.White.copy(alpha = 0.12f),
        rimHi = lerp(Color.White, IconKeySheen, 0.45f).copy(alpha = 0.62f),
        rimLo = IconRoseAccent.copy(alpha = 0.14f),
        topLight = Color.White.copy(alpha = 0.20f),
        edgeGlow = IconKeySheen.copy(alpha = 0.12f),
        depth = Color.Black.copy(alpha = 0.28f)
    )

@Composable
fun rememberGlassLook(
    strong: Boolean = false,
    tint: Color? = null,
    dark: Boolean = true
): GlassLook {
    return remember(strong, tint, dark) {
        if (dark) {
            val base = tint ?: SurfaceNavy.copy(alpha = if (strong) 0.96f else 0.88f)
            GlassLook(
                frostedFill = base,
                clearTint = tint ?: SurfaceNavy.copy(alpha = if (strong) 0.88f else 0.72f),
                sheen = Color.White.copy(alpha = 0.08f),
                rimHi = lerp(Color.White, IconKeySheen, 0.50f).copy(alpha = 0.42f),
                rimLo = IconRoseAccent.copy(alpha = 0.08f),
                topLight = Color.White.copy(alpha = 0.15f),
                edgeGlow = IconRoseAccent.copy(alpha = 0.10f),
                depth = Color.Black.copy(alpha = 0.42f)
            )
        } else {
            val base = tint ?: Color.White.copy(alpha = if (strong) 0.95f else 0.88f)
            GlassLook(
                frostedFill = base,
                clearTint = tint ?: Color.White.copy(alpha = if (strong) 0.88f else 0.72f),
                sheen = Color.White.copy(alpha = 0.50f),
                rimHi = Color.White,
                rimLo = Color(0xFFBAC0DC).copy(alpha = 0.35f),
                topLight = Color.White.copy(alpha = 0.60f),
                edgeGlow = IconRoseAccent.copy(alpha = 0.12f),
                depth = IconRoseAccent.copy(alpha = 0.06f)
            )
        }
    }
}

fun Modifier.liquidGlassDecor(
    shape: Shape,
    look: GlassLook,
    accent: Color = IconRoseAccent,
    paintFill: Boolean = true,
    press: () -> Float = { 0f },
    selected: () -> Float = { 0f },
    touch: () -> Offset = { Offset.Unspecified }
): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val rimW = 2.4.dp.toPx()
    val edgeMax = minOf(20.dp.toPx(), minOf(w, h) * 0.34f)
    val edgeSteps = 10
    val edgeWidths = FloatArray(edgeSteps) { i -> edgeMax * (1f - i / edgeSteps.toFloat()) + 1.5.dp.toPx() }
    val diag = Offset(w, h)

    val caustic = Brush.linearGradient(
        listOf(accent.copy(alpha = 0.085f), Color.Transparent, look.rimHi.copy(alpha = 0.035f)),
        start = Offset.Zero, end = diag
    )
    val sheen = Brush.verticalGradient(
        0f to look.sheen.copy(alpha = look.sheen.alpha * 0.55f),
        0.35f to Color.Transparent, startY = 0f, endY = h
    )
    val depth = Brush.verticalGradient(0.58f to Color.Transparent, 1f to look.depth, startY = 0f, endY = h)
    val edge = Brush.linearGradient(
        listOf(look.edgeGlow.copy(alpha = look.edgeGlow.alpha * 0.10f), Color.Transparent, look.edgeGlow.copy(alpha = look.edgeGlow.alpha * 0.06f)),
        start = Offset.Zero, end = diag
    )
    val rim = Brush.linearGradient(
        listOf(look.rimHi, look.rimLo, look.rimHi.copy(alpha = look.rimHi.alpha * 0.55f)),
        start = Offset.Zero, end = diag
    )
    val topRim = Brush.verticalGradient(
        0f to look.topLight.copy(alpha = look.topLight.alpha * 0.55f), 0.12f to Color.Transparent,
        startY = 0f, endY = h
    )

    onDrawWithContent {
        if (paintFill && look.frostedFill.alpha > 0f) drawOutline(outline, look.frostedFill)
        drawOutline(outline, caustic)
        drawOutline(outline, sheen)
        drawOutline(outline, depth)
        clipPath(path) { for (ew in edgeWidths) drawOutline(outline, edge, style = Stroke(ew)) }

        val p = press()
        if (p > 0.001f) {
            val c = touch().let { if (it == Offset.Unspecified) Offset(w / 2f, h / 2f) else it }
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.30f * p), Color.Transparent),
                    center = c, radius = max(w, h) * 0.85f
                )
            )
        }

        drawContent()

        drawOutline(outline, rim, style = Stroke(rimW))
        drawOutline(outline, topRim, style = Stroke(rimW))
        val s = selected()
        if (s > 0.001f) {
            drawOutline(
                outline,
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.95f * s), accent.copy(alpha = 0.40f * s)),
                    startY = 0f, endY = h
                ),
                style = Stroke(rimW * 1.35f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    strong: Boolean = false,
    selected: Boolean = false,
    @Suppress("UNUSED_PARAMETER") glow: Color? = null,
    @Suppress("UNUSED_PARAMETER") elevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    tint: Color? = null,
    @Suppress("UNUSED_PARAMETER") pressedScale: Float = 0.965f,
    content: @Composable BoxScope.() -> Unit
) {
    // Flat tonal card (the name is kept so existing call sites keep working).
    val cs = MaterialTheme.colorScheme
    val clickable = enabled && (onClick != null || onLongClick != null)
    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (!enabled && onClick != null) 0.45f else 1f }
            .clip(shape)
            .background(tint ?: if (strong) cs.surfaceContainerHigh else cs.surfaceContainer)
            .then(if (selected) Modifier.border(1.5.dp, cs.primary, shape) else Modifier)
            .then(
                if (clickable) Modifier.combinedClickable(
                    role = Role.Button,
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick?.let { lc -> { lc() } }
                ) else Modifier
            ),
        content = content
    )
}

fun Modifier.liquidLens(accent: Color = IconRoseAccent, shape: Shape = CircleShape): Modifier = this
    .clip(shape)
    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), accent, accent.copy(alpha = 0.95f))))
    .liquidGlassDecor(shape, DefaultGlassLook, accent, paintFill = false)

/** Flat tonal badge (name kept for compatibility). */
fun Modifier.glassBadge(
    fill: Color,
    @Suppress("UNUSED_PARAMETER") rim: Color = Color.White,
    shape: Shape = CircleShape
): Modifier = this.clip(shape).background(fill)

/** Intentionally a no-op now: hero cards are flat. Kept so older call sites still compile. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.cinematicOverlay(
    accent: Color = IconRoseAccent,
    highlight: Color = IconKeySheen,
    dark: Boolean = true,
    boost: Float = 0f
): Modifier = this

@Composable
fun Modifier.staggerIn(index: Int, stepMs: Int = 45): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtLeast(0) % 8) * stepMs.toLong())
        shown = true
    }
    val p by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "stagger"
    )
    return this.graphicsLayer {
        alpha = p.coerceIn(0f, 1f)
        val sc = 0.90f + 0.10f * p
        scaleX = sc; scaleY = sc
        translationY = (1f - p) * 36f
    }
}

fun Modifier.trackTouch(onDown: (Offset) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onDown(d.position)
    }
}

// ── Expressive shape system — circular & curvy across M3 slots ─────────
val CoverBoardShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small      = RoundedCornerShape(16.dp),
    medium     = RoundedCornerShape(24.dp),
    large      = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(48.dp),
)

private val DarkColorScheme = darkColorScheme(
    primary                 = IconRoseAccent,
    onPrimary               = Color.White,
    primaryContainer        = Color(0xFF3D1824),
    onPrimaryContainer      = Color(0xFFFFB2C3),

    secondary               = Color(0xFF2FBF71),
    onSecondary             = Color.White,
    secondaryContainer      = Color(0xFF16301F),
    onSecondaryContainer    = Color(0xFFA5F0C4),

    tertiary                = BrandViolet,
    onTertiary              = Color.White,

    background              = Color(0xFF0C0E13),
    onBackground            = Color(0xFFF2F4F8),

    surface                 = Color(0xFF12151C),
    onSurface               = Color(0xFFF2F4F8),
    surfaceVariant          = Color(0xFF1D222C),
    onSurfaceVariant        = Color(0xFFA2AABB),
    surfaceTint             = IconRoseAccent,

    surfaceContainerLowest  = Color(0xFF080A0E),
    surfaceContainerLow     = Color(0xFF10131A),
    surfaceContainer        = Color(0xFF161A22),
    surfaceContainerHigh    = Color(0xFF1D222C),
    surfaceContainerHighest = Color(0xFF282E3A),

    outline                 = Color(0xFF4A5263),
    outlineVariant          = Color(0xFF262C38),

    error                   = Color(0xFFFF5A6E),
    onError                 = Color.White,

    scrim                   = Color.Black.copy(alpha = 0.60f),
)

private val LightColorScheme = lightColorScheme(
    primary                 = Color(0xFFE8335C),
    onPrimary               = Color.White,
    primaryContainer        = Color(0xFFFFDDE4),
    onPrimaryContainer      = Color(0xFF5C0019),

    secondary               = Color(0xFF14A05A),
    onSecondary             = Color.White,
    secondaryContainer      = Color(0xFFD5F5E3),
    onSecondaryContainer    = Color(0xFF05361D),

    tertiary                = BrandViolet,
    onTertiary              = Color.White,

    background              = Color(0xFFF4F5F9),
    onBackground            = Color(0xFF14161C),

    surface                 = Color(0xFFFFFFFF),
    onSurface               = Color(0xFF14161C),
    surfaceVariant          = Color(0xFFE9ECF3),
    onSurfaceVariant        = Color(0xFF5B6272),
    surfaceTint             = Color(0xFFE8335C),

    surfaceContainerLowest  = Color(0xFFFFFFFF),
    surfaceContainerLow     = Color(0xFFF8F9FC),
    surfaceContainer        = Color(0xFFFFFFFF),
    surfaceContainerHigh    = Color(0xFFEEF0F5),
    surfaceContainerHighest = Color(0xFFE3E6EE),

    outline                 = Color(0xFF9AA1B0),
    outlineVariant          = Color(0xFFE3E6EE),

    error                   = Color(0xFFD9283F),
    onError                 = Color.White,

    scrim                   = Color.Black.copy(alpha = 0.40f),
)

@Composable
fun CoverBoardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findHostWindow() ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = colorScheme.background.toArgb()
            window.setBackgroundDrawable(ColorDrawable(colorScheme.background.toArgb()))

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = CinematicTypography,
        shapes      = CoverBoardShapes,
        content     = content,
    )
}

private fun Context.findHostWindow(): android.view.Window? {
    var c: Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is Activity) return c.window
        c = c.baseContext
    }
    return null
}