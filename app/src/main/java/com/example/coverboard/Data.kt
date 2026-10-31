package com.example.coverboard.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// ═════════════════════════════════════════════════════════════════════════════
//  COVERBOARD DATA & PREFERENCES SYSTEM
// ═════════════════════════════════════════════════════════════════════════════

sealed class Pref<T>(val name: String, val default: T) {
    abstract val key: Preferences.Key<T>
}

class BoolPref(name: String, default: Boolean) : Pref<Boolean>(name, default) {
    override val key: Preferences.Key<Boolean> = booleanPreferencesKey(name)
}

class IntPref(name: String, default: Int) : Pref<Int>(name, default) {
    override val key: Preferences.Key<Int> = intPreferencesKey(name)
}

class StrPref(name: String, default: String) : Pref<String>(name, default) {
    override val key: Preferences.Key<String> = stringPreferencesKey(name)
}

class Snapshot(val raw: Preferences) {
    operator fun <T> get(pref: Pref<T>): T = raw[pref.key] ?: pref.default
}

object P {
    internal val registry = mutableListOf<Pref<*>>()

    private fun b(name: String, default: Boolean) = BoolPref(name, default).also { registry += it }
    private fun i(name: String, default: Int) = IntPref(name, default).also { registry += it }
    private fun s(name: String, default: String) = StrPref(name, default).also { registry += it }

    val ONBOARDED = b("onboarded", false)

    val LANGS_ENABLED = s("langs_enabled", "en")
    val LANG_ACTIVE = s("lang_active", "en")
    val MULTILINGUAL = b("multilingual", true)

    val AUTOCORRECT = b("autocorrect", true)
    val AUTOCORRECT_LEVEL = s("autocorrect_level", "medium")
    val PREDICTIONS = b("predictions", true)
    val NEXT_WORD = b("next_word", true)
    val EMOJI_PREDICTION = b("emoji_prediction", true)
    val AUTO_CAPS = b("auto_caps", true)
    val AUTO_CAP_I = b("auto_cap_i", true)
    val AUTO_SPACE_PUNCT = b("auto_space_punct", true)
    val DOUBLE_SPACE_PERIOD = b("double_space_period", true)
    val AUTO_SPACE_SUGGESTION = b("auto_space_suggestion", true)
    val SMART_PUNCT = b("smart_punct", true)
    val CAPS_DOUBLE_TAP = b("caps_double_tap", true)
    val SHORTCUTS_ENABLED = b("shortcuts_enabled", true)
    val LONG_PRESS_MS = i("long_press_ms", 300)

    val SWIPE_TYPING = b("swipe_typing", true)
    val GESTURE_TRAIL = b("gesture_trail", true)
    val TRAIL_WIDTH = i("trail_width", 6)
    val FLOW_SPACE = b("flow_space", true)
    val SWIPE_DELETE = b("swipe_delete", true)
    val SPACE_CURSOR = b("space_cursor", true)
    val CURSOR_SPEED = i("cursor_speed", 50)
    val HOLD_DELETE_WORDS = b("hold_delete_words", true)
    val KEY_POPUP = b("key_popup", true)

    val LAYOUT = s("layout", "auto")
    val NUMBER_ROW = b("number_row", true)
    val ARROW_KEYS = b("arrow_keys", false)
    val EMOJI_KEY = b("emoji_key", true)
    val GLOBE_KEY = b("globe_key", false)
    val HINTS = b("hints", true)
    val LETTER_SYMBOLS = b("letter_symbols", false)
    val LOWERCASE_KEYS = b("lowercase_keys", true)
    val KB_MODE = s("kb_mode", "standard")
    val HEIGHT_PORTRAIT = i("height_portrait", 100)
    val HEIGHT_LANDSCAPE = i("height_landscape", 100)
    val ONEHAND_WIDTH = i("onehand_width", 80)
    val SPLIT_GAP = i("split_gap", 40)
    val KEY_SPACING = i("key_spacing", 4)
    val KEY_CORNER = i("key_corner", 8)
    val KEY_BORDERS = b("key_borders", true)

    val THEME = s("theme", "midnight")
    val CUSTOM_BG = i("custom_bg", 0xFF080D1A.toInt())
    val CUSTOM_KEY = i("custom_key", 0xFF18264A.toInt())
    val CUSTOM_ACCENT = i("custom_accent", 0xFF3F68.toInt())
    val CUSTOM_TEXT = i("custom_text", 0xFFF0F4FF.toInt())

