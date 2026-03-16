package com.kurosu.sleepin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.kurosu.sleepin.domain.model.AppSettings
import com.kurosu.sleepin.domain.model.ThemeMode
import com.kurosu.sleepin.ui.navigation.SleepInNavHost
import com.kurosu.sleepin.ui.theme.SleepInTheme

/**
 * Single-activity entry point hosting the full Compose navigation graph.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val app = application as SleepInApplication
            val settings = app.observeSettingsUseCase().collectAsStateWithLifecycle(initialValue = AppSettings()).value

            SleepInTheme(
                darkTheme = when (settings.themeMode) {
                    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
                dynamicColor = settings.dynamicColorEnabled
            ) {
                // Keep root container explicit so app-level theming/background works consistently.
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    SleepInNavHost(
                        navController = navController,
                        appSettings = settings
                    )
                }
            }
        }
    }
}
