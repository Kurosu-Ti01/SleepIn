package com.kurosu.sleepin.ui.component.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Upper bound for the release-note area so long notes stay inside the dialog. */
private val RELEASE_NOTES_MAX_HEIGHT = 320.dp

/**
 * One-shot dialog announcing a newer GitHub release.
 *
 * The release body is rendered by [ReleaseNotesContent] instead of being dumped as raw Markdown, and
 * a "完整说明" action opens the GitHub release page for the parts the dialog deliberately trims
 * (duplicated translation, English-only details, screenshots).
 *
 * @param versionTag remote version tag, e.g. `v0.6.0`, shown in the title
 * @param releaseNotes raw Markdown release body; may be blank
 * @param releasePageUrl GitHub release page; the link is hidden when blank
 * @param onDismiss invoked by "稍后", back press and outside taps
 * @param onDownload invoked by "下载更新"
 */
@Composable
fun UpdateAvailableDialog(
    versionTag: String,
    releaseNotes: String,
    releasePageUrl: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    // Resolved outside the Dialog subcomposition so the platform handler survives the popup window.
    val uriHandler = LocalUriHandler.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    text = "发现新版本 $versionTag",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                ReleaseNotesContent(
                    releaseNotes = releaseNotes,
                    modifier = Modifier.heightIn(max = RELEASE_NOTES_MAX_HEIGHT)
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (releasePageUrl.isNotBlank()) {
                        TextButton(
                            onClick = {
                                // A device without a browser resolver must not crash the dialog.
                                runCatching { uriHandler.openUri(releasePageUrl) }
                            }
                        ) {
                            Text("完整说明")
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("稍后")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = onDownload) {
                        Text("下载更新")
                    }
                }
            }
        }
    }
}
