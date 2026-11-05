package com.kurosu.sleepin.ui.component.update

/**
 * One `## 🚀 Features` block parsed out of a GitHub release body.
 *
 * [heading] keeps the category emoji emitted by the release-notes workflow (for example
 * `🚀 Features`) so the dialog can show the same grouping GitHub renders. It is blank when the body
 * has no headings at all, which lets the UI fall back to a plain list.
 *
 * @property heading section title with markup removed; blank for untitled content
 * @property items ordered rows of this section
 */
data class ReleaseNoteSection(
    val heading: String,
    val items: List<ReleaseNoteItem>
)

/**
 * A single row inside a [ReleaseNoteSection].
 */
sealed interface ReleaseNoteItem {

    /**
     * One `- ...` entry of a section.
     *
     * @param text entry description, still carrying `**bold**` and `` `code` `` inline markers that
     * [ReleaseNotesContent] turns into styled spans
     * @param metadata trailing commit hashes and `@author` mentions split off from [text], so the UI
     * can render developer-facing noise smaller and muted instead of hiding it
     */
    data class Bullet(val text: String, val metadata: String) : ReleaseNoteItem

    /** A standalone `**模块名**` line that groups the bullets below it. */
    data class GroupTitle(val text: String) : ReleaseNoteItem
}

/**
 * Minimal reader for the Markdown that the `release-notes` workflow publishes as a GitHub release
 * body.
 *
 * The release body is written by a controlled template (bilingual `<details>` blocks, `## emoji`
 * categories, `- ` entries, optional `**模块名**` sub headings, trailing `hash @author`), so this
 * reader supports exactly that subset instead of pulling a full CommonMark engine into the app:
 * anything it does not recognise degrades to plain paragraph text rather than disappearing.
 *
 * Network-free and Android-free on purpose, so the whole contract is covered by JVM unit tests.
 */
object ReleaseNotesParser {

