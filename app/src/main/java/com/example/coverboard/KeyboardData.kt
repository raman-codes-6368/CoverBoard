package com.example.coverboard.ime

import android.content.Context
import android.content.Intent
import android.graphics.PointF
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.coverboard.Graph
import com.example.coverboard.MainActivity
import com.example.coverboard.R
import com.example.coverboard.data.P
import com.example.coverboard.data.Snapshot
import com.example.coverboard.engine.Scored

// ═════════════════════════════════════════════════════════════════════════════
//  COVERBOARD KEYBOARD DATA, HAPTICS, LAYOUTS & STATE
// ═════════════════════════════════════════════════════════════════════════════

enum class ShiftState { OFF, ON, LOCKED }
enum class ActionKind { ENTER, SEARCH, SEND, GO, NEXT, DONE }

interface KeyActions {
    fun onText(text: String, key: KeySpec)
    fun onSpace()
    fun onDelete()
    fun onDeleteWord()
    fun onEnter()
    fun onShift()
    fun onShiftLock()
    fun onModeKey(type: KeyType)
    fun onEmojiKey()
    fun onGlobe(long: Boolean)
    fun onSpaceLong()
    fun onArrow(left: Boolean)
    fun onCursorMove(delta: Int)
    fun onGestureStart()
    fun onGestureWord(path: List<PointF>, centers: Map<Char, PointF>, keyWidth: Float)
}

enum class HapticEvent { KEY, SPECIAL, LONG_PRESS, GESTURE_TICK, GESTURE_COMMIT, CURSOR_TICK, DELETE_TICK, MODE }

class HapticEngine(context: Context, private val settings: () -> Snapshot) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val cache = HashMap<String, VibrationEffect>()
    private val primSupport = HashMap<Int, Boolean>()

    fun play(event: HapticEvent, view: View? = null) {
        val s = settings()
        if (!s[P.HAPTIC_ENABLED]) return
        when (event) {
            HapticEvent.LONG_PRESS -> if (!s[P.HAPTIC_LONGPRESS]) return
            HapticEvent.GESTURE_TICK, HapticEvent.GESTURE_COMMIT -> if (!s[P.HAPTIC_GESTURE]) return
            HapticEvent.CURSOR_TICK, HapticEvent.DELETE_TICK -> if (!s[P.HAPTIC_CURSOR]) return
            else -> {}
        }
        if (s[P.HAPTIC_MODE] == "system") {
            val flag = if (event == HapticEvent.LONG_PRESS) HapticFeedbackConstants.LONG_PRESS
            else HapticFeedbackConstants.KEYBOARD_TAP
            view?.performHapticFeedback(flag, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            return
        }
        if (!vibrator.hasVibrator()) return
        val effect = effectFor(event, s) ?: return
        try {
            vibrator.vibrate(effect)
        } catch (_: Exception) {
        }
    }

    private fun supported(prim: Int): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        return primSupport.getOrPut(prim) { vibrator.areAllPrimitivesSupported(prim) }
    }

    private fun effectFor(event: HapticEvent, s: Snapshot): VibrationEffect? {
        val strength = s[P.HAPTIC_STRENGTH]
        val profile = s[P.HAPTIC_PROFILE]
        val special = s[P.HAPTIC_SPECIAL]
        val cacheKey = "$event|$strength|$profile|$special|${s[P.HAPTIC_DURATION]}"
        cache[cacheKey]?.let { return it }

        var boost = when (event) {
            HapticEvent.KEY -> 1f
            HapticEvent.SPECIAL -> if (special) 1.3f else 1f
            HapticEvent.LONG_PRESS -> 1.5f
            HapticEvent.GESTURE_TICK -> 0.5f
            HapticEvent.GESTURE_COMMIT -> 1.3f
            HapticEvent.CURSOR_TICK -> 0.6f
            HapticEvent.DELETE_TICK -> 0.8f
            HapticEvent.MODE -> 1.1f
        }
        boost *= strength / 60f
        val scale = (boost * 0.7f).coerceIn(0.05f, 1f)

        var effect: VibrationEffect? = null
        if (Build.VERSION.SDK_INT >= 30) {
            val prim = when {
                event == HapticEvent.LONG_PRESS || event == HapticEvent.GESTURE_COMMIT -> VibrationEffect.Composition.PRIMITIVE_CLICK
                profile == "soft" -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                profile == "firm" -> VibrationEffect.Composition.PRIMITIVE_CLICK
                else -> VibrationEffect.Composition.PRIMITIVE_TICK
            }
            if (supported(prim)) {
                effect = VibrationEffect.startComposition().addPrimitive(prim, scale).compose()
            }
        }
        if (effect == null) {
            val base = s[P.HAPTIC_DURATION].coerceIn(3, 40)
            val ms = when (profile) {
                "soft" -> base * 0.7f
                "firm" -> base * 1.4f
                else -> base.toFloat()
            }.toLong().coerceAtLeast(3)
            val amp = if (vibrator.hasAmplitudeControl()) (scale * 255).toInt().coerceIn(1, 255)
            else VibrationEffect.DEFAULT_AMPLITUDE
            effect = VibrationEffect.createOneShot(ms, amp)
        }
        cache[cacheKey] = effect!!
        return effect
    }
}

/** Plays CoverBoard's own recorded key sounds (res/raw/keypress_*.ogg) through a low-latency SoundPool. */
class SoundEngine(context: Context, private val settings: () -> Snapshot) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val rng = java.util.Random(7)

    private var pool: android.media.SoundPool? = null
    private var idStandard = 0
    private var idStandard2 = 0
    private var idStandard3 = 0
    private var tapCount = 0
    private var idSpace = 0
    private var idDelete = 0
    private var idEnter = 0
    private val loaded = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    init {
        try {
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val p = android.media.SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(attrs)
                .build()
            p.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
            idStandard = p.load(context, R.raw.keypress_standard, 1)
            idStandard2 = p.load(context, R.raw.keypress_standard_2, 1)
            idStandard3 = p.load(context, R.raw.keypress_standard_3, 1)
            idSpace = p.load(context, R.raw.keypress_spacebar, 1)
            idDelete = p.load(context, R.raw.keypress_delete, 1)
            idEnter = p.load(context, R.raw.keypress_return, 1)
            pool = p
        } catch (_: Exception) {
            pool = null
        }
    }

    fun play(kind: KeyType) {
        val s = settings()
        if (!s[P.SOUND_ENABLED]) return
        val vol = Math.pow((s[P.SOUND_VOLUME] / 100f).coerceIn(0.05f, 1f).toDouble(), 1.3).toFloat().coerceIn(0.03f, 1f)

        // Letters rotate through three real recorded taps so fast typing never sounds like one repeated sample.
        val nextStandard = when (tapCount++ % 3) { 0 -> idStandard; 1 -> idStandard2; else -> idStandard3 }
        val id = if (!s[P.SOUND_SPECIAL]) nextStandard else when (kind) {
            KeyType.SPACE -> idSpace
            KeyType.DELETE -> idDelete
            KeyType.ENTER -> idEnter
            else -> nextStandard
        }

        val p = pool
        if (p != null && id != 0 && loaded.contains(id)) {
            try {
                // A tiny random pitch shift keeps fast typing from sounding robotic.
                val rate = 0.985f + rng.nextFloat() * 0.03f
                p.play(id, vol, vol, 1, 0, rate)
                return
            } catch (_: Exception) {
            }
        }

        // Fallback if the recordings aren't ready yet: the phone's own key sounds.
        val fx = if (!s[P.SOUND_SPECIAL]) AudioManager.FX_KEYPRESS_STANDARD else when (kind) {
            KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
            KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
            KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        audio.playSoundEffect(fx, vol)
    }
}

