package com.example.coverboard.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.draw.rotate
import com.example.coverboard.ime.KeyboardTheme
import com.example.coverboard.ui.theme.IconRoseDeep
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import com.example.coverboard.Graph
import com.example.coverboard.R
import com.example.coverboard.data.BoolPref
import com.example.coverboard.data.IntPref
import com.example.coverboard.data.P
import com.example.coverboard.data.Pref
import com.example.coverboard.data.Snapshot
import com.example.coverboard.data.StrPref
import com.example.coverboard.ime.ActionKind
import com.example.coverboard.ime.EmojiData
import com.example.coverboard.ime.FieldKind
import com.example.coverboard.ime.HapticEvent
import com.example.coverboard.ime.KeyActions
import com.example.coverboard.ime.KeySpec
import com.example.coverboard.ime.KeyType
import com.example.coverboard.ime.KeyboardSurface
import com.example.coverboard.ime.Languages
import com.example.coverboard.ime.Layouts
import com.example.coverboard.ime.ShiftState
import com.example.coverboard.ime.ThemeSwatch
import com.example.coverboard.ime.Themes
import com.example.coverboard.ime.allToolbarIds
import com.example.coverboard.ime.layoutOptions
import com.example.coverboard.ime.surfaceConfig
import com.example.coverboard.ime.toolbarIcon
import com.example.coverboard.ime.toolbarLabel
import com.example.coverboard.ime.toolbarOrder
import com.example.coverboard.ui.theme.AlertDialog
import com.example.coverboard.ui.theme.BrandGradientRose
import com.example.coverboard.ui.theme.Button
import com.example.coverboard.ui.theme.Checkbox
import com.example.coverboard.ui.theme.HorizontalDivider
import com.example.coverboard.ui.theme.OutlinedTextField
import com.example.coverboard.ui.theme.RadioButton
import com.example.coverboard.ui.theme.Slider
import com.example.coverboard.ui.theme.Switch
import com.example.coverboard.ui.theme.TextButton
import com.example.coverboard.ui.theme.glassScrollbar
import com.example.coverboard.ui.theme.GlassSurface
import com.example.coverboard.ui.theme.IconKeySheen
import com.example.coverboard.ui.theme.IconRoseAccent
import com.example.coverboard.ui.theme.SurfaceRaisedAlt
import com.example.coverboard.ui.theme.cinematicOverlay
import com.example.coverboard.ui.theme.glassBadge
import com.example.coverboard.ui.theme.staggerIn
import org.json.JSONObject
import java.io.File

// ═════════════════════════════════════════════════════════════════════════════
//  COVERBOARD SETTINGS & SCREENS UI
// ═════════════════════════════════════════════════════════════════════════════

private class Category(@DrawableRes val iconRes: Int, val route: String, val title: String, val subtitle: String)

/** The detailed, power-user screens. They live behind "Advanced settings" so the home screen stays simple. */
private val advancedCategories = listOf(
    Category(R.drawable.ic_dl_keyboard, "typing", "Typing & gestures", "Autocorrect strength, spacing, swipe, delete"),
    Category(R.drawable.ic_dl_palette, "themes", "Themes & layout", "Custom colours, layouts, size, one-handed, keys"),
    Category(R.drawable.ic_dl_microphone, "haptics", "Sound & haptics", "Haptic feel and strength, key sounds"),
    Category(R.drawable.ic_dl_layout_navbar_collapse, "toolbar", "Toolbar & extras", "Toolbar order, clipboard, emoji"),
)

fun routeTitle(route: String): String = when (route) {
    "home" -> "CoverBoard"
    "advanced" -> "Advanced settings"
    "languages" -> "Languages"
    "dictionary" -> "Personal dictionary"
    "shortcuts" -> "Text shortcuts"
    "privacy" -> "Privacy & data"
    "about" -> "About"
    "typing" -> "Typing & gestures"
    "themes", "layout" -> "Themes & layout"
    "haptics" -> "Sound & haptics"
    "toolbar" -> "Toolbar & extras"
    else -> "CoverBoard"
}

/** The handful of settings most people ever touch, shown right on the home screen. */
private val essentials = listOf<Item>(
    Toggle(P.AUTOCORRECT, "Autocorrect", "Fix typos as you type"),
    Toggle(P.PREDICTIONS, "Suggestions", "Word and emoji suggestions above the keys"),
    Toggle(P.SWIPE_TYPING, "Swipe typing", "Slide across the letters to type a word"),
    Toggle(P.NUMBER_ROW, "Number row", "Keep the numbers on top of the keyboard"),
    Toggle(P.HAPTIC_ENABLED, "Vibrate on keypress"),
    Toggle(P.SOUND_ENABLED, "Sound on keypress"),
)

private val heightLabels = listOf("Compact", "Normal", "Tall")
private val heightValues = listOf(85, 100, 115)

