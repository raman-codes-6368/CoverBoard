package com.example.coverboard

import android.app.Application
import android.content.Context
import com.example.coverboard.data.ClipboardHistory
import com.example.coverboard.data.P
import com.example.coverboard.data.SettingsStore
import com.example.coverboard.data.UserData
import com.example.coverboard.engine.PredictionEngine
import com.example.coverboard.ime.HapticEngine
import com.example.coverboard.ime.SoundEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** One shared set of services for the keyboard and the settings app (same process). */
object Graph {
    lateinit var appContext: Context
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settings: SettingsStore
        private set
    lateinit var user: UserData
        private set
    lateinit var clipboard: ClipboardHistory
        private set
    lateinit var engine: PredictionEngine
        private set
    lateinit var haptics: HapticEngine
        private set
    lateinit var sound: SoundEngine
        private set
    private var ready = false

    fun init(context: Context) {
        if (ready) return
        appContext = context.applicationContext
        settings = SettingsStore(appContext, scope)
        user = UserData(appContext, scope) { settings.current[P.ENCRYPT] }
        clipboard = ClipboardHistory(user)
        engine = PredictionEngine(appContext, user)
        haptics = HapticEngine(appContext) { settings.current }
        sound = SoundEngine(appContext) { settings.current }
        scope.launchLoad()
        ready = true
    }

    private fun CoroutineScope.launchLoad() {
        launch(Dispatchers.IO) { user.load() }
    }
}

class CoverBoardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
