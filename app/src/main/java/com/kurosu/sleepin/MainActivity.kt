package com.kurosu.sleepin

import android.Manifest
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.kurosu.sleepin.domain.model.AppSettings
import com.kurosu.sleepin.domain.model.ThemeMode
import com.kurosu.sleepin.ui.navigation.SleepInNavHost
import com.kurosu.sleepin.ui.theme.SleepInTheme
import com.kurosu.sleepin.update.ApkDownloadManager
import kotlinx.coroutines.launch

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
            val context = LocalContext.current
            val shouldRequestNotificationPermission =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    settings.notificationsEnabled &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            var permissionRequestTriggered by remember { mutableStateOf(false) }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (!granted) {
                    permissionRequestTriggered = true
                }
            }

            LaunchedEffect(shouldRequestNotificationPermission) {
                if (shouldRequestNotificationPermission && !permissionRequestTriggered) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    permissionRequestTriggered = true
                }
                if (!shouldRequestNotificationPermission) {
                    permissionRequestTriggered = false
                }
            }

            val shouldShowUpdateDialog = settings.updateAvailable &&
                settings.latestApkDownloadUrl.isNotBlank() &&
                settings.latestRemoteVersion.isNotBlank() &&
                settings.latestRemoteVersion != settings.dismissedUpdateVersion

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

                    if (shouldShowUpdateDialog) {
                        UpdateAvailableDialog(
                            versionTag = settings.latestRemoteVersion,
                            releaseNotes = settings.latestReleaseNotes,
                            onDismiss = {
                                acknowledgeUpdateDialog(app, settings)
                            },
                            onDownload = {
                                ApkDownloadManager.enqueueApkDownload(
                                    context = this,
                                    downloadUrl = settings.latestApkDownloadUrl,
                                    versionTag = settings.latestRemoteVersion
                                )
                                acknowledgeUpdateDialog(app, settings)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun acknowledgeUpdateDialog(app: SleepInApplication, settings: AppSettings) {
        lifecycleScope.launch {
            app.updateSettingsUseCase(settings.copy(dismissedUpdateVersion = settings.latestRemoteVersion))
        }
    }
}

/**
 * Dialog shown once per detected version to present release notes and quick download action.
 */
@androidx.compose.runtime.Composable
private fun UpdateAvailableDialog(
    versionTag: String,
    releaseNotes: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 $versionTag") },
        text = {
            Text(
                text = releaseNotes.ifBlank { "该版本未提供 Release Note。" },
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text("下载更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        }
    )
}