@Composable
fun HomeScreen(resumeTick: Int, s: Snapshot, navigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    fun checkEnabled(): Boolean = try { imm.enabledInputMethodList.any { it.packageName == ctx.packageName } } catch (_: Exception) { false }
    fun checkSelected(): Boolean = try {
        val cur = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        cur.contains(ctx.packageName)
    } catch (_: Exception) { false }

    var enabled by remember(resumeTick) { mutableStateOf(checkEnabled()) }
    var selected by remember(resumeTick) { mutableStateOf(checkSelected()) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, resumeTick) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                enabled = checkEnabled()
                selected = checkSelected()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val ready = enabled && selected
    val activeLang = Languages.all.firstOrNull { it.code == s[P.LANG_ACTIVE] }?.name ?: "English"
    val langCount = s[P.LANGS_ENABLED].split(",").count { it.isNotBlank() }
    val langSummary = if (langCount > 1) "$activeLang + ${langCount - 1} more" else activeLang
    val currentTheme = s[P.THEME]
    val heightNow = s[P.HEIGHT_PORTRAIT]
    val heightIdx = when { heightNow < 93 -> 0; heightNow <= 107 -> 1; else -> 2 }

    val listState = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxSize().glassScrollbar(listState), state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item {
            if (ready) ReadyCard() else SetupCard(
                enabled = enabled,
                onEnable = { ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                onChoose = { imm.showInputMethodPicker() },
            )
        }
        item {
            var text by remember { mutableStateOf("") }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Try your keyboard here") }, shape = RoundedCornerShape(18.dp),
            )
        }
        item {
            SectionTitle("Essentials")
            SectionCard {
                essentials.forEachIndexed { i, row ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ItemRow(row, s)
                }
            }
        }
        item {
            SectionTitle("Look & feel")
            SectionCard {
                Column(Modifier.padding(top = 14.dp, bottom = 10.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RowTitle("Theme")
                        Spacer(Modifier.weight(1f))
                        Text(
                            Themes.displayName(currentTheme).substringBefore(" ("),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(Themes.ids.filter { it != "custom" || currentTheme == "custom" }) { id ->
                            ThemeChip(themeFor(id, s), currentTheme == id, Themes.displayName(id).substringBefore(" (")) {
                                Graph.haptics.play(HapticEvent.MODE, view)
                                Graph.settings.set(P.THEME, id)
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    RowTitle("Keyboard height")
                    Spacer(Modifier.height(10.dp))
                    Segmented(heightLabels, heightIdx) { i ->
                        Graph.haptics.play(HapticEvent.MODE, view)
                        Graph.settings.set(P.HEIGHT_PORTRAIT, heightValues[i])
                        Graph.settings.set(P.HEIGHT_LANDSCAPE, heightValues[i])
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().clickable { navigate("themes") }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("More themes, colours & layouts", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.primary)
                    Icon(painterResource(R.drawable.ic_chevron_right_custom), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            SectionTitle("More")
            NavCard {
                NavCardRow("Languages", langSummary, { navigate("languages") }) { NavIcon(R.drawable.ic_dl_language) }
                NavDivider()
                NavCardRow("Personal dictionary", "Learned words and text shortcuts", { navigate("dictionary") }) { NavIcon(R.drawable.ic_dl_keyboard) }
                NavDivider()
                NavCardRow("Privacy & data", "Incognito, encryption, backup", { navigate("privacy") }) { NavIcon(R.drawable.ic_dl_shield) }
            }
            Spacer(Modifier.height(12.dp))
            NavCard {
                NavCardRow("Advanced settings", "Fine-tune typing, haptics, toolbar and more", { navigate("advanced") }) { NavIcon(R.drawable.ic_dl_layout_navbar_collapse) }
                NavDivider()
                NavCardRow("About", "Version and app info", { navigate("about") }) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Everything you type stays on this phone.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun AdvancedScreen(navigate: (String) -> Unit) {
    val listState = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxSize().glassScrollbar(listState), state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item {
            Text(
                "Everything here is optional. The defaults already work well for most people.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(10.dp))
            NavCard {
                advancedCategories.forEachIndexed { i, c ->
                    if (i > 0) NavDivider()
                    NavCardRow(c.title, c.subtitle, { navigate(c.route) }) { NavIcon(c.iconRes) }
                }
            }
        }
    }
}

// ---- home building blocks ---------------------------------------------------------

@Composable
private fun SetupCard(enabled: Boolean, onEnable: () -> Unit, onChoose: () -> Unit) {
    val step = if (enabled) 2 else 1
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BrandGradientRose)

            .padding(22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).glassBadge(fill = Color.White.copy(alpha = 0.20f), rim = Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_dl_keyboard), null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Welcome to CoverBoard", color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Step $step of 2", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) { i ->
                    Box(
                        Modifier.weight(1f).height(4.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i < step) 0.95f else 0.28f))
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (enabled) "Make it your keyboard" else "Turn on CoverBoard",
                color = Color.White, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (enabled) "Pick CoverBoard from the list so it appears whenever you type."
                else "Switch it on in your phone's keyboard settings. It only takes a moment.",
                color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().clip(CircleShape).background(Color.White)
                    .clickable { if (enabled) onChoose() else onEnable() }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (enabled) "Choose CoverBoard" else "Enable CoverBoard",
                    color = IconRoseDeep, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun ReadyCard() {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).glassBadge(
                    fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    rim = MaterialTheme.colorScheme.primary,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("CoverBoard is ready", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Private and on-device. Nothing you type leaves this phone.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun themeFor(id: String, s: Snapshot): KeyboardTheme = when (id) {
    "auto" -> Themes.builtIn[0].copy(id = "auto", name = "System")
    "custom" -> Themes.custom(s)
    else -> Themes.builtIn.firstOrNull { it.id == id } ?: Themes.builtIn[0]
}

/** Mini keyboard preview used in the home theme picker (label uses the app's text colour so light themes stay readable). */
@Composable
private fun ThemeChip(theme: KeyboardTheme, selected: Boolean, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(92.dp).height(62.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(theme.bgTop, theme.bgBottom)))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) theme.accent else theme.text.copy(alpha = 0.15f),
                    RoundedCornerShape(14.dp),
                )
                .padding(7.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(if (r == 1) 6 else 7) {
                            Box(Modifier.size(width = 8.dp, height = 9.dp).clip(RoundedCornerShape(2.dp)).background(theme.key))
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).size(width = 18.dp, height = 9.dp).clip(RoundedCornerShape(3.dp)).background(theme.accent))
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label, maxLines = 1, fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(bg)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                )
            }
        }
    }
}

@Composable
private fun NavCard(content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun NavDivider() =
    HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun NavIcon(@DrawableRes res: Int) =
    Icon(painterResource(res), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))

@Composable
private fun NavCardRow(title: String, subtitle: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).glassBadge(
                fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                rim = MaterialTheme.colorScheme.primary,
            ),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right_custom), null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(18.dp),
        )
    }
}

/** Collapsible card: keeps rarely used controls out of sight until asked for. */
@Composable
private fun Disclosure(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 90f else 0f, label = "disclosure")
    SectionCard {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    RowTitle(title)
                    if (subtitle != null) RowSummary(subtitle)
                }
                Icon(Icons.Rounded.ChevronRight, null, Modifier.rotate(rot))
            }
            if (open) content()
        }
    }
}