    private val DETAILS_BLOCK = Regex(
        pattern = """<details\b[^>]*>(.*?)</details\s*>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val SUMMARY = Regex(
        pattern = """<summary\b[^>]*>(.*?)</summary\s*>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * Only tags the release template actually emits are stripped; a generic `<...>` pattern would
     * also eat Kotlin generics quoted as code, e.g. `` `Map<String, Int>` ``.
     */
    private val HTML_TAG = Regex(
        pattern = """</?(?:details|summary|b|i|em|strong|br|p|div|span|a|code|pre|ul|ol|li|img|h[1-6]|hr)\b[^>]*>""",
        option = RegexOption.IGNORE_CASE
    )

    private val MARKDOWN_IMAGE = Regex(pattern = """!\[[^\]]*]\([^)]*\)""")
    private val MARKDOWN_LINK = Regex(pattern = """\[([^\]]*)]\([^)]*\)""")
    private val ANGLE_AUTOLINK = Regex(
        pattern = """<((?:https?|mailto):[^>\s]+)>""",
        option = RegexOption.IGNORE_CASE
    )

    private val HEADING = Regex(pattern = """^(#{1,6})\s+(.+)$""")
    private val GROUP_TITLE = Regex(pattern = """^\*\*([^*]+)\*\*[:：]?$""")
    private val HORIZONTAL_RULE = Regex(pattern = """^(?:-{3,}|\*{3,}|_{3,})$""")
    private val NUMBERED_BULLET = Regex(pattern = """^\d+[.)]\s+""")

    /** `abc1234` (7-40 hex chars) or `@someone`; matches only at the very end of a line. */
    private val TRAILING_METADATA = Regex(
        pattern = """\s*(?:[0-9a-fA-F]{7,40}|@[\p{L}\p{N}_.\-]+)$"""
    )

    private val REPEATED_WHITESPACE = Regex(pattern = """\s+""")

    /**
     * Parses [releaseNotes] into the sections the update dialog renders.
     *
     * Bilingual bodies are reduced to a single language: the `<details>` block whose summary mentions
     * Chinese wins, otherwise the first block. Bodies without `<details>` (older releases, plain
     * Markdown) are read as-is.
     *
     * @return sections in document order; empty when the body carries no renderable content
     */
    fun parse(releaseNotes: String): List<ReleaseNoteSection> {
        if (releaseNotes.isBlank()) return emptyList()

        val sanitized = stripHtmlAndLinks(selectPreferredDetailsBlock(releaseNotes))
        val sections = mutableListOf<MutableSectionBuilder>()
        var current: MutableSectionBuilder? = null

        fun currentOrNew(): MutableSectionBuilder {
            current?.let { return it }
            return MutableSectionBuilder(heading = "").also {
                sections += it
                current = it
            }
        }

        sanitized.lineSequence().forEach { rawLine ->
            val line = rawLine.trim().removePrefix(">").trim()
            if (line.isEmpty() || HORIZONTAL_RULE.matches(line)) return@forEach

            val headingMatch = HEADING.find(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val title = normalizeHeading(headingMatch.groupValues[2])
                // Level 1 is the document title ("# Changelog"); the dialog shows the version instead.
                if (level == 1 || title.isEmpty()) return@forEach
                if (level == 2) {
                    val created = MutableSectionBuilder(heading = title)
                    sections += created
                    current = created
                } else {
                    currentOrNew().items += ReleaseNoteItem.GroupTitle(title)
                }
                return@forEach
            }

            val groupTitleMatch = GROUP_TITLE.find(line)
            if (groupTitleMatch != null) {
                val title = normalizeHeading(groupTitleMatch.groupValues[1])
                if (title.isNotEmpty()) currentOrNew().items += ReleaseNoteItem.GroupTitle(title)
                return@forEach
            }

            val bulletBody = stripBulletPrefix(line)
            if (bulletBody != null) {
                currentOrNew().items += toBullet(bulletBody)
                return@forEach
            }

            // Hand-wrapped description lines belong to the bullet above them, not to a new row.
            val section = currentOrNew()
            val lastBulletIndex = section.items.indexOfLast { it is ReleaseNoteItem.Bullet }
            if (lastBulletIndex >= 0) {
                val previous = section.items[lastBulletIndex] as ReleaseNoteItem.Bullet
                section.items[lastBulletIndex] =
                    previous.copy(text = (previous.text + " " + line).trim())
            } else {
                section.items += toBullet(line)
            }
        }

        return sections
            .filter { it.items.isNotEmpty() }
            .map { section -> ReleaseNoteSection(section.heading, section.items.toList()) }
    }

    /**
     * Picks the block that should be displayed, dropping the duplicated translation.
     */
    private fun selectPreferredDetailsBlock(markdown: String): String {
        val blocks = DETAILS_BLOCK.findAll(markdown).toList()
        if (blocks.isEmpty()) return markdown

        val chineseBlock = blocks.firstOrNull { block ->
            val summary = SUMMARY.find(block.value)?.groupValues?.get(1).orEmpty()
            summary.contains("中文") || summary.contains("chinese", ignoreCase = true)
        }
        return (chineseBlock ?: blocks.first()).groupValues[1]
    }

    /**
     * Removes the HTML wrapper and link syntax a text-only renderer cannot display.
     */
    private fun stripHtmlAndLinks(markdown: String): String = markdown
        .replace(SUMMARY, " ")
        .replace(ANGLE_AUTOLINK, "$1")
        .replace(MARKDOWN_IMAGE, " ")
        .replace(MARKDOWN_LINK, "$1")
        .replace(HTML_TAG, " ")

    /**
     * Splits a bullet line into its description and the trailing `hash @author` mentions.
     */
    private fun toBullet(rawText: String): ReleaseNoteItem.Bullet {
        val trimmed = rawText.trim()
        var remainder = trimmed
        val metadataTokens = ArrayDeque<String>()
        var match = TRAILING_METADATA.find(remainder)
        while (match != null) {
            metadataTokens.addFirst(match.value.trim())
            remainder = remainder.substring(0, match.range.first).trimEnd()
            match = TRAILING_METADATA.find(remainder)
        }

        // A line made only of hashes/authors has no user-facing text left; keep it verbatim instead of
        // rendering an empty bullet.
        if (remainder.isBlank()) {
            return ReleaseNoteItem.Bullet(text = trimmed, metadata = "")
        }
        return ReleaseNoteItem.Bullet(
            text = remainder,
            metadata = metadataTokens.joinToString(separator = " ")
        )
    }

    /**
     * Strips list markers from [line], returning `null` when the line is not a list item.
     */
    private fun stripBulletPrefix(line: String): String? {
        if (line.length > 1 && line[1] == ' ' && (line[0] == '-' || line[0] == '*' || line[0] == '+')) {
            return line.substring(2).takeIf { it.isNotBlank() }
        }
        val numbered = NUMBERED_BULLET.find(line)
        return numbered?.let { match -> line.substring(match.range.last + 1).takeIf { it.isNotBlank() } }
    }

    /**
     * Normalizes a heading or group title to display text.
     */
    private fun normalizeHeading(raw: String): String = raw
        .replace("**", "")
        .replace(REPEATED_WHITESPACE, " ")
        .trim()
        .trimEnd('#', ':', '：')
        .trim()

    /**
     * Mutable accumulator used only while parsing.
     */
    private class MutableSectionBuilder(val heading: String) {
        val items = mutableListOf<ReleaseNoteItem>()
    }
}
