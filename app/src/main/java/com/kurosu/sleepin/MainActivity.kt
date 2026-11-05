package com.kurosu.sleepin

import android.Manifest
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.kurosu.sleepin.domain.model.AppReleaseInfo
import com.kurosu.sleepin.domain.model.AppSettings
import com.kurosu.sleepin.domain.model.ThemeMode
import com.kurosu.sleepin.ui.component.update.UpdateAvailableDialog
import com.kurosu.sleepin.ui.navigation.SleepInNavHost
import com.kurosu.sleepin.ui.theme.SleepInTheme
import com.kurosu.sleepin.update.ApkDownloadManager
import com.kurosu.sleepin.update.DebugUpdateSimulator
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

            // Debug builds can render the dialog with a built-in sample instead of waiting for a
            // GitHub release newer than the installed version. Nothing is persisted in this mode.
            val simulatedRelease by if (BuildConfig.DEBUG) {
                DebugUpdateSimulator.simulatedRelease.collectAsStateWithLifecycle()
            } else {
                remember { mutableStateOf<AppReleaseInfo?>(null) }
            }
            val pendingUpdateRelease = simulatedRelease ?: settings.pendingUpdateRelease()

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

                    if (pendingUpdateRelease != null) {
                        UpdateAvailableDialog(
                            versionTag = pendingUpdateRelease.versionTag,
                            releaseNotes = pendingUpdateRelease.releaseNotes,
                            releasePageUrl = pendingUpdateRelease.releasePageUrl,
                            onDismiss = {
                                if (simulatedRelease != null) {
                                    DebugUpdateSimulator.setEnabled(false)
                                } else {
                                    acknowledgeUpdateDialog(app, settings)
                                }
                            },
                            onDownload = {
                                if (simulatedRelease != null) {
                                    // The sample carries no APK; closing beats a broken download.
                                    DebugUpdateSimulator.setEnabled(false)
                                } else {
                                    ApkDownloadManager.enqueueApkDownload(
                                        context = this,
                                        downloadUrl = settings.latestApkDownloadUrl,
                                        versionTag = settings.latestRemoteVersion
                                    )
                                    acknowledgeUpdateDialog(app, settings)
                                }
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

    /**
     * Maps persisted update state to the dialog payload, or `null` when no dialog should be shown.
     *
     * A version the user already dismissed stays dismissed: the update check keeps
     * `dismissedUpdateVersion` equal to the remote tag, so the dialog does not reappear on every
     * launch.
     */
    private fun AppSettings.pendingUpdateRelease(): AppReleaseInfo? {
        val shouldShowDialog = updateAvailable &&
            latestApkDownloadUrl.isNotBlank() &&
            latestRemoteVersion.isNotBlank() &&
            latestRemoteVersion != dismissedUpdateVersion
        if (!shouldShowDialog) return null

        return AppReleaseInfo(
            versionTag = latestRemoteVersion,
            releaseName = latestRemoteVersion,
            releaseNotes = latestReleaseNotes,
            apkDownloadUrl = latestApkDownloadUrl,
            releasePageUrl = latestReleasePageUrl
        )
    }
}