@Composable
fun LanguagesScreen(s: Snapshot, navigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val enabled = s[P.LANGS_ENABLED].split(",").filter { it.isNotBlank() }
    val active = s[P.LANG_ACTIVE]
    var importFor by remember { mutableStateOf("en") }
    var importTick by remember { mutableStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val lines = text.lineSequence().take(200_000).joinToString("\n")
                File(ctx.filesDir, "dict_$importFor.txt").writeText(lines)
                Graph.engine.invalidate()
                importTick++
            } catch (_: Exception) {
            }
        }
    }
    val mine = Languages.all.filter { it.code in enabled }
    val others = Languages.all.filter { it.code !in enabled }

    val listState = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().glassScrollbar(listState), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
        item {
            Text(
                "Tap a language to show it on the keyboard. Long-press the space bar or tap the globe key to switch while typing.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
            SectionTitle("Your languages")
            SectionCard {
                mine.forEachIndexed { i, lang ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    val isActive = lang.code == active
                    val bundled = remember(lang.code) { Graph.engine.hasBundled(lang.code) }
                    val imported = remember(lang.code, importTick) { File(ctx.filesDir, "dict_${lang.code}.txt").exists() }
                    Row(
                        Modifier.fillMaxWidth().clickable { Graph.settings.set(P.LANG_ACTIVE, lang.code) }
                            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isActive, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lang.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when {
                                    !bundled && !imported -> "No dictionary yet – add a word list below"
                                    isActive -> "Shown on the keyboard"
                                    else -> "Tap to show on the keyboard"
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (mine.size > 1) {
                            TextButton(onClick = {
                                val list = enabled - lang.code
                                if (list.isNotEmpty()) {
                                    Graph.settings.set(P.LANGS_ENABLED, list.joinToString(","))
                                    if (isActive) Graph.settings.set(P.LANG_ACTIVE, list.first())
                                }
                            }) { Text("Remove") }
                        }
                    }
                }
            }
            if (others.isNotEmpty()) {
                SectionTitle("Add a language")
                SectionCard {
                    others.forEachIndexed { i, lang ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        val add = { Graph.settings.set(P.LANGS_ENABLED, (enabled + lang.code).distinct().joinToString(",")) }
                        Row(
                            Modifier.fillMaxWidth().clickable { add() }.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(lang.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { add() }) { Text("Add") }
                        }
                    }
                }
            }
            if (enabled.size > 1) {
                SectionTitle("Multiple languages")
                SectionCard {
                    ItemRow(
                        Toggle(P.MULTILINGUAL, "Type in all my languages at once", "Suggestions and autocorrect use every language you've added"),
                        s,
                    )
                }
            }
            SectionTitle("More")
            Disclosure("Word lists", "Import your own dictionary files") {
                mine.forEach { lang ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(lang.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { importFor = lang.code; picker.launch(arrayOf("text/*", "application/octet-stream")) }) {
                            Text("Import .txt")
                        }
                    }
                }
                Text(
                    "A word list is a plain text file with one word per line, most common first. Imported lists stay on this phone.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            NavCard {
                NavCardRow("Personal dictionary", "Learned words, blocked words, shortcuts", { navigate("dictionary") }) { NavIcon(R.drawable.ic_dl_keyboard) }
            }
        }
    }
}

@Composable
fun ToolbarScreen(s: Snapshot) {
    val hidden = s[P.TOOLBAR_HIDDEN].split(",").filter { it.isNotBlank() }.toSet()
    val visible = toolbarOrder(s)
    val full = visible + allToolbarIds().filter { it !in visible }

    fun save(order: List<String>, hide: Set<String>) {
        Graph.settings.set(P.TOOLBAR_ORDER, order.joinToString(","))
        Graph.settings.set(P.TOOLBAR_HIDDEN, hide.joinToString(","))
    }
    SettingsList(
        listOf<Item>(
            Header("Toolbar"),
            Toggle(P.TOOLBAR_ENABLED, "Show toolbar button", "The grid button next to the suggestions"),
            Toggle(P.TOOLBAR_OPEN_DEFAULT, "Open toolbar by default", null, P.TOOLBAR_ENABLED),
        ) + Schema.clipboard + Schema.emoji,
        s,
        resetExtra = listOf(P.TOOLBAR_ORDER, P.TOOLBAR_HIDDEN),
        extras = mapOf<String, @Composable () -> Unit>("Toolbar" to {
            SectionTitle("Shortcuts & order")
            SectionCard {
                full.forEachIndexed { i, id ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(toolbarIcon(id)), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(toolbarLabel(id), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Icon(Icons.Rounded.KeyboardArrowUp, "Up", Modifier.clip(CircleShape).clickable(enabled = i > 0) {
                            val l = full.toMutableList(); l.add(i - 1, l.removeAt(i)); save(l, hidden)
                        }.padding(6.dp))
                        Icon(Icons.Rounded.KeyboardArrowDown, "Down", Modifier.clip(CircleShape).clickable(enabled = i < full.size - 1) {
                            val l = full.toMutableList(); l.add(i + 1, l.removeAt(i)); save(l, hidden)
                        }.padding(6.dp))
                        Switch(checked = id !in hidden, onCheckedChange = { on ->
                            save(full, if (on) hidden - id else hidden + id)
                        }, compact = true)
                    }
                }
            }
            if (s[P.TOOLBAR_ORDER] != P.TOOLBAR_ORDER.default || s[P.TOOLBAR_HIDDEN] != P.TOOLBAR_HIDDEN.default) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        Graph.settings.set(P.TOOLBAR_ORDER, P.TOOLBAR_ORDER.default)
                        Graph.settings.set(P.TOOLBAR_HIDDEN, P.TOOLBAR_HIDDEN.default)
                    }) { Text("Reset order") }
                }
            }
        }),
    )
}

