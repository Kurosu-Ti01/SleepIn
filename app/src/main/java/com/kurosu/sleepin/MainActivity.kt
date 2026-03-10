package com.kurosu.sleepin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
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
            SleepInTheme {
                // Keep root container explicit so app-level theming/background works consistently.
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    SleepInNavHost(navController = navController)
                }
            }
        }
    }
}
