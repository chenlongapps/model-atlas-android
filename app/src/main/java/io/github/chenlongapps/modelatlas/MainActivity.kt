package io.github.chenlongapps.modelatlas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.chenlongapps.modelatlas.ui.ModelAtlasApp
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent {
            ModelAtlasTheme {
                ModelAtlasApp()
            }
        }
    }
}
