package io.github.chenlongapps.modelatlas

import android.os.Bundle
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.chenlongapps.modelatlas.ui.ModelAtlasApp
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import io.github.chenlongapps.modelatlas.ui.settings.AppearanceScreen
import io.github.chenlongapps.modelatlas.ui.settings.AppearanceViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appearance: AppearanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        // 平台启动画面保留到主题首次组合完成；偏好首读由 ViewModel 限时 1.5 秒。
        var readyToDraw = false
        val root = window.decorView
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!readyToDraw) return false
                root.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        setContent {
            val state by appearance.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = state.appearance.mode.isDark(systemDark)
            ModelAtlasTheme(appearance = state.appearance) {
                val background = MaterialTheme.colorScheme.surfaceContainerLowest.toArgb()
                SideEffect {
                    val bars = WindowCompat.getInsetsController(window, window.decorView)
                    bars.isAppearanceLightStatusBars = !dark
                    bars.isAppearanceLightNavigationBars = !dark
                    window.setBackgroundDrawable(background.toDrawable())
                    readyToDraw = state.startupComplete
                }
                LaunchedEffect(state.hasReadError, state.hasWriteError) {
                    val message = when {
                        state.hasReadError -> R.string.appearance_read_error
                        state.hasWriteError -> R.string.appearance_write_error
                        else -> null
                    }
                    message?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
                }
                if (state.startupComplete) ModelAtlasApp(appearanceContent = { onBack ->
                    AppearanceScreen(
                        uiState = state,
                        onBack = onBack,
                        onMode = appearance::setMode,
                        onSystemColors = appearance::useSystemColors,
                        onPreset = appearance::selectPreset,
                        onRestoreCustom = appearance::restoreCustomColor,
                        onHue = appearance::setHue,
                        onFlush = appearance::flushPending,
                        onRetry = appearance::retry,
                    )
                })
            }
        }
    }

    override fun onPause() {
        appearance.flushPending()
        super.onPause()
    }
}