object EmojiData {
    class Category(val id: String, val icon: String, val name: String, val items: List<String>)

    private fun split(s: String) = s.trim().split(Regex("\\s+"))

    val categories: List<Category> = listOf(
        Category("smileys", "😀", "Smileys", split("😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👿 👹 👺 🤡 💩 👻 💀 👽 👾 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾")),
        Category("gestures", "👋", "People", split("👋 🤚 ✋ 🖖 👌 🤌 🤏 🤞 🤟 🤘 🤙 👈 👉 👆 👇 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🙏 💅 🤳 💪 👂 👃 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 🙋 🙆 🙅 🤷 🤦 💁 🙇")),
        Category("hearts", "❤️", "Hearts", split("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ☮️ ✨ 💫 ⭐ 🌟 💥 💢 💦 💨 🕊️ 🔥 🌈 ☀️ ⚡ ❄️ 💯")),
        Category("animals", "🐶", "Animals", split("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦀 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐘 🦒 🦓 🐪 🐑 🐐 🌵 🌲 🌳 🌴 🌱 🌿 🍀 🍁 🍂 🌸 🌼 🌻 🌹 🌷")),
        Category("food", "🍕", "Food", split("🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🥦 🥕 🌽 🥔 🍞 🥐 🧀 🍳 🥞 🥓 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍦 🍩 🍪 🎂 🍰 🍫 🍬 🍭 ☕ 🍵 🥛 🍺 🍻 🥂 🍷 🍸 🍹")),
        Category("travel", "✈️", "Travel", split("🚗 🚕 🚙 🚌 🏎️ 🚓 🚑 🚒 🚚 🚲 🛵 🏍️ 🚂 ✈️ 🚀 🛸 🚁 ⛵ 🚢 ⚓ 🏠 🏢 🏥 🏫 🏰 🗼 🗽 <ctrl42> 🕌 🏖️ 🏝️ 🏔️ ⛰️ 🌋 🗻 🏕️ 🌅 🌄 🌃 🌉 🎡 🎢")),
        Category("activities", "⚽", "Activity", split("⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥊 🥋 ⛳ 🏹 🎣 🎮 🕹️ 🎲 🎯 🎳 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🏆 🥇 🥈 🥉 🏅 🎉 🎊 🎈 🎁")),
        Category("objects", "💡", "Objects", split("⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 📷 📹 🎥 📞 ☎️ 📺 📻 ⏰ 🔋 🔌 💡 🔦 💰 💳 💎 🔧 🔨 🧰 🔑 🔒 🔓 📦 ✉️ 📧 📝 📁 📅 📌 📎 ✂️ 📚 📖 🔍 🔔 🎒 👓 🕶️ 👑 💍 🧸")),
        Category("symbols", "✅", "Symbols", split("✅ ❌ ❓ ❗ ⚠️ 🚫 ✔️ ➕ ➖ ➗ ✖️ ♾️ 💲 ™️ ©️ ®️ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🔶 🔷 ▶️ ⏸️ ⏹️ ⏩ ⏪ 🔀 🔁 🔄 ⬆️ ⬇️ ⬅️ ➡️ ↗️ ↘️ 🆗 🆕 🆒 🆓 🔝 ♻️ 🔰")),
    )

    private val modifiable: Set<String> = split("👋 🤚 ✋ 🖖 👌 🤌 🤏 🤞 🤟 🤘 🤙 👈 👉 👆 👇 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🙏 💅 🤳 💪 👂 👃 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 🙋 🙆 🙅 🤷 🤦 💁 🙇").toSet()

    private val tones = mapOf(
        "light" to "🏻", "medium_light" to "🏼", "medium" to "🏽",
        "medium_dark" to "🏾", "dark" to "🏿",
    )

    val toneNames = listOf(
        "none" to "Default (yellow)", "light" to "Light", "medium_light" to "Medium-light",
        "medium" to "Medium", "medium_dark" to "Medium-dark", "dark" to "Dark",
    )

    fun withTone(emoji: String, tone: String): String {
        val mod = tones[tone] ?: return emoji
        return if (emoji in modifiable) emoji + mod else emoji
    }
}

enum class KeyType {
    LETTER, CHAR, SHIFT, DELETE, ENTER, SPACE, SYMBOLS, LETTERS, SYMBOLS2, NUMPAD, EMOJI, GLOBE, ARROW_LEFT, ARROW_RIGHT
}

data class KeySpec(
    val id: String,
    val type: KeyType,
    val label: String,
    val weight: Float = 1f,
    val alts: List<String> = emptyList(),
    val hint: String? = null,
)

data class KeyRow(val keys: List<KeySpec>, val inset: Float = 0f, val heightWeight: Float = 1f)

enum class FieldKind { TEXT, EMAIL, URL, NUMBER }

data class LayoutOptions(
    val layout: String,
    val lang: String,
    val numberRow: Boolean,
    val emojiKey: Boolean,
    val globeKey: Boolean,
    val arrowKeys: Boolean,
    val hints: Boolean,
    val field: FieldKind,
    val letterSymbols: Boolean = false,
)

data class Language(val code: String, val name: String, val defaultLayout: String)

object Languages {
    val all = listOf(
        Language("en", "English (US)", "qwerty"),
        Language("en-GB", "English (UK)", "qwerty"),
        Language("en-IN", "English (India)", "qwerty"),
        Language("en-AU", "English (Australia)", "qwerty"),
        Language("en-CA", "English (Canada)", "qwerty"),
        Language("en-IE", "English (Ireland)", "qwerty"),
        Language("en-NZ", "English (New Zealand)", "qwerty"),
        Language("en-ZA", "English (South Africa)", "qwerty"),
        Language("es", "Español", "qwerty"),
        Language("fr", "Français", "azerty"),
        Language("de", "Deutsch", "qwertz"),
        Language("it", "Italiano", "qwerty"),
        Language("pt", "Português", "qwerty"),
        Language("nl", "Nederlands", "qwerty"),
        Language("ru", "Русский", "ru"),
        Language("hi", "हिन्दी", "hi"),
        Language("ar", "العربية", "ar"),
        Language("zh", "中文 (Pinyin)", "qwerty"),
        Language("ja", "日本語", "qwerty"),
        Language("ko", "한국어", "qwerty"),
        Language("tr", "Türkçe", "qwerty"),
        Language("pl", "Polski", "qwerty"),
        Language("sv", "Svenska", "qwerty"),
        Language("vi", "Tiếng Việt", "qwerty"),
        Language("el", "Ελληνικά", "el"),
        Language("he", "עברית", "he"),
        Language("bn", "বাংলা", "qwerty"),
        Language("id", "Bahasa Indonesia", "qwerty"),
        Language("uk", "Українська", "ru"),
    )

    fun byCode(code: String) = all.firstOrNull { it.code == code } ?: all.firstOrNull { it.code == code.substringBefore('-') } ?: all[0]
}

object Layouts {

    /** Number row height relative to a letter row (1f = identical size as alphabet keys). */
    const val NUMBER_ROW_WEIGHT = 0.90f

    val layoutIds = listOf("auto", "qwerty", "qwertz", "azerty", "dvorak", "colemak", "ru", "ar", "hi", "el", "he")

