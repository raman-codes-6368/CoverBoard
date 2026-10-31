package com.example.coverboard

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.coverboard.data.P
import com.example.coverboard.ui.settings.AboutScreen
import com.example.coverboard.ui.settings.AdvancedScreen
import com.example.coverboard.ui.settings.DictionaryScreen
import com.example.coverboard.ui.settings.HomeScreen
import com.example.coverboard.ui.settings.KeyboardPreview
import com.example.coverboard.ui.settings.LanguagesScreen
import com.example.coverboard.ui.settings.PrivacyScreen
import com.example.coverboard.ui.settings.Schema
import com.example.coverboard.ui.settings.SettingsList
import com.example.coverboard.ui.settings.ShortcutsScreen
import com.example.coverboard.ui.settings.ThemesScreen
import com.example.coverboard.ui.settings.ToolbarScreen
import com.example.coverboard.ui.settings.rememberSettings
import com.example.coverboard.ui.settings.routeTitle
import com.example.coverboard.ui.theme.CinematicBackdrop
import com.example.coverboard.ui.theme.CoverBoardTheme
import com.example.coverboard.ui.theme.GlassTopBar
import com.example.coverboard.ui.theme.TopBarHeight
import com.example.coverboard.ui.theme.rememberChromeScroll

class MainActivity : ComponentActivity() {

    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(this)
        enableEdgeToEdge()
        setContent {
            val s = rememberSettings()
            val secure = s[P.SECURE_APP]
            SideEffect {
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            // Follows the phone: light or dark.
            CoverBoardTheme(darkTheme = isSystemInDarkTheme()) {
                // Navigation is a simple back stack kept as "home>advanced>typing" so it survives rotation.
                // Home is the simple screen; everything detailed lives under "advanced".
                var trail by rememberSaveable { mutableStateOf("home") }
                val route = trail.substringAfterLast('>')
                val canGoBack = trail.contains('>')
                fun go(to: String) { if (to != route) trail = "$trail>$to" }
                fun back() { if (trail.contains('>')) trail = trail.substringBeforeLast('>') }
                BackHandler(enabled = canGoBack) { back() }

                val chrome = rememberChromeScroll()
                LaunchedEffect(route) { chrome.reset() }

                val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarHeight
                val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp

                Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
                    CinematicBackdrop(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().nestedScroll(chrome.connection)) {
                            Box(Modifier.fillMaxSize().padding(top = topPad, bottom = bottomPad)) {
                                AnimatedContent(
                                    targetState = trail, modifier = Modifier.fillMaxSize(),
                                    transitionSpec = {
                                        val deeper = targetState.count { it == '>' } > initialState.count { it == '>' }
                                        if (deeper) {
                                            (slideInHorizontally { it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 } + fadeOut())
                                        } else {
                                            (slideInHorizontally { -it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { it / 6 } + fadeOut())
                                        }
                                    },
                                    label = "route",
                                ) { t ->
                                    when (t.substringAfterLast('>')) {
                                        "home" -> HomeScreen(resumeTick, s) { go(it) }
                                        "advanced" -> AdvancedScreen { go(it) }
                                        "languages" -> LanguagesScreen(s) { go(it) }
                                        "typing" -> SettingsList(Schema.typingAndGestures, s, header = { KeyboardPreview(s) })
                                        "themes", "layout" -> ThemesScreen(s)
                                        "haptics" -> SettingsList(Schema.soundHaptics, s, header = { KeyboardPreview(s) })
                                        "toolbar" -> ToolbarScreen(s)
                                        "dictionary" -> DictionaryScreen(s) { go(it) }
                                        "shortcuts" -> ShortcutsScreen()
                                        "privacy" -> PrivacyScreen(s)
                                        "about" -> AboutScreen()
                                        else -> HomeScreen(resumeTick, s) { go(it) }
                                    }
                                }
                            }

                            GlassTopBar(
                                title = routeTitle(route),
                                showBack = canGoBack,
                                onBack = { back() },
                                scrolled = chrome.scrolled,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )

                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}