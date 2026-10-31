package com.example.coverboard.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * CoverBoard's own in-keyboard voice typing. No external dictation screen is shown: the keyboard
 * hosts the listening UI itself and types the words straight into the field, continuously, until stopped.
 */
class VoiceEngine(
    private val ctx: Context,
    private val lang: () -> String,
    private val onText: (String) -> Unit,
) {
    enum class State { IDLE, LISTENING, PROCESSING }

    var state by mutableStateOf(State.IDLE)
        private set
    var partial by mutableStateOf("")
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)

    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var active = false
    private var idleRounds = 0
    private var retries = 0
    private var preferOffline = true

    fun hasPermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Entry point from the toolbar: asks for the microphone first if needed. */
    fun begin() {
        error = null
        if (!hasPermission()) {
            error = "Allow microphone access to type with your voice"
            ctx.startActivity(Intent(ctx, VoiceInputActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        start()
    }

    fun start() {
        error = null
        partial = ""
        idleRounds = 0
        retries = 0
        preferOffline = true
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            error = "No speech recognizer is installed on this phone"
            state = State.IDLE
            return
        }
        active = true
        listen()
    }

    /** Stop listening but still type whatever was already heard. */
    fun stop() {
        active = false
        if (state == State.LISTENING) {
            state = State.PROCESSING
            rec?.stopListening()
        } else if (state == State.IDLE) {
            release()
        }
    }

    fun cancel() {
        active = false
        release()
        state = State.IDLE
        partial = ""
        level = 0f
    }

    private fun listen() {
        release()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(listener)
        rec = r
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        }
        state = State.LISTENING
        r.startListening(i)
    }

    /** "en" alone is rejected by some recognizers; prefer the phone's own regional variant (e.g. en-IN). */
    private fun languageTag(): String {
        val code = lang()
        if (code.contains('-')) return code // an explicit regional choice such as en-IN or en-GB
        val dl = Locale.getDefault()
        return if (dl.language == code.substringBefore('-')) dl.toLanguageTag() else code
    }

    private fun fail(msg: String) {
        active = false
        finish()
        error = msg
    }

    private fun release() {
        rec?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        rec = null
    }

    private fun finish() {
        release()
        state = State.IDLE
        partial = ""
        level = 0f
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            level = ((rmsdB + 2f) / 10f).coerceIn(0f, 1f)
        }

        override fun onEndOfSpeech() {
            level = 0f
            if (state == State.LISTENING) state = State.PROCESSING
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) partial = t
        }

        override fun onResults(results: Bundle?) {
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            partial = ""
            if (!t.isNullOrBlank()) {
                idleRounds = 0
                onText(t.trim() + " ")
            }
            main.post { if (active) listen() else finish() }
        }

        override fun onError(code: Int) {
            if (rec == null) return
            level = 0f
            partial = ""
            when (code) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Silence: keep the mic open for a couple of rounds, then rest.
                    if (active && ++idleRounds < 3) main.post { listen() }
                    else { active = false; main.post { finish() } }
                }
                // 1 network timeout, 2 network, 4 server, 12 language not supported, 13 language unavailable:
                // the offline preference is the usual culprit, so retry once allowing online recognition.
                1, 2, 4, 12, 13 -> {
                    if (active && preferOffline) {
                        preferOffline = false
                        main.post { if (active) listen() }
                    } else fail("Speech service unavailable. Check your internet or speech settings (code $code)")
                }
                // 5 client, 10 too many requests, 11 server disconnected: transient, retry shortly.
                5, 10, 11 -> {
                    if (active && ++retries < 3) main.postDelayed({ if (active) listen() }, 300)
                    else fail("Speech service isn't responding. Tap the mic to retry (code $code)")
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    if (active && ++retries < 3) main.postDelayed({ if (active) listen() }, 350)
                    else fail("Speech service is busy. Tap the mic to retry")
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Microphone permission is needed")
                SpeechRecognizer.ERROR_AUDIO -> fail("Couldn't access the microphone. Another app may be using it")
                else -> fail("Voice typing isn't available (code $code)")
            }
        }
    }
}