@Composable
fun ThemesScreen(s: Snapshot) {
    val ctx = LocalContext.current
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        GridCells.Adaptive(100.dp), Modifier.fillMaxSize().glassScrollbar(gridState), state = gridState,
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { KeyboardPreview(s) }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Choose a theme") }
        items(Themes.ids) { id ->
            val theme = when (id) {
                "auto" -> Themes.builtIn[0].copy(id = "auto", name = "System")
                "custom" -> Themes.custom(s)
                else -> Themes.builtIn.first { it.id == id }
            }
            ThemeSwatch(theme, s[P.THEME] == id, Themes.displayName(id).substringBefore(" (")) { Graph.settings.set(P.THEME, id) }
        }
        if (s[P.THEME] == "custom") {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionTitle("Custom colours")
                    SectionCard {
                        ColorRow("Background", P.CUSTOM_BG, s)
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ColorRow("Keys", P.CUSTOM_KEY, s)
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ColorRow("Accent", P.CUSTOM_ACCENT, s)
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ColorRow("Text", P.CUSTOM_TEXT, s)
                    }
                }
            }
        }
        val sections = ArrayList<Pair<String?, MutableList<Item>>>()
        for (it in Schema.layout) {
            if (it is Header) sections.add(it.title to ArrayList()) else sections.lastOrNull()?.second?.add(it)
        }
        sections.forEach { (title, rows) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    if (title != null) SectionTitle(title)
                    SectionCard {
                        rows.forEachIndexed { i, row ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            ItemRow(row, s)
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ResetPageButton(
                    prefsOf(Schema.layout) + listOf(P.THEME, P.CUSTOM_BG, P.CUSTOM_KEY, P.CUSTOM_ACCENT, P.CUSTOM_TEXT),
                    s, "Reset themes & layout",
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SettingsListInline(items: List<Item>, s: Snapshot) {
    Column {
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            InlineRow(item, s)
        }
    }
}

@Composable
private fun InlineRow(item: Item, s: Snapshot) {
    when (item) {
        is Toggle -> Row(
            Modifier.fillMaxWidth().clickable { Graph.settings.set(item.pref, !s[item.pref]) }.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = s[item.pref], onCheckedChange = null)
        }
        is SliderItem -> {
            val stored = s[item.pref]
            var v by remember { mutableStateOf(stored.toFloat()) }
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row {
                    Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("${v.toInt()}${item.unit}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                Slider(
                    value = v, onValueChange = { v = it; Graph.settings.set(item.pref, it.toInt()) },
                    valueRange = item.range.first.toFloat()..item.range.last.toFloat(),
                )
            }
        }
        else -> {}
    }
}

@Composable
private fun ColorRow(label: String, pref: IntPref, s: Snapshot) {
    var open by remember { mutableStateOf(false) }
    val argb = s[pref]
    val hsv = remember(pref) { FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) } }
    var h by remember(pref) { mutableStateOf(hsv[0]) }
    var sat by remember(pref) { mutableStateOf(hsv[1]) }
    var v by remember(pref) { mutableStateOf(hsv[2]) }
    fun push() = Graph.settings.set(pref, android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)))
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(argb)))
            Spacer(Modifier.width(14.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("#%06X".format(argb and 0xFFFFFF), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (argb != pref.default) {
                Spacer(Modifier.width(8.dp))
                ResetButton {
                    Graph.settings.set(pref, pref.default)
                    val d = FloatArray(3); android.graphics.Color.colorToHSV(pref.default, d)
                    h = d[0]; sat = d[1]; v = d[2]
                }
            }
        }
        if (open) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp)) {
                Text("Hue", style = MaterialTheme.typography.labelMedium)
                Slider(h, { h = it; push() }, valueRange = 0f..360f)
                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                Slider(sat, { sat = it; push() }, valueRange = 0f..1f)
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Slider(v, { v = it; push() }, valueRange = 0f..1f)
            }
        }
    }
}

@Composable
fun DictionaryScreen(s: Snapshot, navigate: (String) -> Unit) {
    val rev by Graph.user.revision.collectAsState()
    val learned = remember(rev) { Graph.user.words.filter { it.value >= 2 }.entries.sortedByDescending { it.value }.map { it.key } }
    val blocked = remember(rev) { Graph.user.blocked.sorted() }
    var newWord by remember { mutableStateOf("") }
    var newBlock by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().glassScrollbar(listState), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionCard {
                Row(
                    Modifier.fillMaxWidth().clickable { navigate("shortcuts") }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Text shortcuts", style = MaterialTheme.typography.bodyLarge)
                        Text("${Graph.user.shortcuts.size} saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
            SectionTitle("Add a word")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newWord, { newWord = it }, Modifier.weight(1f), singleLine = true, label = { Text("New word") }, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (newWord.isNotBlank()) { Graph.user.noteWord(newWord.trim().lowercase(), force = true); newWord = "" } }) { Text("Add") }
            }
            SectionTitle("Learned words (${learned.size})")
        }
        if (learned.isEmpty()) {
            item {
                Text(
                    "Words you type often will appear here. Nothing is learned in incognito mode or in password fields.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        items(learned.take(150)) { w -> WordRow(w, "Forget") { Graph.user.forgetWord(w) } }
        item {
            SectionTitle("Blocked words")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newBlock, { newBlock = it }, Modifier.weight(1f), singleLine = true, label = { Text("Never suggest…") }, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (newBlock.isNotBlank()) { Graph.user.block(newBlock); newBlock = "" } }) { Text("Block") }
            }
        }
        items(blocked) { w -> WordRow(w, "Unblock") { Graph.user.unblock(w) } }
    }
}