    private val altsBase: Map<Char, List<String>> = mapOf(
        'a' to listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā"),
        'e' to listOf("è", "é", "ê", "ë", "ē", "ė", "ę"),
        'i' to listOf("î", "ï", "í", "ī", "į", "ì"),
        'o' to listOf("ô", "ö", "ò", "ó", "œ", "ø", "ō", "õ"),
        'u' to listOf("û", "ü", "ù", "ú", "ū"),
        'c' to listOf("ç", "ć", "č"),
        'n' to listOf("ñ", "ń"),
        's' to listOf("ß", "ś", "š"),
        'y' to listOf("ý", "ÿ"),
        'z' to listOf("ž", "ź", "ż"),
        'l' to listOf("ł"),
        'd' to listOf("ð"),
    )

    private val altsByLang: Map<String, Map<Char, List<String>>> = mapOf(
        "de" to mapOf('a' to listOf("ä"), 'o' to listOf("ö"), 'u' to listOf("ü"), 's' to listOf("ß")),
        "es" to mapOf('n' to listOf("ñ"), 'a' to listOf("á"), 'e' to listOf("é"), 'i' to listOf("í"), 'o' to listOf("ó"), 'u' to listOf("ú", "ü")),
        "fr" to mapOf('e' to listOf("é", "è", "ê", "ë"), 'a' to listOf("à", "â", "æ"), 'c' to listOf("ç"), 'u' to listOf("ù", "û", "ü"), 'o' to listOf("ô", "œ")),
        "pt" to mapOf('a' to listOf("á", "ã", "â", "à"), 'c' to listOf("ç"), 'e' to listOf("é", "ê"), 'o' to listOf("ó", "õ", "ô")),
        "it" to mapOf('a' to listOf("à"), 'e' to listOf("è", "é"), 'i' to listOf("ì"), 'o' to listOf("ò"), 'u' to listOf("ù")),
    )

    fun altsFor(ch: Char, lang: String): List<String> {
        val pri = altsByLang[lang.substringBefore('-')]?.get(ch) ?: emptyList()
        val base = altsBase[ch] ?: emptyList()
        return (pri + base).distinct()
    }

    /** Symbols that can be added to the long-press row of each letter (Settings > Layout > Symbols on letter keys). */
    private val letterSymbolHints: Map<Char, String> = mapOf(
        'a' to "@", 's' to "#", 'd' to "\$", 'f' to "%", 'g' to "&", 'h' to "-", 'j' to "+", 'k' to "(", 'l' to ")",
        'z' to "*", 'x' to "\"", 'c' to "'", 'v' to ":", 'b' to ";", 'n' to "!", 'm' to "?",
    )

    private val punctAlts = mapOf(
        "," to listOf("'", ";", ":"),
        "." to listOf("?", "!", ":", ";", "-", "'", "\"", "…"),
        "-" to listOf("–", "—", "_"),
        "'" to listOf("\"", "`", "’"),
        "!" to listOf("¡"),
        "?" to listOf("¿"),
        "\$" to listOf("€", "£", "¥", "¢"),
        "0" to listOf("°", "⁰"),
        "%" to listOf("‰"),
        "=" to listOf("≠", "≈"),
        "/" to listOf("\\"),
        "(" to listOf("[", "{", "<"),
        ")" to listOf("]", "}", ">"),
    )

    private fun letterRows(layout: String): List<String> = when (layout) {
        "ru", "cyrillic" -> listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю")
        "ar", "arabic" -> listOf("ضصثقفغعهخحج", "شسيبلاتنمكط", "ئءؤرلاىةوزظ")
        "hi", "hindi", "devanagari" -> listOf("कखगघङचछजझञ", "टठडढणतथदधन", "पफबभमयरलवशषसह")
        "el", "greek" -> listOf("υθερτυιοπ", "ασδφγηξκλ", "ζχψβνμ")
        "he", "hebrew" -> listOf("קראטוןםפ", "שדגכעיחלךף", "זסבהנמצתץ")
        "qwertz" -> listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")
        "azerty" -> listOf("azertyuiop", "qsdfghjklm", "wxcvbn")
        "dvorak" -> listOf("',.pyfgcrl", "aoeuidhtns", "qjkxbmwvz")
        "colemak" -> listOf("qwfpgjluy", "arstdhneio", "zxcvbkm")
        else -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    }

    fun resolveLayout(pref: String, lang: String): String {
        if (pref != "auto") return pref
        return when (lang.substringBefore('-')) {
            "ru", "uk" -> "ru"
            "ar" -> "ar"
            "hi" -> "hi"
            "el" -> "el"
            "he" -> "he"
            else -> Languages.byCode(lang).defaultLayout
        }
    }

    private fun key(c: Char, o: LayoutOptions, hint: String? = null): KeySpec {
        val s = c.toString()
        val isLetter = c.isLetter()
        val sym = if (o.letterSymbols && isLetter) letterSymbolHints[c.lowercaseChar()] else null
        val base = if (isLetter) altsFor(c, o.lang) else (punctAlts[s] ?: emptyList())
        return KeySpec(
            id = "k_$c",
            type = if (isLetter) KeyType.LETTER else KeyType.CHAR,
            label = s,
            alts = if (sym != null) listOf(sym) + base else base,
            hint = hint ?: (if (o.hints) sym else null),
        )
    }

    private fun ch(c: String, o: LayoutOptions, id: String = "c_$c") =
        KeySpec(id, KeyType.CHAR, c, alts = punctAlts[c] ?: emptyList())

    private fun bottomRow(o: LayoutOptions, symbols: Boolean, page: Int): KeyRow {
        val list = ArrayList<KeySpec>()
        list.add(
            if (symbols) KeySpec("m_abc", KeyType.LETTERS, "ABC", 1.3f)
            else KeySpec("m_sym", KeyType.SYMBOLS, "?123", 1.3f)
        )
        if (symbols) list.add(KeySpec("m_num", KeyType.NUMPAD, "123", 1.0f))
        if (o.emojiKey) list.add(KeySpec("m_emoji", KeyType.EMOJI, "emoji", 1.0f))
        if (o.globeKey) list.add(KeySpec("m_globe", KeyType.GLOBE, "globe", 1.0f))
        when (o.field) {
            FieldKind.EMAIL -> list.add(ch("@", o))
            FieldKind.URL -> list.add(ch("/", o))
            else -> list.add(ch(",", o))
        }
        val others = (if (o.arrowKeys) 1.6f else 0f) + 1f + 1.3f + list.sumOf { it.weight.toDouble() }.toFloat() +
                (if (o.field == FieldKind.EMAIL || o.field == FieldKind.URL) 1.1f else 0f)
        val space = (10f - others).coerceAtLeast(2.4f)
        list.add(KeySpec("m_space", KeyType.SPACE, "space", space))
        if (o.arrowKeys) {
            list.add(KeySpec("m_left", KeyType.ARROW_LEFT, "left", 0.8f))
            list.add(KeySpec("m_right", KeyType.ARROW_RIGHT, "right", 0.8f))
        }
        when (o.field) {
            FieldKind.EMAIL -> list.add(KeySpec("c_.com", KeyType.CHAR, ".com", 1.1f, alts = listOf(".net", ".org", ".co", ".io")))
            FieldKind.URL -> list.add(KeySpec("c_.com", KeyType.CHAR, ".com", 1.1f, alts = listOf(".net", ".org", ".co", ".io")))
            else -> {}
        }
        list.add(ch(".", o))
        list.add(KeySpec("m_enter", KeyType.ENTER, "enter", 1.3f))
        return KeyRow(list)
    }

