package com.example.coverboard.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.foundation.isSystemInDarkTheme
import android.content.ComponentName
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.coverboard.Graph
import com.example.coverboard.data.P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The keyboard itself: an Android input method hosting a Jetpack Compose UI. */
class CoverBoardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val vmStore = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = vmStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val ks = KeyboardState()
    private lateinit var controller: InputController
    private var clipboardManager: ClipboardManager? = null
    private var maintenanceJob: Job? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { onClipChanged() }

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        controller = InputController(this, ks)

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
        VoiceBridge.listener = { text -> controller.commitExternal(text) }

        maintenanceJob = Graph.scope.launch {
            while (true) {
                loadDictionaries()
                delay(15_000)
                withContext(Dispatchers.Main) { controller.refreshIncognito() }
            }
        }
    }

    private suspend fun loadDictionaries() {
        val s = Graph.settings.current
        val langs = s[P.LANGS_ENABLED].split(",").filter { it.isNotBlank() }
        val use = if (s[P.MULTILINGUAL]) langs else listOf(s[P.LANG_ACTIVE])
        Graph.engine.load(use.ifEmpty { listOf("en") })
    }

    override fun onCreateInputView(): View {
        val decor = window.window!!.decorView
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                val settings by Graph.settings.state.collectAsState()
                val ctx = LocalContext.current
                val dark = isSystemInDarkTheme()
                val theme = Themes.resolve(ctx, settings, dark)
                val activeLang = settings[P.LANG_ACTIVE]
                LaunchedEffect(activeLang) { syncSubtype(activeLang) }
                KeyboardRoot(controller, ks, settings, theme)
            }
        }
    }

    /** Makes the phone's keyboard settings show the language chosen in CoverBoard (e.g. English (India), not English (US)). */
    private fun syncSubtype(code: String) {
        runCatching {
            val locale = if (code == "en") "en_US" else code.replace('-', '_')
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val id = ComponentName(this, CoverBoardService::class.java).flattenToShortString()
            val subtype = InputMethodSubtype.InputMethodSubtypeBuilder()
                .setSubtypeNameResId(0)
                .setSubtypeLocale(locale)
                .setSubtypeMode("keyboard")
                .setIsAsciiCapable(true)
                .build()
            imm.setAdditionalInputMethodSubtypes(id, arrayOf(subtype))
            if (Build.VERSION.SDK_INT >= 28 && imm.currentInputMethodSubtype?.locale != locale) {
                switchInputMethod(id, subtype)
            }
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        controller.onStartInput(info)
        Graph.scope.launch { loadDictionaries() }
        ks.symbolsPage = 0
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        controller.onFinishInput()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        controller.onUpdateSelection(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    private fun onClipChanged() {
        val s = Graph.settings.current
        if (!s[P.CLIP_ENABLED] || ks.incognito) return
        val clip: ClipData = clipboardManager?.primaryClip ?: return
        if (clip.itemCount == 0) return
        val sensitive = Build.VERSION.SDK_INT >= 26 &&
                clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
        if (sensitive && s[P.CLIP_SKIP_SENSITIVE]) return
        val text = clip.getItemAt(0).coerceToText(this).toString()
        if (text.isBlank()) return
        Graph.clipboard.add(text, s[P.CLIP_MAX])
        controller.clipText = text.take(60)
        Graph.scope.launch {
            delay(60_000)
            withContext(Dispatchers.Main) {
                if (controller.clipText == text.take(60)) {
                    controller.clipText = null
                    controller.refreshSuggestions()
                }
            }
        }
        controller.refreshSuggestions()
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        maintenanceJob?.cancel()
        VoiceBridge.listener = null
        VoiceBridge.onPermission = null
        controller.voice.cancel()
        Graph.user.save()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        vmStore.clear()
        super.onDestroy()
    }
}