@Composable
private fun WordRow(word: String, action: String, onClick: () -> Unit) {
    SectionCard {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(word, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
fun ShortcutsScreen() {
    val rev by Graph.user.revision.collectAsState()
    val list = remember(rev) { Graph.user.shortcuts.entries.sortedBy { it.key }.map { it.key to it.value } }
    var trigger by remember { mutableStateOf("") }
    var expansion by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().glassScrollbar(listState), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Type the shortcut and press space; CoverBoard replaces it with the full text.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(trigger, { trigger = it.replace(" ", "") }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Shortcut (e.g. omw)") }, shape = RoundedCornerShape(16.dp))
            OutlinedTextField(expansion, { expansion = it }, Modifier.fillMaxWidth(), label = { Text("Expands to") }, shape = RoundedCornerShape(16.dp))
            Button(
                onClick = {
                    if (trigger.isNotBlank() && expansion.isNotBlank()) {
                        Graph.user.setShortcut(trigger, expansion); trigger = ""; expansion = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save shortcut") }
            Spacer(Modifier.height(8.dp))
        }
        items(list) { (k, v) ->
            SectionCard {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(k, style = MaterialTheme.typography.titleMedium)
                        Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.Close, "Delete", Modifier.clip(CircleShape).clickable { Graph.user.removeShortcut(k) }.padding(10.dp))
                }
            }
        }
    }
}

@Composable
fun PrivacyScreen(s: Snapshot) {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val hasInternet = remember {
        @Suppress("DEPRECATION")
        val perms = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
        perms?.contains("android.permission.INTERNET") == true
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val o = JSONObject()
                o.put("app", "CoverBoard backup")
                o.put("settings", Graph.settings.exportJson())
                o.put("data", Graph.user.toJson())
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(o.toString(2).toByteArray()) }
            } catch (_: Exception) {
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@rememberLauncherForActivityResult
                val o = JSONObject(text)
                o.optJSONObject("settings")?.let { Graph.settings.importJson(it) }
                o.optJSONObject("data")?.let { Graph.user.merge(it); Graph.user.save(); Graph.user.bump() }
            } catch (_: Exception) {
            }
        }
    }
    val items = listOf(
        Header("What CoverBoard learns"),
        Toggle(P.LEARN, "Learn from my typing", "Remembers words and word pairs on this phone only"),
        Toggle(P.AUTO_INCOGNITO, "Auto-incognito", "Never learn in password fields or apps that ask for private typing"),
        Toggle(P.INCOGNITO, "Incognito mode", "Nothing you type is learned or added to clipboard history"),
        Toggle(P.ENCRYPT, "Encrypt stored data", "Seals learned words with a key kept in the Android Keystore"),
        Toggle(P.SECURE_APP, "Block screenshots in this app"),
        Header("Your data"),
        ActionItem("Export backup", "Settings and dictionary as a file you control") { export.launch("coverboard-backup.json") },
        ActionItem("Import backup", "Merge a backup file into this phone") { import.launch(arrayOf("application/json", "text/*", "*/*")) },
        ActionItem("Clear learned words", "Forget everything learned from typing", true) {
            pending = "Forget all learned words and word pairs?" to { Graph.user.clearLearned() }
        },
        ActionItem("Clear clipboard history", "Including pinned items", true) {
            pending = "Delete clipboard history, including pins?" to { Graph.clipboard.clearAll() }
        },
        ActionItem("Delete all my data", "Words, shortcuts, blocklist and pins", true) {
            pending = "Permanently delete everything CoverBoard stores about you?" to { Graph.user.wipeEverything(); Graph.clipboard.clearAll() }
        },
        ActionItem("Reset all settings", "Back to factory defaults", true) {
            pending = "Reset every setting to its default?" to { Graph.settings.resetAll() }
        },
    )
    SettingsList(
        items, s, resettable = false,
        header = {
            GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (!hasInternet) Icons.Rounded.CheckCircle else Icons.Rounded.Info, null,
                        tint = if (!hasInternet) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(34.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(if (!hasInternet) "No network permission" else "Network permission found!", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (!hasInternet) "Checked just now: this app does not hold the INTERNET permission, so Android blocks it from going online."
                            else "This build requests INTERNET. Official CoverBoard builds never do.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
    pending?.let { (msg, action) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Are you sure?") }, text = { Text(msg) },
            confirmButton = { TextButton(onClick = { action(); pending = null }) { Text("Yes, do it") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

private const val DEVELOPER_NAME = "Raman Singh"
private const val DEVELOPER_UPI_ID = "raman6369@fam"

private fun buildUpiPayString(payeeUpiId: String, payeeName: String): String {
    val encodedName = java.net.URLEncoder.encode(payeeName, "UTF-8")
    return "upi://pay?pa=$payeeUpiId&pn=$encodedName&cu=INR"
}

@Composable
fun DeveloperCard(context: Context) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    SectionCard {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(12.dp, CircleShape, spotColor = IconRoseAccent.copy(alpha = 0.4f))
                        .clip(CircleShape)
                        .background(BrandGradientRose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_developer),
                        contentDescription = "Developer",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        DEVELOPER_NAME,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Independent Developer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                onClick = {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(DEVELOPER_UPI_ID))
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    android.widget.Toast.makeText(context, "UPI ID copied: $DEVELOPER_UPI_ID", android.widget.Toast.LENGTH_SHORT).show()
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, IconRoseAccent.copy(alpha = 0.35f)),
                modifier = Modifier.clip(CircleShape)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Support via UPI: $DEVELOPER_UPI_ID",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            val upiUri = buildUpiPayString(DEVELOPER_UPI_ID, DEVELOPER_NAME)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                            context.startActivity(Intent.createChooser(intent, "Pay with UPI"))
                        } catch (_: Exception) {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(DEVELOPER_UPI_ID))
                            android.widget.Toast.makeText(context, "UPI ID copied: $DEVELOPER_UPI_ID", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pay with UPI", fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/raman-codes-6368"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("GitHub Profile", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    val s = rememberSettings()
    val info = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
    val version = info?.versionName ?: "1.0"
    val build = info?.let { runCatching { PackageInfoCompat.getLongVersionCode(it).toString() }.getOrNull() } ?: "1"
    val hasInternet = remember {
        runCatching {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.contains("android.permission.INTERNET") == true
        }.getOrDefault(false)
    }
    val features = listOf(
        Triple(R.drawable.ic_dl_keyboard, "Smart typing", "Autocorrect, next-word and emoji prediction that learn from you"),
        Triple(R.drawable.ic_dl_device_mobile_vibration, "Swipe & gestures", "Gesture typing, swipe cursor and whole-word delete"),
        Triple(R.drawable.ic_dl_microphone, "Haptics & sound", "Precision haptics and CoverBoard's own key sounds"),
        Triple(R.drawable.ic_dl_palette, "Themes & layouts", "Premium themes, custom colours, one-handed and split modes"),
        Triple(R.drawable.ic_dl_shield, "Private by design", "Everything stays on this phone. No accounts, no cloud, no tracking"),
    )
    val listState = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().glassScrollbar(listState), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BrandGradientRose)
                    .padding(20.dp),
            ) {
                Column {
                    Box(Modifier.size(38.dp).glassBadge(fill = Color.White.copy(alpha = 0.20f), rim = Color.White), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_dl_keyboard), null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("CoverBoard", color = Color.White, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Version $version", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A private, open-source keyboard with gesture typing, on-device prediction and precision haptics.",
                        color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            SectionTitle("Developer Profile")
            DeveloperCard(ctx)
        }
        item {
            SectionTitle("Highlights")
            SectionCard {
                features.forEachIndexed { i, (icon, title, text) ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) { RowTitle(title); RowSummary(text) }
                    }
                }
            }
        }
        item {
            SectionTitle("App info")
            SectionCard {
                val rows = listOf(
                    "Version" to version,
                    "Build" to build,
                    "Package" to ctx.packageName,
                    "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    "Internet access" to if (hasInternet) "Requested" else "None (Private)",
                )
                rows.forEachIndexed { i, (k, v) ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Everything you type stays on this phone.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---- declarative settings model --------------------------------------------

sealed interface Item
class Header(val title: String) : Item
class Toggle(val pref: BoolPref, val title: String, val summary: String? = null, val dependsOn: BoolPref? = null) : Item
class SliderItem(
    val pref: IntPref, val title: String, val summary: String? = null, val range: IntRange,
    val unit: String = "", val zeroLabel: String? = null, val dependsOn: BoolPref? = null,
) : Item

class ChoiceItem(val pref: StrPref, val title: String, val options: List<Pair<String, String>>, val summary: String? = null) : Item
class Note(val text: String) : Item
class ActionItem(val title: String, val summary: String? = null, val destructive: Boolean = false, val onClick: (Context) -> Unit) : Item

object Schema {
    val typing = listOf(
        Header("Corrections & predictions"),
        Toggle(P.AUTOCORRECT, "Autocorrect", "Fix typos when you press space or punctuation. Tap backspace right after to undo."),
        ChoiceItem(
            P.AUTOCORRECT_LEVEL, "Autocorrect strength",
            listOf("low" to "Gentle – only obvious typos", "medium" to "Balanced", "high" to "Aggressive – fixes more"),
        ),
        Toggle(P.PREDICTIONS, "Show suggestions", "Word suggestions above the keys"),
        Toggle(P.NEXT_WORD, "Next-word prediction", "Suggest the next word before you start typing it", P.PREDICTIONS),
        Toggle(P.EMOJI_PREDICTION, "Emoji prediction", "Suggest an emoji that fits the last word", P.PREDICTIONS),
        Toggle(P.SHORTCUTS_ENABLED, "Text shortcuts", "Expand your own shortcuts such as \"omw\" → \"On my way!\""),
        Header("Capitalisation & spacing"),
        Toggle(P.AUTO_CAPS, "Auto-capitalise", "Capital letter at the start of sentences"),
        Toggle(P.AUTO_CAP_I, "Capitalise \"I\"", "Turns a lone \"i\" into \"I\""),
        Toggle(P.CAPS_DOUBLE_TAP, "Double-tap shift for caps lock"),
        Toggle(P.AUTO_SPACE_PUNCT, "Auto-space after punctuation", "Adds a space after , ! ? ; : when you keep typing"),
        Toggle(P.DOUBLE_SPACE_PERIOD, "Double-space full stop", "Tap space twice to insert \". \""),
        Toggle(P.AUTO_SPACE_SUGGESTION, "Space after suggestion", "Add a space when you tap a suggestion"),
        Toggle(P.SMART_PUNCT, "Smart punctuation", "Removes the auto-space before . , ! ? ; :"),
        Header("Keys"),
        Toggle(P.HOLD_DELETE_WORDS, "Hold delete to remove whole words", "Holding delete removes words instead of individual letters"),
        SliderItem(P.LONG_PRESS_MS, "Long-press delay", "How long to hold for accents and symbols", 150..700, " ms"),
        Toggle(P.KEY_POPUP, "Key pop-ups", "Magnified preview above the pressed key"),
    )

    private val gesturesOnly = listOf(
        Header("Gesture typing"),
        Toggle(P.SWIPE_TYPING, "Swipe to type", "Slide your finger across letters to write a word"),
        Toggle(P.FLOW_SPACE, "Flow through space", "Swipe through the space bar to keep going without lifting", P.SWIPE_TYPING),
        Toggle(P.GESTURE_TRAIL, "Show gesture trail", null, P.SWIPE_TYPING),
        SliderItem(P.TRAIL_WIDTH, "Trail thickness", null, 2..12, " dp", dependsOn = P.GESTURE_TRAIL),
        Header("Swipe actions"),
        Toggle(P.SWIPE_DELETE, "Swipe left on delete", "Slide left on the delete key to remove whole words"),
        Toggle(P.SPACE_CURSOR, "Swipe on space bar", "Slide along the space bar to move the cursor"),
        SliderItem(P.CURSOR_SPEED, "Cursor speed", null, 1..100, "%", dependsOn = P.SPACE_CURSOR),
    )

    val typingAndGestures: List<Item> get() = typing + gesturesOnly

    val layout = listOf(
        Header("Layout"),
        ChoiceItem(
            P.LAYOUT, "Keyboard layout",
            listOf("auto" to "Automatic (per language)", "qwerty" to "QWERTY", "qwertz" to "QWERTZ", "azerty" to "AZERTY", "dvorak" to "Dvorak", "colemak" to "Colemak"),
        ),
        Toggle(P.NUMBER_ROW, "Number row"),
        Toggle(P.ARROW_KEYS, "Arrow keys", "Left / right cursor keys next to the space bar"),
        Toggle(P.EMOJI_KEY, "Emoji key"),
        Toggle(P.GLOBE_KEY, "Language / keyboard switch key"),
        Toggle(P.HINTS, "Key hints", "Show the number or symbol you get by long-pressing"),
        Toggle(P.LETTER_SYMBOLS, "Symbols on letter keys", "Add symbols to the long-press accents, e.g. hold A for @"),
        Toggle(P.LOWERCASE_KEYS, "Lowercase letters", "Show lowercase letters until shift is on"),
        Header("Size & one-handed mode"),
        ChoiceItem(
            P.KB_MODE, "Keyboard mode",
            listOf("standard" to "Full width", "left" to "One-handed (left)", "right" to "One-handed (right)", "split" to "Split"),
        ),
        SliderItem(P.HEIGHT_PORTRAIT, "Height – portrait", null, 70..140, "%"),
        SliderItem(P.HEIGHT_LANDSCAPE, "Height – landscape", null, 70..140, "%"),
        SliderItem(P.ONEHAND_WIDTH, "One-handed width", null, 55..95, "%"),
        SliderItem(P.SPLIT_GAP, "Split gap", null, 8..120, " dp"),
        Header("Key style"),
        Toggle(P.KEY_BORDERS, "Key shadows & highlights"),
        SliderItem(P.KEY_SPACING, "Key spacing", null, 0..10, " dp"),
        SliderItem(P.KEY_CORNER, "Corner roundness", null, 0..24, " dp"),
    )

    val soundHaptics = listOf(
        Header("Haptic feedback"),
        Toggle(P.HAPTIC_ENABLED, "Vibrate on keypress"),
        ChoiceItem(
            P.HAPTIC_MODE, "Haptic engine",
            listOf("custom" to "CoverBoard precision haptics", "system" to "Follow system touch feedback"),
            "Precision haptics use your phone's fine-grained vibration motor.",
        ),
        ChoiceItem(
            P.HAPTIC_PROFILE, "Feel",
            listOf("soft" to "Soft tick", "crisp" to "Crisp tap", "firm" to "Firm click"),
        ),
        SliderItem(P.HAPTIC_STRENGTH, "Strength", null, 5..100, "%", dependsOn = P.HAPTIC_ENABLED),
        SliderItem(P.HAPTIC_DURATION, "Pulse length (older phones)", "Used when your phone lacks precise haptics", 3..40, " ms", dependsOn = P.HAPTIC_ENABLED),
        Toggle(P.HAPTIC_SPECIAL, "Stronger for special keys", "Shift, delete, enter, space…", P.HAPTIC_ENABLED),
        Toggle(P.HAPTIC_LONGPRESS, "Long-press feedback", null, P.HAPTIC_ENABLED),
        Toggle(P.HAPTIC_GESTURE, "Gesture typing feedback", "Soft ticks while you swipe, a click on every word", P.HAPTIC_ENABLED),
        Toggle(P.HAPTIC_CURSOR, "Cursor & delete feedback", "Ticks when moving the cursor or deleting words", P.HAPTIC_ENABLED),
        Header("Sound"),
        Toggle(P.SOUND_ENABLED, "Sound on keypress", "CoverBoard's own key sounds"),
        SliderItem(P.SOUND_VOLUME, "Volume", null, 1..100, "%", dependsOn = P.SOUND_ENABLED),
        Toggle(P.SOUND_SPECIAL, "Different sounds for special keys", null, P.SOUND_ENABLED),
    )

    val clipboard = listOf(
        Header("Clipboard manager"),
        Toggle(P.CLIP_ENABLED, "Keep clipboard history", "Remembers things you copy while CoverBoard is your keyboard"),
        Toggle(P.CLIP_SUGGEST, "Suggest copied text", "Shows a \"Paste\" suggestion after you copy", P.CLIP_ENABLED),
        Toggle(P.CLIP_SKIP_SENSITIVE, "Ignore sensitive copies", "Skips items apps mark as sensitive, e.g. passwords", P.CLIP_ENABLED),
        SliderItem(P.CLIP_EXPIRE_MIN, "Auto-clear after", "Pinned items are never cleared", 0..1440, " min", zeroLabel = "Never", dependsOn = P.CLIP_ENABLED),
        SliderItem(P.CLIP_MAX, "Items to keep", null, 5..100, "", dependsOn = P.CLIP_ENABLED),
        Note("Clipboard history is held in memory only. Only the items you pin are stored on the phone."),
    )

    val emoji = listOf(
        Header("Emoji"),
        ChoiceItem(P.SKIN_TONE, "Skin tone", EmojiData.toneNames, "Applies to hand and people emoji."),
        ActionItem("Clear recent emoji") { Graph.settings.set(P.EMOJI_RECENTS, "") },
    )
}

// ---- rendering -------------------------------------------------------------------

@Composable
fun SettingsList(
    items: List<Item>, s: Snapshot, modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null, footer: (@Composable () -> Unit)? = null,
    extras: Map<String, @Composable () -> Unit> = emptyMap(),
    resettable: Boolean = true, resetExtra: List<Pref<*>> = emptyList(),
) {
    val groups = remember(items) {
        val out = ArrayList<Pair<String?, MutableList<Item>>>()
        for (it in items) {
            if (it is Header) out.add(it.title to ArrayList()) else {
                if (out.isEmpty()) out.add(null to ArrayList())
                out.last().second.add(it)
            }
        }
        out
    }
    val listState = rememberLazyListState()
    LazyColumn(modifier.fillMaxSize().glassScrollbar(listState), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp)) {
        if (header != null) item { header() }
        items(groups) { (title, rows) ->
            if (title != null) SectionTitle(title)
            SectionCard {
                rows.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ItemRow(item, s)
                }
            }
            if (title != null) extras[title]?.invoke()
        }
        if (footer != null) item { Spacer(Modifier.height(12.dp)); footer() }
        if (resettable) item { ResetPageButton(prefsOf(items) + resetExtra, s) }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
fun SectionCard(content: @Composable () -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun RowTitle(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))

@Composable
private fun RowSummary(text: String) = Text(
    text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** Small round "back to default" button shown only while a value differs from its default. */
@Composable
private fun ResetButton(onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Refresh, "Reset to default", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}

private fun prefsOf(items: List<Item>): List<Pref<*>> = items.mapNotNull { i ->
    val p: Pref<*>? = when (i) {
        is Toggle -> i.pref
        is SliderItem -> i.pref
        is ChoiceItem -> i.pref
        else -> null
    }
    p
}.distinct()

@Suppress("UNCHECKED_CAST")
private fun resetPrefs(list: List<Pref<*>>) {
    list.forEach { Graph.settings.set(it as Pref<Any?>, it.default) }
}

/** "Reset this page" text button (only visible when something on the page was changed) with a confirmation. */
@Composable
private fun ResetPageButton(prefs: List<Pref<*>>, s: Snapshot, label: String = "Reset this page to defaults") {
    var confirm by remember { mutableStateOf(false) }
    if (prefs.none { p -> s[p] != p.default }) return
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = { confirm = true }) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Reset to defaults?") },
            text = { Text("Only the settings on this page go back to their original values. Your learned words and data are not touched.") },
            confirmButton = { TextButton(onClick = { resetPrefs(prefs); confirm = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ItemRow(item: Item, s: Snapshot) {
    val view = LocalView.current
    when (item) {
        is Toggle -> {
            val enabled = item.dependsOn?.let { s[it] } ?: true
            val src = remember { MutableInteractionSource() }
            Row(
                Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f)
                    .clickable(interactionSource = src, indication = null, enabled = enabled) {
                        Graph.haptics.play(HapticEvent.SPECIAL, view)
                        Graph.settings.set(item.pref, !s[item.pref])
                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    RowTitle(item.title)
                    if (item.summary != null) RowSummary(item.summary)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = s[item.pref], onCheckedChange = null, enabled = enabled, interactionSource = src, compact = true)
            }
        }
        is SliderItem -> {
            val enabled = item.dependsOn?.let { s[it] } ?: true
            val stored = s[item.pref]
            var v by remember(stored) { mutableFloatStateOf(stored.toFloat()) }
            Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        RowTitle(item.title)
                        if (item.summary != null) RowSummary(item.summary)
                    }
                    if (enabled && stored != item.pref.default) {
                        ResetButton {
                            Graph.haptics.play(HapticEvent.MODE, view)
                            Graph.settings.set(item.pref, item.pref.default)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    val shown = v.toInt()
                    Box(Modifier.glassBadge(fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(
                            if (shown == 0 && item.zeroLabel != null) item.zeroLabel else "$shown${item.unit}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Slider(
                    value = v, enabled = enabled,
                    onValueChange = {
                        val changed = it.toInt() != v.toInt()
                        v = it
                        if (changed) {
                            Graph.settings.set(item.pref, it.toInt())
                            Graph.haptics.play(HapticEvent.GESTURE_TICK, view)
                        }
                    },
                    onValueChangeFinished = { Graph.settings.set(item.pref, v.toInt()) },
                    valueRange = item.range.first.toFloat()..item.range.last.toFloat(),
                )
            }
        }
        is ChoiceItem -> {
            var open by remember { mutableStateOf(false) }
            val cur = s[item.pref]
            Row(
                Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    RowTitle(item.title)
                    Text(
                        item.options.firstOrNull { it.first == cur }?.second ?: cur,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    )
                    if (item.summary != null) RowSummary(item.summary)
                }
                if (cur != item.pref.default) {
                    ResetButton {
                        Graph.haptics.play(HapticEvent.MODE, view)
                        Graph.settings.set(item.pref, item.pref.default)
                    }
                }
            }
            if (open) {
                AlertDialog(
                    onDismissRequest = { open = false },
                    title = { Text(item.title) },
                    text = {
                        Column {
                            item.options.forEach { (value, label) ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                        Graph.haptics.play(HapticEvent.MODE, view)
                                        Graph.settings.set(item.pref, value); open = false
                                    }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = value == cur, onClick = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
                )
            }
        }
        is Note -> Text(
            item.text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        is ActionItem -> {
            val ctx = LocalContext.current
            Column(Modifier.fillMaxWidth().clickable { item.onClick(ctx) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    item.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (item.summary != null) RowSummary(item.summary)
            }
        }
        is Header -> {}
    }
}

@Composable
fun KeyboardPreview(s: Snapshot, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val theme = Themes.resolve(ctx, s, isSystemInDarkTheme())
    var typed by remember { mutableStateOf("") }
    var shift by remember { mutableStateOf(ShiftState.OFF) }
    var symbols by remember { mutableStateOf(0) }
    val opts = layoutOptions(s, s[P.LANG_ACTIVE], FieldKind.TEXT)
    val rows = remember(opts, symbols) { when (symbols) { 0 -> Layouts.letters(opts); 3 -> Layouts.numpadPage(); else -> Layouts.symbols(opts, symbols) } }
    val actions = remember {
        object : KeyActions {
            override fun onText(text: String, key: KeySpec) {
                typed += if (shift != ShiftState.OFF) text.uppercase() else text
                if (shift == ShiftState.ON) shift = ShiftState.OFF
            }
            override fun onSpace() { typed += " " }
            override fun onDelete() { typed = typed.dropLast(1) }
            override fun onDeleteWord() { typed = typed.trimEnd().substringBeforeLast(' ', "").let { if (it.isEmpty()) "" else "$it " } }
            override fun onEnter() { typed += "\n" }
            override fun onShift() { shift = if (shift == ShiftState.OFF) ShiftState.ON else ShiftState.OFF }
            override fun onShiftLock() { shift = ShiftState.LOCKED }
            override fun onModeKey(type: KeyType) { symbols = when (type) { KeyType.SYMBOLS -> 1; KeyType.SYMBOLS2 -> 2; KeyType.NUMPAD -> 3; else -> 0 } }
            override fun onEmojiKey() {}
            override fun onGlobe(long: Boolean) {}
            override fun onSpaceLong() {}
            override fun onArrow(left: Boolean) {}
            override fun onCursorMove(delta: Int) {}
            override fun onGestureStart() {}
            override fun onGestureWord(path: List<PointF>, centers: Map<Char, PointF>, keyWidth: Float) {
                val w = Graph.engine.decodeGesture(path, centers, keyWidth).firstOrNull()
                if (w != null) typed += "$w "
            }
        }
    }
    val baseRowH = if (rows.size > 4) 38.dp else 42.dp
    val rowH = baseRowH
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(theme.bgTop, theme.bgBottom)))) {
        Text(
            if (typed.isEmpty()) "Try typing, swiping and feeling the haptics…" else typed,
            color = if (typed.isEmpty()) theme.textDim else theme.text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), maxLines = 2,
            style = MaterialTheme.typography.bodyLarge,
        )
        Box(Modifier.fillMaxWidth().height(rowH * rows.size + 8.dp)) {
            KeyboardSurface(
                rows = rows, theme = theme, cfg = surfaceConfig(s, s[P.KB_MODE] == "split"),
                shift = shift, action = ActionKind.ENTER, spaceLabel = "CoverBoard",
                haptics = Graph.haptics, sound = Graph.sound, actions = actions,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

@Composable
fun rememberSettings(): Snapshot {
    val s by Graph.settings.state.collectAsState()
    return s
}