    fun letters(o: LayoutOptions): List<KeyRow> {
        val rows = ArrayList<KeyRow>()
        val l = letterRows(o.layout)
        if (o.numberRow) {
            rows.add(KeyRow("1234567890".map { KeySpec("n_$it", KeyType.CHAR, it.toString(), alts = punctAlts[it.toString()] ?: emptyList()) }, heightWeight = NUMBER_ROW_WEIGHT))
        }
        val top = l[0]
        rows.add(KeyRow(top.mapIndexed { i, c ->
            val hint = if (!o.numberRow && o.hints) "${(i + 1) % 10}" else null
            val k = key(c, o, hint)
            if (hint != null) k.copy(alts = listOf(hint) + k.alts) else k
        }, inset = (10 - top.length) / 2f))
        rows.add(KeyRow(l[1].map { key(it, o) }, inset = (10 - l[1].length) / 2f))
        val third = ArrayList<KeySpec>()
        third.add(KeySpec("m_shift", KeyType.SHIFT, "shift", 1.5f))
        l[2].forEach { third.add(key(it, o)) }
        third.add(KeySpec("m_del", KeyType.DELETE, "delete", 1.5f))
        rows.add(KeyRow(third))
        rows.add(bottomRow(o, false, 0))
        return rows
    }

    fun symbols(o: LayoutOptions, page: Int): List<KeyRow> {
        val r1 = if (page == 1) "1234567890" else "~`|•√π÷×¶∆"
        val r2 = if (page == 1) "@#\$%&-+()/" else "£¢€¥^°={}\\"
        val r3 = if (page == 1) listOf("*", "\"", "'", ":", ";", "!", "?") else listOf("%", "©", "®", "™", "✓", "[", "]")
        val rows = ArrayList<KeyRow>()
        rows.add(KeyRow(r1.map { ch(it.toString(), o, "s${page}_$it") }))
        rows.add(KeyRow(r2.map { ch(it.toString(), o, "s${page}_$it") }))
        val third = ArrayList<KeySpec>()
        third.add(
            if (page == 1) KeySpec("m_sym2", KeyType.SYMBOLS2, "=\\<", 1.5f)
            else KeySpec("m_sym1", KeyType.SYMBOLS, "?123", 1.5f)
        )
        r3.forEach { third.add(ch(it, o, "s${page}_$it")) }
        third.add(KeySpec("m_del", KeyType.DELETE, "delete", 1.5f))
        rows.add(KeyRow(third))
        rows.add(bottomRow(o, true, page))
        return rows
    }

    /** Full-width number pad reached from the symbols pages (the "123" key). */
    fun numpadPage(): List<KeyRow> {
        fun k(c: String) = KeySpec("np_$c", KeyType.CHAR, c, 2.5f, alts = punctAlts[c] ?: emptyList())
        return listOf(
            KeyRow(listOf(k("1"), k("2"), k("3"), KeySpec("m_del", KeyType.DELETE, "delete", 2.5f))),
            KeyRow(listOf(k("4"), k("5"), k("6"), k(","))),
            KeyRow(listOf(k("7"), k("8"), k("9"), k("."))),
            KeyRow(listOf(KeySpec("m_abc", KeyType.LETTERS, "ABC", 2.5f), k("0"), KeySpec("m_space", KeyType.SPACE, "space", 2.5f), KeySpec("m_enter", KeyType.ENTER, "enter", 2.5f))),
        )
    }

    fun numpad(): List<KeyRow> {
        fun k(c: String) = KeySpec("p_$c", KeyType.CHAR, c, alts = punctAlts[c] ?: emptyList())
        return listOf(
            KeyRow(listOf(k("1"), k("2"), k("3"), KeySpec("m_del", KeyType.DELETE, "delete", 1f))),
            KeyRow(listOf(k("4"), k("5"), k("6"), k("-"))),
            KeyRow(listOf(k("7"), k("8"), k("9"), k("."))),
            KeyRow(listOf(k(","), k("0"), KeySpec("m_space", KeyType.SPACE, "space", 1f), KeySpec("m_enter", KeyType.ENTER, "enter", 1f))),
        )
    }
}

@Immutable
data class KeyboardTheme(
    val id: String,
    val name: String,
    val dark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val key: Color,
    val keySpecial: Color,
    val keyPressed: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color,
    val onAccent: Color,
    val popup: Color,
    val trail: Color,
)

private fun c(hex: Long) = Color(hex or 0xFF000000)

private fun theme(
    id: String, name: String, dark: Boolean,
    bgTop: Long, bgBottom: Long, key: Long, special: Long, pressed: Long,
    text: Long, accent: Long, onAccent: Long = if (dark) 0x0B0D12 else 0xFFFFFF,
) = KeyboardTheme(
    id, name, dark, c(bgTop), c(bgBottom), c(key), c(special), c(pressed),
    c(text), c(text).copy(alpha = 0.58f), c(accent), c(onAccent),
    popup = lerp(c(key), c(text), if (dark) 0.14f else 0f), trail = c(accent),
)

object Themes {
    val builtIn: List<KeyboardTheme> = listOf(
        theme("midnight", "Crimson Navy", true, 0x1B1D27, 0x11131A, 0x252832, 0x1F222C, 0x343846, 0xF1F2F5, 0xF03F72),
        theme("aurora", "Arctic Aurora", true, 0x04100F, 0x020807, 0x0A2220, 0x061817, 0x123633, 0xF0FAF8, 0x00B8C4),
        theme("graphite", "Ultraviolet Dusk", true, 0x0F0819, 0x08040E, 0x1E1233, 0x160C26, 0x301D52, 0xFFFAF4, 0xA15CFF),
        theme("amoled", "AMOLED Crimson", true, 0x000000, 0x000000, 0x1A1C24, 0x12131A, 0x2C2F3B, 0xFFFFFF, 0xF03F72),
        theme("sunset", "Solar Ember", true, 0x120A05, 0x090502, 0x26160C, 0x1C1008, 0x3B2213, 0xFFF7EE, 0xFFFF7A00),
        theme("sakura", "Velvet Rose", true, 0x16080F, 0x0B0408, 0x2B101E, 0x1F0B15, 0x42192E, 0xFFFFF4, 0xFFE91E63),
        theme("paper", "Crimson Light", false, 0xF4F7FE, 0xE2EBFE, 0xFFFFFF, 0xD4E0FE, 0xBACDFE, 0x15151A, 0xF02A55),
        theme("mint", "Arctic Teal", false, 0xF0FAF8, 0xE2F5F2, 0xFFFFFF, 0xC5EAE4, 0xAEE0D9, 0x04100F, 0x00929E),
        theme("sand", "Solar Warmth", false, 0xFFF7EE, 0xFFEEDD, 0xFFFFFF, 0xFFE2C4, 0xFFD4A8, 0x120A05, 0xE86A00),
    )

    val ids: List<String> get() = listOf("auto") + builtIn.map { it.id } + "custom"

    fun displayName(id: String): String = when (id) {
        "auto" -> "System (Material You)"
        "custom" -> "Custom"
        else -> builtIn.firstOrNull { it.id == id }?.name ?: id
    }