    val HAPTIC_ENABLED = b("haptic_enabled", true)
    val HAPTIC_MODE = s("haptic_mode", "custom")
    val HAPTIC_PROFILE = s("haptic_profile", "crisp")
    val HAPTIC_STRENGTH = i("haptic_strength", 60)
    val HAPTIC_DURATION = i("haptic_duration", 12)
    val HAPTIC_SPECIAL = b("haptic_special", true)
    val HAPTIC_GESTURE = b("haptic_gesture", true)
    val HAPTIC_LONGPRESS = b("haptic_longpress", true)
    val HAPTIC_CURSOR = b("haptic_cursor", true)
    val SOUND_ENABLED = b("sound_enabled", false)
    val SOUND_VOLUME = i("sound_volume", 50)
    val SOUND_SPECIAL = b("sound_special", true)

    val TOOLBAR_ENABLED = b("toolbar_enabled", true)
    val TOOLBAR_ORDER = s("toolbar_order", "clipboard,emoji,edit,theme,voice,incognito,onehanded,layout,settings")
    val TOOLBAR_HIDDEN = s("toolbar_hidden", "")
    val TOOLBAR_OPEN_DEFAULT = b("toolbar_open_default", false)

    val CLIP_ENABLED = b("clip_enabled", true)
    val CLIP_SUGGEST = b("clip_suggest", true)
    val CLIP_SKIP_SENSITIVE = b("clip_skip_sensitive", true)
    val CLIP_EXPIRE_MIN = i("clip_expire_min", 60)
    val CLIP_MAX = i("clip_max", 30)

    val SKIN_TONE = s("skin_tone", "none")
    val EMOJI_RECENTS = s("emoji_recents", "")

    val LEARN = b("learn", true)
    val INCOGNITO = b("incognito", false)
    val AUTO_INCOGNITO = b("auto_incognito", true)
    val ENCRYPT = b("encrypt", true)
    val SECURE_APP = b("secure_app", false)

    val all: List<Pref<*>> get() = registry
}

private val Context.settingsDataStore by preferencesDataStore(name = "coverboard_settings")

class SettingsStore(private val context: Context, private val scope: CoroutineScope) {

    val state: StateFlow<Snapshot> = context.settingsDataStore.data
        .map { Snapshot(it) }
        .stateIn(scope, SharingStarted.Eagerly, Snapshot(emptyPreferences()))

    val current: Snapshot get() = state.value

    fun <T> set(pref: Pref<T>, value: T) {
        scope.launch { context.settingsDataStore.edit { it[pref.key] = value } }
    }

    fun resetAll() {
        scope.launch { context.settingsDataStore.edit { it.clear() } }
    }

    fun exportJson(): JSONObject {
        val snap = current
        val o = JSONObject()
        for (p in P.all) {
            when (p) {
                is BoolPref -> o.put(p.name, snap[p])
                is IntPref -> o.put(p.name, snap[p])
                is StrPref -> o.put(p.name, snap[p])
            }
        }
        return o
    }

