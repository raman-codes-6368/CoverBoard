package com.example.coverboard.ime

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle

/** Bridges the system microphone-permission result back to the running keyboard. */
object VoiceBridge {
    @Volatile
    var listener: ((String) -> Unit)? = null

    @Volatile
    var onPermission: ((Boolean) -> Unit)? = null
}

/**
 * Invisible helper. An input method cannot show a permission dialog itself, so this asks for the
 * microphone once and closes. All listening and typing happens inside the keyboard (see VoiceEngine).
 */
class VoiceInputActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            VoiceBridge.onPermission?.invoke(true)
            finish()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 42)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        VoiceBridge.onPermission?.invoke(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        finish()
    }
}