    fun resolve(context: Context, s: Snapshot, systemDark: Boolean): KeyboardTheme {
        return when (val id = s[P.THEME]) {
            "auto" -> auto(context, systemDark)
            "custom" -> custom(s)
            else -> builtIn.firstOrNull { it.id == id } ?: builtIn[0]
        }
    }

    fun custom(s: Snapshot): KeyboardTheme {
        val bg = Color(s[P.CUSTOM_BG])
        val key = Color(s[P.CUSTOM_KEY])
        val text = Color(s[P.CUSTOM_TEXT])
        val accent = Color(s[P.CUSTOM_ACCENT])
        val dark = bg.luminance() < 0.45f
        return KeyboardTheme(
            "custom", "Custom", dark,
            bg, lerp(bg, Color.Black, if (dark) 0.25f else 0.06f),
            key, lerp(key, bg, 0.5f), lerp(key, text, 0.18f),
            text, text.copy(alpha = 0.58f), accent,
            if (accent.luminance() > 0.5f) Color(0xFF0B0D12) else Color.White,
            key, accent,
        )
    }

    private fun auto(context: Context, dark: Boolean): KeyboardTheme {
        if (Build.VERSION.SDK_INT >= 31) {
            fun col(id: Int) = Color(context.getColor(id))
            return if (dark) {
                val bg = col(android.R.color.system_neutral1_900)
                val key = col(android.R.color.system_neutral1_800)
                val accent = col(android.R.color.system_accent1_200)
                KeyboardTheme(
                    "auto", "System", true, bg, lerp(bg, Color.Black, 0.3f), key,
                    lerp(key, bg, 0.5f), col(android.R.color.system_neutral1_700),
                    col(android.R.color.system_neutral1_50), col(android.R.color.system_neutral1_50).copy(alpha = 0.6f),
                    accent, col(android.R.color.system_accent1_900), key, accent,
                )
            } else {
                val bg = col(android.R.color.system_neutral1_100)
                val key = col(android.R.color.system_neutral1_10)
                val accent = col(android.R.color.system_accent1_600)
                KeyboardTheme(
                    "auto", "System", false, bg, col(android.R.color.system_neutral1_200), key,
                    col(android.R.color.system_neutral1_200), col(android.R.color.system_neutral1_300),
                    col(android.R.color.system_neutral1_900), col(android.R.color.system_neutral1_900).copy(alpha = 0.6f),
                    accent, Color.White, key, accent,
                )
            }
        }
        return if (dark) builtIn[0] else builtIn.first { it.id == "paper" }
    }
}

enum class Panel { KEYS, EMOJI, CLIPBOARD, EDIT, THEMES, VOICE }
enum class SugKind { TYPED, COMPLETION, CORRECTION, NEXT, EMOJI, CLIPBOARD, SHORTCUT, GESTURE_ALT }

data class Suggestion(val text: String, val kind: SugKind, val primary: Boolean = false)

class KeyboardState {
    var shift by mutableStateOf(ShiftState.OFF)
    var shiftAuto by mutableStateOf(false)
    var symbolsPage by mutableIntStateOf(0)
    var panel by mutableStateOf(Panel.KEYS)
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
    var action by mutableStateOf(ActionKind.ENTER)
    var lang by mutableStateOf("en")
    var incognito by mutableStateOf(false)
    var toolbarOpen by mutableStateOf(false)
    var field by mutableStateOf(FieldKind.TEXT)
    var selectMode by mutableStateOf(false)
}

private class Auto(val original: String, val corrected: String, val trailing: String)
private class GestureRec(val len: Int)

class InputController(private val service: InputMethodService, val ks: KeyboardState) : KeyActions {

    private val s get() = Graph.settings.current
    private val engine get() = Graph.engine
    private val user get() = Graph.user
    private val ic: InputConnection? get() = service.currentInputConnection

    /** CoverBoard's own dictation: listens and types inside the keyboard, no external screen. */
    val voice = VoiceEngine(service, { ks.lang }) { commitExternal(it) }

    init {
        VoiceBridge.onPermission = { granted ->
            if (granted) voice.start() else voice.error = "Microphone permission was denied"
        }
    }

    private val composing = StringBuilder()
    private var info: EditorInfo? = null
    private var composingMode = true
    private var sensitive = false
    private var noPersonalized = false
    private var prevWord: String? = null
    private var lastAuto: Auto? = null
    private val revertedWords = HashSet<String>()
    private var lastGesture: GestureRec? = null
    private var lastSpaceAt = 0L
    private var lastShiftAt = 0L
    private var spaceAfterSuggestion = false
    private var pendingAutoSpace = false
    var clipText: String? = null

    private val tokenRe = Regex("[\\p{L}][\\p{L}']*|[.!?\\n]")
    private var ctxCache: Pair<String?, String?> = null to null

    /**
     * The two words before the cursor (or before the word being typed), empty after a sentence end.
     * Always read from the field, so backspace, cursor moves and edits reset the context correctly.
     */
    private fun readContext(): Pair<String?, String?> {
        val conn = ic ?: return null to prevWord
        val n = composing.length
        val raw = conn.getTextBeforeCursor(72 + n, 0)?.toString() ?: return null to prevWord
        val t = if (n > 0 && raw.length >= n) raw.substring(0, raw.length - n) else raw
        val found = ArrayList<String>(2)
        for (m in tokenRe.findAll(t.lowercase()).toList().asReversed()) {
            val v = m.value
            if (v == "." || v == "!" || v == "?" || v == "\n") break
            found.add(v.trim('\''))
            if (found.size == 2) break
        }
        return found.getOrNull(1) to found.getOrNull(0)
    }

    /** Context for the word being typed: read once at its first letter, reused for the rest of the word. */
    private fun context(): Pair<String?, String?> {
        if (composing.length > 1) return ctxCache
        ctxCache = readContext()
        return ctxCache
    }

    private val learnAllowed: Boolean get() = s[P.LEARN] && !ks.incognito

    private fun effectiveIncognito() =
        s[P.INCOGNITO] || (s[P.AUTO_INCOGNITO] && (sensitive || noPersonalized))

