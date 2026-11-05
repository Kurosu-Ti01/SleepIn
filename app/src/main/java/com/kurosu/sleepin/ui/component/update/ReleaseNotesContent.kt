package com.kurosu.sleepin.ui.component.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Metadata (hashes, authors) of a bullet is muted so it does not compete with the description. */
private val METADATA_FONT_SIZE = 11.sp

/**
 * Renders a GitHub release body as a readable category list for the update dialog.
 *
 * The raw Markdown body is first reduced by [ReleaseNotesParser] to sections and bullets, then drawn
 * with Material typography: category headings in the primary color, `**bold**` and `` `code` ``
 * turned into real text spans, and trailing commit hashes / `@author` mentions rendered as small
 * muted text. Scrolling is handled here, so the caller only has to bound the height, e.g.
 * `Modifier.heightIn(max = 320.dp)`.
 *
 * @param releaseNotes raw release body as returned by the GitHub API, possibly still containing
 * HTML `<details>` wrappers and bilingual duplicates
 */
@Composable
fun ReleaseNotesContent(
    releaseNotes: String,
    modifier: Modifier = Modifier
) {
    val sections = remember(releaseNotes) { ReleaseNotesParser.parse(releaseNotes) }

    if (sections.isEmpty()) {
        Text(
            text = "该版本未提供 Release Note。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            if (section.heading.isNotBlank()) {
                Text(
                    text = section.heading,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            section.items.forEach { item ->
                when (item) {
                    is ReleaseNoteItem.GroupTitle -> Text(
                        text = item.text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    is ReleaseNoteItem.Bullet -> ReleaseNoteBullet(bullet = item)
                }
            }
        }
    }
}

/**
 * One bullet row: a hanging bullet glyph plus the styled description.
 */
@Composable
private fun ReleaseNoteBullet(bullet: ReleaseNoteItem.Bullet) {
    val boldStyle = SpanStyle(fontWeight = FontWeight.SemiBold)
    val codeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = MaterialTheme.colorScheme.surfaceContainerHighest
    )
    val metadataStyle = SpanStyle(
        fontSize = METADATA_FONT_SIZE,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )
    val styledText = remember(bullet, boldStyle, codeStyle, metadataStyle) {
        buildAnnotatedString {
            appendInlineMarkdown(bullet.text, boldStyle, codeStyle)
            if (bullet.metadata.isNotBlank()) {
                append("  ")
                withStyle(metadataStyle) { append(bullet.metadata) }
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = styledText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * Appends [text] while converting the inline markers the release template uses.
 *
 * Only `**bold**` and `` `code` `` are supported; any other asterisk or backtick is kept literally, so
 * unexpected Markdown degrades to plain text instead of disappearing.
 */
private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    boldStyle: SpanStyle,
    codeStyle: SpanStyle
) {
    val pending = StringBuilder()

    fun flushPending() {
        if (pending.isNotEmpty()) {
            append(pending.toString())
            pending.clear()
        }
    }

    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", startIndex = index + 2)
                if (end < 0) {
                    pending.append(text[index])
                    index++
                } else {
                    flushPending()
                    withStyle(boldStyle) { append(text.substring(index + 2, end)) }
                    index = end + 2
                }
            }

            text[index] == '`' -> {
                val end = text.indexOf('`', startIndex = index + 1)
                if (end < 0) {
                    pending.append(text[index])
                    index++
                } else {
                    flushPending()
                    withStyle(codeStyle) { append(text.substring(index + 1, end)) }
                    index = end + 1
                }
            }

            else -> {
                pending.append(text[index])
                index++
            }
        }
    }
    flushPending()
}