    fun importJson(o: JSONObject) {
        scope.launch {
            context.settingsDataStore.edit { prefs ->
                for (p in P.all) {
                    if (!o.has(p.name)) continue
                    try {
                        when (p) {
                            is BoolPref -> prefs[p.key] = o.getBoolean(p.name)
                            is IntPref -> prefs[p.key] = o.getInt(p.name)
                            is StrPref -> prefs[p.key] = o.getString(p.name)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}

object SecureFile {
    private const val ALIAS = "coverboard_data_key"
    private val MAGIC_ENC = byteArrayOf('C'.code.toByte(), 'B'.code.toByte(), 'E'.code.toByte(), '1'.code.toByte())
    private val MAGIC_PLAIN = byteArrayOf('C'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte())

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun write(file: File, data: ByteArray, encrypt: Boolean) {
        val out: ByteArray = if (encrypt) {
            try {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, key())
                MAGIC_ENC + c.iv + c.doFinal(data)
            } catch (e: Exception) {
                MAGIC_PLAIN + data
            }
        } else {
            MAGIC_PLAIN + data
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(out)
        if (!tmp.renameTo(file)) {
            file.writeBytes(out)
            tmp.delete()
        }
    }

    fun read(file: File): ByteArray? {
        if (!file.exists()) return null
        return try {
            val raw = file.readBytes()
            if (raw.size < 4) return null
            val head = raw.copyOfRange(0, 4)
            when {
                head.contentEquals(MAGIC_PLAIN) -> raw.copyOfRange(4, raw.size)
                head.contentEquals(MAGIC_ENC) -> {
                    val iv = raw.copyOfRange(4, 16)
                    val c = Cipher.getInstance("AES/GCM/NoPadding")
                    c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                    c.doFinal(raw, 16, raw.size - 16)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

class UserData(
    private val context: Context,
    private val scope: CoroutineScope,
    private val encrypt: () -> Boolean,
) {
    val words = ConcurrentHashMap<String, Int>()
    val bigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    val trigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    val shortcuts = ConcurrentHashMap<String, String>()
    val blocked: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val pinned = CopyOnWriteArrayList<String>()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    private val file get() = File(context.filesDir, "user_data.bin")
    private var dirty = 0

    fun bump() {
        _revision.value = _revision.value + 1
    }

    fun learnedCount(word: String): Int = words[word] ?: 0

    fun isLearned(word: String): Boolean = learnedCount(word) >= 2

    fun noteWord(word: String, force: Boolean = false) {
        if (word.length < 2 || word.length > 32) return
        if (force) {
            words[word] = maxOf(words[word] ?: 0, 3)
        } else {
            words[word] = (words[word] ?: 0) + 1
        }
        if (words.size > 20000) pruneWords()
        touch()
    }

    fun noteBigram(prev: String, next: String) {
        if (prev.isBlank() || next.isBlank()) return
        val m = bigrams.getOrPut(prev) { ConcurrentHashMap() }
        m[next] = (m[next] ?: 0) + 1
        if (m.size > 14) {
            val weakest = m.entries.minByOrNull { it.value }
            if (weakest != null && weakest.key != next) m.remove(weakest.key)
        }
        if (bigrams.size > 6000) {
            bigrams.keys.take(500).forEach { bigrams.remove(it) }
        }
        touch()
    }

    fun noteTrigram(w2: String, w1: String, next: String) {
        if (w2.isBlank() || w1.isBlank() || next.isBlank()) return
        val m = trigrams.getOrPut("$w2 $w1") { ConcurrentHashMap() }
        m[next] = (m[next] ?: 0) + 1
        if (m.size > 10) {
            val weakest = m.entries.minByOrNull { it.value }
            if (weakest != null && weakest.key != next) m.remove(weakest.key)
        }
        if (trigrams.size > 12000) {
            trigrams.keys.take(1000).forEach { trigrams.remove(it) }
        }
        touch()
    }

    fun forgetWord(word: String) {
        words.remove(word)
        bigrams.remove(word)
        bigrams.values.forEach { it.remove(word) }
        trigrams.keys.toList().filter { k -> k.split(' ').contains(word) }.forEach { trigrams.remove(it) }
        trigrams.values.forEach { it.remove(word) }
        touch(force = true)
    }

    fun clearLearned() {
        words.clear()
        bigrams.clear()
        trigrams.clear()
        touch(force = true)
    }

    private fun pruneWords() {
        val drop = words.entries.sortedBy { it.value }.take(2000).map { it.key }
        drop.forEach { words.remove(it) }
    }

    fun setShortcut(trigger: String, expansion: String) {
        shortcuts[trigger.trim().lowercase()] = expansion
        touch(force = true)
    }

    fun removeShortcut(trigger: String) {
        shortcuts.remove(trigger)
        touch(force = true)
    }

    fun block(word: String) {
        blocked += word.trim().lowercase()
        touch(force = true)
    }

    fun unblock(word: String) {
        blocked -= word
        touch(force = true)
    }

    fun setPinned(text: String, pin: Boolean) {
        if (pin) {
            if (!pinned.contains(text)) pinned.add(0, text)
        } else {
            pinned.remove(text)
        }
        touch(force = true)
    }

    private fun touch(force: Boolean = false) {
        dirty++
        bump()
        if (force || dirty >= 12) save()
    }

    fun save() {
        dirty = 0
        val json = toJson().toString().toByteArray(Charsets.UTF_8)
        val enc = encrypt()
        scope.launch(Dispatchers.IO) {
            try {
                SecureFile.write(file, json, enc)
            } catch (_: Exception) {
            }
        }
    }

    fun load() {
        val bytes = SecureFile.read(file) ?: return
        try {
            merge(JSONObject(String(bytes, Charsets.UTF_8)))
        } catch (_: Exception) {
        }
        bump()
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("words", JSONObject().also { j -> words.forEach { (k, v) -> j.put(k, v) } })
        o.put("bigrams", JSONObject().also { j ->
            bigrams.forEach { (k, m) -> j.put(k, JSONObject().also { jj -> m.forEach { (a, b) -> jj.put(a, b) } }) }
        })
        o.put("trigrams", JSONObject().also { j ->
            trigrams.forEach { (k, m) -> j.put(k, JSONObject().also { jj -> m.forEach { (a, b) -> jj.put(a, b) } }) }
        })
        o.put("shortcuts", JSONObject().also { j -> shortcuts.forEach { (k, v) -> j.put(k, v) } })
        o.put("blocked", JSONArray(blocked.toList()))
        o.put("pinned", JSONArray(pinned.toList()))
        return o
    }

    fun merge(o: JSONObject) {
        o.optJSONObject("words")?.let { j ->
            j.keys().forEach { k -> words[k] = maxOf(words[k] ?: 0, j.optInt(k, 2)) }
        }
        o.optJSONObject("bigrams")?.let { j ->
            j.keys().forEach { k ->
                val inner = j.optJSONObject(k) ?: return@forEach
                val m = bigrams.getOrPut(k) { ConcurrentHashMap() }
                inner.keys().forEach { a -> m[a] = maxOf(m[a] ?: 0, inner.optInt(a, 1)) }
            }
        }
        o.optJSONObject("trigrams")?.let { j ->
            j.keys().forEach { k ->
                val inner = j.optJSONObject(k) ?: return@forEach
                val m = trigrams.getOrPut(k) { ConcurrentHashMap() }
                inner.keys().forEach { a -> m[a] = maxOf(m[a] ?: 0, inner.optInt(a, 1)) }
            }
        }
        o.optJSONObject("shortcuts")?.let { j -> j.keys().forEach { k -> shortcuts[k] = j.optString(k) } }
        o.optJSONArray("blocked")?.let { a -> for (i in 0 until a.length()) blocked += a.optString(i) }
        o.optJSONArray("pinned")?.let { a ->
            for (i in 0 until a.length()) {
                val s = a.optString(i)
                if (s.isNotEmpty() && !pinned.contains(s)) pinned.add(s)
            }
        }
    }

    fun wipeEverything() {
        words.clear(); bigrams.clear(); trigrams.clear(); shortcuts.clear(); blocked.clear(); pinned.clear()
        file.delete()
        bump()
    }
}

data class ClipItem(val text: String, val time: Long, val pinned: Boolean)

class ClipboardHistory(private val user: UserData) {

    private val recent = ArrayList<ClipItem>()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    @Volatile
    var latest: ClipItem? = null
        private set

    @Synchronized
    fun add(text: String, max: Int) {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > 10_000) return
        recent.removeAll { it.text == clean }
        val item = ClipItem(clean, System.currentTimeMillis(), false)
        recent.add(0, item)
        while (recent.size > max) recent.removeAt(recent.size - 1)
        latest = item
        _revision.value++
    }

    @Synchronized
    fun items(expireMinutes: Int): List<ClipItem> {
        if (expireMinutes > 0) {
            val cutoff = System.currentTimeMillis() - expireMinutes * 60_000L
            recent.removeAll { it.time < cutoff }
        }
        val pins = user.pinned.map { ClipItem(it, 0L, true) }
        return pins + recent.filter { !user.pinned.contains(it.text) }
    }

    @Synchronized
    fun remove(text: String) {
        recent.removeAll { it.text == text }
        user.setPinned(text, false)
        if (latest?.text == text) latest = null
        _revision.value++
    }

    @Synchronized
    fun togglePin(text: String) {
        user.setPinned(text, !user.pinned.contains(text))
        _revision.value++
    }

    @Synchronized
    fun clearUnpinned() {
        recent.clear()
        latest = null
        _revision.value++
    }

    @Synchronized
    fun clearAll() {
        recent.clear()
        latest = null
        user.pinned.toList().forEach { user.setPinned(it, false) }
        _revision.value++
    }
}