    fun onStartInput(ei: EditorInfo) {
        info = ei
        composing.clear()
        lastAuto = null
        revertedWords.clear()
        lastGesture = null
        prevWord = null
        pendingAutoSpace = false
        spaceAfterSuggestion = false
        val t = ei.inputType
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        ks.field = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> FieldKind.NUMBER
            InputType.TYPE_CLASS_TEXT -> when (v) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldKind.EMAIL
                InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
                else -> FieldKind.TEXT
            }
            else -> FieldKind.TEXT
        }
        val password = (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        sensitive = password
        noPersonalized = (ei.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        composingMode = cls == InputType.TYPE_CLASS_TEXT && !password
        ks.action = if ((ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) ActionKind.ENTER
        else when (ei.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> ActionKind.SEARCH
            EditorInfo.IME_ACTION_SEND -> ActionKind.SEND
            EditorInfo.IME_ACTION_GO -> ActionKind.GO
            EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_PREVIOUS -> ActionKind.NEXT
            EditorInfo.IME_ACTION_DONE -> ActionKind.DONE
            else -> ActionKind.ENTER
        }
        ks.symbolsPage = 0
        ks.panel = Panel.KEYS
        ks.incognito = effectiveIncognito()
        ks.toolbarOpen = s[P.TOOLBAR_OPEN_DEFAULT]
        ks.lang = s[P.LANG_ACTIVE]
        updateAutoCaps()
        refreshSuggestions()
    }

    fun onFinishInput() {
        voice.cancel()
        if (ks.panel == Panel.VOICE) ks.panel = Panel.KEYS
        ic?.finishComposingText()
        composing.clear()
        user.save()
    }

    fun onUpdateSelection(newSelStart: Int, newSelEnd: Int, candStart: Int, candEnd: Int) {
        if (composing.isNotEmpty()) {
            val outside = candStart == -1 || newSelStart < candStart || newSelEnd > candEnd
            if (outside) {
                composing.clear()
                ic?.finishComposingText()
            }
        }
        updateAutoCaps()
        if (composing.isEmpty()) refreshSuggestions()
    }

    fun refreshIncognito() {
        ks.incognito = effectiveIncognito()
    }

    private fun updateAutoCaps() {
        if (ks.shift == ShiftState.LOCKED) return
        val conn = ic
        val ei = info
        if (!s[P.AUTO_CAPS] || conn == null || ei == null || !composingMode) {
            if (ks.shiftAuto) { ks.shift = ShiftState.OFF; ks.shiftAuto = false }
            return
        }
        val mode = conn.getCursorCapsMode(ei.inputType)
        if (mode != 0) {
            if (ks.shift == ShiftState.OFF) { ks.shift = ShiftState.ON; ks.shiftAuto = true }
        } else if (ks.shiftAuto) {
            ks.shift = ShiftState.OFF
            ks.shiftAuto = false
        }
    }

    override fun onShift() {
        val now = SystemClock.uptimeMillis()
        ks.shift = when {
            ks.shift == ShiftState.LOCKED -> ShiftState.OFF
            ks.shift == ShiftState.ON && s[P.CAPS_DOUBLE_TAP] && now - lastShiftAt < 380 && !ks.shiftAuto -> ShiftState.LOCKED
            ks.shift == ShiftState.ON -> ShiftState.OFF
            else -> ShiftState.ON
        }
        ks.shiftAuto = false
        lastShiftAt = now
    }

    override fun onShiftLock() {
        ks.shift = if (ks.shift == ShiftState.LOCKED) ShiftState.OFF else ShiftState.LOCKED
        ks.shiftAuto = false
    }

    private fun consumeShift() {
        if (ks.shift == ShiftState.ON) {
            ks.shift = ShiftState.OFF
            ks.shiftAuto = false
        }
    }

    override fun onText(text: String, key: KeySpec) {
        val conn = ic ?: return
        if (text.isEmpty()) return
        lastGesture = null
        var out = text
        if (ks.shift != ShiftState.OFF && out[0].isLetter()) out = out.uppercase()
        val first = out[0]
        val wordChar = out.length == 1 && (first.isLetter() || (first == '\'' && composing.isNotEmpty()))

        if (composingMode && wordChar) {
            if (pendingAutoSpace && composing.isEmpty()) conn.commitText(" ", 1)
            pendingAutoSpace = false
            lastAuto = null
            spaceAfterSuggestion = false
            composing.append(out)
            conn.setComposingText(composing, 1)
            consumeShift()
            refreshSuggestions()
            return
        }
        if (!composingMode && first.isLetter()) {
            conn.commitText(out, 1)
            consumeShift()
            pendingAutoSpace = false
            return
        }
        val corrected = finishComposing(true, out)
        if (out.length == 1 && out[0] in ".,!?;:)" && spaceAfterSuggestion && s[P.SMART_PUNCT]) {
            val before = conn.getTextBeforeCursor(1, 0)
            if (before == " ") conn.deleteSurroundingText(1, 0)
        }
        conn.commitText(out, 1)
        spaceAfterSuggestion = false
        pendingAutoSpace = out.length == 1 && out[0] in ",!?;:" && s[P.AUTO_SPACE_PUNCT] && composingMode
        if (!corrected) lastAuto = null
        consumeShift()
        updateAutoCaps()
        refreshSuggestions()
    }

    override fun onSpace() {
        val conn = ic ?: return
        lastGesture = null
        pendingAutoSpace = false
        val now = SystemClock.uptimeMillis()
        if (composing.isNotEmpty()) {
            val corrected = finishComposing(true, " ")
            conn.commitText(" ", 1)
            if (!corrected) lastAuto = null
        } else {
            lastAuto = null
            val before = conn.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (s[P.DOUBLE_SPACE_PERIOD] && composingMode && now - lastSpaceAt < 650 && before.length == 2 &&
                before[1] == ' ' && before[0].isLetterOrDigit()
            ) {
                conn.deleteSurroundingText(1, 0)
                conn.commitText(". ", 1)
            } else {
                conn.commitText(" ", 1)
            }
        }
        lastSpaceAt = now
        spaceAfterSuggestion = false
        updateAutoCaps()
        refreshSuggestions()
    }

    override fun onEnter() {
        val conn = ic ?: return
        finishComposing(true, "\n")
        lastAuto = null
        lastGesture = null
        pendingAutoSpace = false
        val ei = info
        val action = ei?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noAction = ei == null || (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
                action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
        if (noAction) sendKey(KeyEvent.KEYCODE_ENTER) else conn.performEditorAction(action)
        prevWord = null
        updateAutoCaps()
        refreshSuggestions()
    }

    override fun onDelete() {
        val conn = ic ?: return
        pendingAutoSpace = false
        val g = lastGesture
        if (g != null && composing.isEmpty()) {
            conn.deleteSurroundingText(g.len, 0)
            lastGesture = null
            updateAutoCaps(); refreshSuggestions()
            return
        }
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            if (composing.isEmpty()) conn.commitText("", 1) else conn.setComposingText(composing, 1)
            refreshSuggestions()
            updateAutoCaps()
            return
        }
        val a = lastAuto
        if (a != null) {
            val total = a.corrected.length + a.trailing.length
            val before = conn.getTextBeforeCursor(total, 0)?.toString()
            if (before == a.corrected + a.trailing) {
                conn.beginBatchEdit()
                conn.deleteSurroundingText(total, 0)
                composing.append(a.original)
                conn.setComposingText(composing, 1)
                conn.endBatchEdit()
                ctxCache = readContext()
                revertedWords.add(a.original.lowercase())
                if (learnAllowed) user.noteWord(a.original.lowercase(), force = true)
                lastAuto = null
                refreshSuggestions()
                return
            }
            lastAuto = null
        }
        val sel = conn.getSelectedText(0)
        if (!sel.isNullOrEmpty()) {
            conn.commitText("", 1)
        } else {
            val before = conn.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (before.length == 2 && Character.isLowSurrogate(before[1]) && Character.isHighSurrogate(before[0])) {
                conn.deleteSurroundingText(2, 0)
            } else if (before.isEmpty()) {
                sendKey(KeyEvent.KEYCODE_DEL)
            } else {
                conn.deleteSurroundingText(1, 0)
            }
        }
        spaceAfterSuggestion = false
        syncPrevWord()
        updateAutoCaps()
        refreshSuggestions()
    }

    override fun onDeleteWord() {
        val conn = ic ?: return
        lastAuto = null
        lastGesture = null
        pendingAutoSpace = false
        if (composing.isNotEmpty()) {
            composing.clear()
            conn.commitText("", 1)
        } else if (!conn.getSelectedText(0).isNullOrEmpty()) {
            conn.commitText("", 1)
        } else {
            val before = conn.getTextBeforeCursor(64, 0)?.toString() ?: ""
            var i = before.length
            while (i > 0 && before[i - 1].isWhitespace()) i--
            while (i > 0 && !before[i - 1].isWhitespace()) i--
            val n = before.length - i
            if (n > 0) conn.deleteSurroundingText(n, 0) else sendKey(KeyEvent.KEYCODE_DEL)
        }
        spaceAfterSuggestion = false
        syncPrevWord()
        updateAutoCaps()
        refreshSuggestions()
    }

    override fun onModeKey(type: KeyType) {
        finishComposing(false, null)
        ks.symbolsPage = when (type) {
            KeyType.SYMBOLS -> 1
            KeyType.SYMBOLS2 -> 2
            KeyType.NUMPAD -> 3
            else -> 0
        }
    }

    override fun onEmojiKey() {
        finishComposing(false, null)
        ks.panel = Panel.EMOJI
    }

    override fun onGlobe(long: Boolean) {
        if (long) {
            showPicker()
            return
        }
        val langs = enabledLangs()
        if (langs.size > 1) cycleLanguage() else switchIme()
    }

    override fun onSpaceLong() {
        if (enabledLangs().size > 1) cycleLanguage()
    }

    override fun onArrow(left: Boolean) {
        finishComposing(false, null)
        sendKey(if (left) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    override fun onCursorMove(delta: Int) {
        finishComposing(false, null)
        lastAuto = null
        lastGesture = null
        repeat(kotlin.math.abs(delta)) {
            sendKey(if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
        }
    }

    override fun onGestureStart() {
        finishComposing(false, null)
    }

    override fun onGestureWord(path: List<PointF>, centers: Map<Char, PointF>, keyWidth: Float) {
        val conn = ic ?: return
        val words = engine.decodeGesture(path, centers, keyWidth)
        if (words.isEmpty()) return
        val best = caseWord(words[0])
        val before = conn.getTextBeforeCursor(1, 0)?.toString()
        val lead = if (before.isNullOrEmpty() || before.last().isWhitespace() || before.last() in "([{\"“‘") "" else " "
        conn.commitText("$lead$best ", 1)
        lastGesture = GestureRec(lead.length + best.length + 1)
        lastAuto = null
        spaceAfterSuggestion = true
        if (learnAllowed) {
            val low = words[0]
            prevWord?.let { user.noteBigram(it, low) }
        }
        prevWord = words[0]
        consumeShift()
        val alts = words.drop(1).take(2).map { Suggestion(caseWord(it), SugKind.GESTURE_ALT) }
        ks.suggestions = buildList {
            add(Suggestion(best, SugKind.GESTURE_ALT, primary = true))
            addAll(alts)
        }
    }

    private fun caseWord(w: String): String = when (ks.shift) {
        ShiftState.LOCKED -> w.uppercase()
        ShiftState.ON -> w.replaceFirstChar { it.uppercase() }
        else -> if (w == "i") "I" else w
    }

    private fun matchCase(typed: String, cand: String): String = when {
        typed.length > 1 && typed.all { it.isUpperCase() } -> cand.uppercase()
        typed[0].isUpperCase() -> cand.replaceFirstChar { it.uppercase() }
        else -> cand
    }

    /** Only autocorrect when the best candidate is clearly ahead of the runner-up. */
    private fun pickAuto(list: List<Scored>, typedLen: Int = 0): Scored? {
        val best = list.firstOrNull() ?: return null
        if (best.score <= 1.8f) return null
        // Very short words with a big edit are too risky to change on their own.
        if (typedLen in 1..3 && best.cost > 1.0f) return null
        val second = list.getOrNull(1)
        return if (second == null || best.score - second.score >= 0.4f) best else null
    }

    /** Capitalised words mid-sentence are probably names: leave them alone. */
    private fun skipCapitalized(typed: String, conn: InputConnection): Boolean {
        if (!typed[0].isUpperCase()) return false
        val before = conn.getTextBeforeCursor(typed.length + 6, 0)?.toString() ?: return false
        val raw = before.dropLast(typed.length)
        if (raw.isBlank()) return false
        val t = raw.trimEnd(' ')
        return t.isNotEmpty() && t.last() !in ".!?\n"
    }

    private fun syncPrevWord() {
        val b = ic?.getTextBeforeCursor(48, 0)?.toString() ?: return
        val w = b.trimEnd().takeLastWhile { it.isLetter() || it == '\'' }.lowercase()
        prevWord = w.ifEmpty { null }
    }

    private fun finishComposing(autocorrect: Boolean, trailing: String?): Boolean {
        if (composing.isEmpty()) return false
        val conn = ic
        if (conn == null) {
            composing.clear(); return false
        }
        val typed = composing.toString()
        val low = typed.lowercase()
        val (c2, c1) = context()
        var fin = typed
        var corrected = false
        if (autocorrect) {
            val shortcut = if (s[P.SHORTCUTS_ENABLED]) user.shortcuts[low] else null
            if (shortcut != null) {
                fin = shortcut
                corrected = true
            } else if (s[P.AUTO_CAP_I] && low == "i") {
                fin = "I"
            } else if (s[P.AUTOCORRECT] && typed.length >= 2 && !engine.isKnown(low) && low !in revertedWords &&
                typed.none { it.isDigit() } && !(typed.length > 1 && typed.all { it.isUpperCase() }) &&
                !skipCapitalized(typed, conn)
            ) {
                val best = pickAuto(engine.corrections(low, 3, s[P.AUTOCORRECT_LEVEL], c1, c2), typed.length)
                if (best != null) {
                    fin = matchCase(typed, best.word)
                    corrected = fin != typed
                }
            }
        }
        conn.commitText(fin, 1)
        composing.clear()
        if (learnAllowed && !corrected && low.length >= 2) {
            if (!engine.lexicon.contains(low)) user.noteWord(low)
            c1?.let { a ->
                user.noteBigram(a, low)
                c2?.let { b -> user.noteTrigram(b, a, low) }
            }
        }
        prevWord = fin.lowercase()
        if (corrected) lastAuto = Auto(typed, fin, trailing ?: "")
        return corrected
    }

    fun refreshSuggestions() {
        if (!composingMode || !s[P.PREDICTIONS]) {
            ks.suggestions = emptyList()
            return
        }
        if (composing.isEmpty()) {
            val out = ArrayList<Suggestion>()
            val clip = clipText
            if (clip != null && s[P.CLIP_SUGGEST]) {
                out.add(Suggestion(clip, SugKind.CLIPBOARD))
            }
            if (s[P.NEXT_WORD]) {
                val (n2, n1) = readContext()
                val n = engine.nextWords(n1, 3, n2)
                val ordered = if (n.size == 3) listOf(n[1], n[0], n[2]) else n
                ordered.forEachIndexed { i, w ->
                    if (out.size < 3) out.add(Suggestion(caseWord(w), SugKind.NEXT, primary = (i == (if (n.size == 3) 1 else 0))))
                }
                if (s[P.EMOJI_PREDICTION]) {
                    engine.emojiFor(n1)?.let { e ->
                        if (out.size >= 3) out[out.size - 1] = Suggestion(e, SugKind.EMOJI) else out.add(Suggestion(e, SugKind.EMOJI))
                    }
                }
            }
            ks.suggestions = out
            return
        }
        val typed = composing.toString()
        val low = typed.lowercase()
        val known = engine.isKnown(low)
        val (c2, c1) = context()
        val comps = engine.completions(low, 4, c1, c2)
        val corr = if (!known && low.length >= 2) engine.corrections(low, 3, s[P.AUTOCORRECT_LEVEL], c1, c2) else emptyList()
        val list = ArrayList<Suggestion>()
        val shortcut = if (s[P.SHORTCUTS_ENABLED]) user.shortcuts[low] else null
        val auto = if (s[P.AUTOCORRECT] && low !in revertedWords) pickAuto(corr, low.length) else null
        if (shortcut != null) list.add(Suggestion(shortcut, SugKind.SHORTCUT, primary = true))
        if (!known) {
            if (shortcut == null) {
                if (auto != null) {
                    list.add(Suggestion(typed, SugKind.TYPED))
                    list.add(Suggestion(matchCase(typed, auto.word), SugKind.CORRECTION, primary = true))
                } else {
                    list.add(Suggestion(typed, SugKind.TYPED, primary = comps.isEmpty()))
                    comps.firstOrNull()?.let { list.add(Suggestion(matchCase(typed, it.word), SugKind.COMPLETION, primary = true)) }
                }
            }
        } else {
            list.add(Suggestion(typed, SugKind.TYPED))
        }
        for (c in corr + comps) {
            if (list.size >= 3) break
            val t = matchCase(typed, c.word)
            if (list.none { it.text.equals(t, true) }) list.add(Suggestion(t, SugKind.COMPLETION))
        }
        if (list.none { it.primary } && list.size > 1) list[1] = list[1].copy(primary = true)
        else if (list.none { it.primary } && list.isNotEmpty()) list[0] = list[0].copy(primary = true)
        if (list.size == 3) {
            val pi = list.indexOfFirst { it.primary }
            if (pi != 1 && pi >= 0) {
                val tmp = list[1]; list[1] = list[pi]; list[pi] = tmp
            }
        }
        ks.suggestions = list.take(3)
    }

    fun onSuggestion(sug: Suggestion) {
        val conn = ic ?: return
        Graph.haptics.play(HapticEvent.SPECIAL, null)
        val space = if (s[P.AUTO_SPACE_SUGGESTION]) " " else ""
        when (sug.kind) {
            SugKind.CLIPBOARD -> {
                conn.commitText(sug.text, 1)
                clipText = null
                refreshSuggestions()
            }
            SugKind.EMOJI -> {
                conn.commitText(sug.text, 1)
                refreshSuggestions()
            }
            SugKind.NEXT -> {
                val before = conn.getTextBeforeCursor(1, 0)?.toString()
                val lead = if (!before.isNullOrEmpty() && !before.last().isWhitespace()) " " else ""
                val (n2, n1) = readContext()
                conn.commitText(lead + sug.text + space, 1)
                if (learnAllowed) n1?.let { a ->
                    user.noteBigram(a, sug.text.lowercase())
                    n2?.let { b -> user.noteTrigram(b, a, sug.text.lowercase()) }
                }
                prevWord = sug.text.lowercase()
                spaceAfterSuggestion = space.isNotEmpty()
                consumeShift()
                updateAutoCaps()
                refreshSuggestions()
            }
            SugKind.GESTURE_ALT -> {
                val g = lastGesture
                if (g != null) {
                    conn.deleteSurroundingText(g.len, 0)
                    conn.commitText(sug.text + " ", 1)
                    lastGesture = GestureRec(sug.text.length + 1)
                    prevWord = sug.text.lowercase()
                }
            }
            else -> {
                if (composing.isNotEmpty()) {
                    val typed = composing.toString()
                    val (s2, s1) = context()
                    conn.commitText(sug.text + space, 1)
                    composing.clear()
                    if (sug.kind == SugKind.TYPED && learnAllowed) user.noteWord(typed.lowercase(), force = true)
                    else if (learnAllowed) s1?.let { a ->
                        user.noteBigram(a, sug.text.lowercase())
                        s2?.let { b -> user.noteTrigram(b, a, sug.text.lowercase()) }
                    }
                    prevWord = sug.text.lowercase()
                    lastAuto = null
                    spaceAfterSuggestion = space.isNotEmpty()
                    consumeShift()
                    updateAutoCaps()
                    refreshSuggestions()
                }
            }
        }
    }

    fun commitText(text: String) {
        finishComposing(false, null)
        ic?.commitText(text, 1)
        lastAuto = null
        refreshSuggestions()
    }

    fun commitExternal(text: String) {
        commitText(text)
        updateAutoCaps()
    }

    fun editAction(id: String) {
        val conn = ic ?: return
        finishComposing(false, null)
        when (id) {
            "all" -> conn.performContextMenuAction(android.R.id.selectAll)
            "cut" -> conn.performContextMenuAction(android.R.id.cut)
            "copy" -> conn.performContextMenuAction(android.R.id.copy)
            "paste" -> conn.performContextMenuAction(android.R.id.paste)
            "undo" -> conn.performContextMenuAction(android.R.id.undo)
            "redo" -> conn.performContextMenuAction(android.R.id.redo)
            "up" -> sendKey(KeyEvent.KEYCODE_DPAD_UP, if (ks.selectMode) KeyEvent.META_SHIFT_ON else 0)
            "down" -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN, if (ks.selectMode) KeyEvent.META_SHIFT_ON else 0)
            "left" -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT, if (ks.selectMode) KeyEvent.META_SHIFT_ON else 0)
            "right" -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, if (ks.selectMode) KeyEvent.META_SHIFT_ON else 0)
            "home" -> sendKey(KeyEvent.KEYCODE_MOVE_HOME, if (ks.selectMode) KeyEvent.META_SHIFT_ON else 0)
            "end" -> sendKey(KeyEvent.KEYCODE_MOVE_END, if (ks.selectMode) KeyEvent.META_SHIFT_ON else 0)
            "word_left" -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_CTRL_ON or (if (ks.selectMode) KeyEvent.META_SHIFT_ON else 0))
            "word_right" -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_CTRL_ON or (if (ks.selectMode) KeyEvent.META_SHIFT_ON else 0))
            "select" -> ks.selectMode = !ks.selectMode
        }
    }

    private fun sendKey(code: Int, meta: Int = 0) {
        val conn = ic ?: return
        val now = SystemClock.uptimeMillis()
        conn.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        conn.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }

    fun enabledLangs(): List<String> =
        s[P.LANGS_ENABLED].split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("en") }

    private fun cycleLanguage() {
        val l = enabledLangs()
        val next = l[(l.indexOf(ks.lang) + 1).mod(l.size)]
        ks.lang = next
        Graph.settings.set(P.LANG_ACTIVE, next)
    }

    private fun showPicker() {
        (service.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    private fun switchIme() {
        if (Build.VERSION.SDK_INT >= 28) {
            if (!service.switchToNextInputMethod(false)) showPicker()
        } else showPicker()
    }

    fun cycleMode() {
        val order = listOf("standard", "left", "right", "split")
        val cur = s[P.KB_MODE]
        Graph.settings.set(P.KB_MODE, order[(order.indexOf(cur) + 1).mod(order.size)])
    }

    fun openSettings() {
        val i = Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(i)
    }

    fun toggleIncognito() {
        Graph.settings.set(P.INCOGNITO, !s[P.INCOGNITO])
        ks.incognito = !s[P.INCOGNITO] || (s[P.AUTO_INCOGNITO] && (sensitive || noPersonalized))
    }

    fun startVoice() {
        ks.panel = Panel.VOICE
        voice.begin()
    }
}