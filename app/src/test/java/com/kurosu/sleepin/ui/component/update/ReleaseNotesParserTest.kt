package com.kurosu.sleepin.ui.component.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the release-note reader used by the update dialog.
 *
 * The fixtures mirror what the `release-notes` workflow publishes as a GitHub release body, so a
 * template change that this parser no longer understands shows up as a failing test instead of an
 * unreadable dialog.
 */
class ReleaseNotesParserTest {

    @Test
    fun parse_bilingualBody_keepsChineseBlockOnly() {
        val releaseNotes = """
            # Changelog

            <details open="True">
              <summary><b>简体中文</b></summary>

            ## 🚀 Features

            - 新增 ICS 导入。 abc1234 @alice

            </details>

            <details>
              <summary><b>English</b></summary>

            ## 🚀 Features

            - Added ICS import. abc1234 @alice

            </details>
        """.trimIndent()

        val sections = ReleaseNotesParser.parse(releaseNotes)

        assertEquals(1, sections.size)
        assertEquals("🚀 Features", sections.single().heading)
        assertEquals(
            listOf(ReleaseNoteItem.Bullet(text = "新增 ICS 导入。", metadata = "abc1234 @alice")),
            sections.single().items
        )
    }

    @Test
    fun parse_plainMarkdownWithoutDetails_groupsByCategoryHeading() {
        val releaseNotes = """
            # Changelog

            ## 🚀 Features

            - 新增 ICS 导入。 abc1234 @alice
            - 支持深色模式。 def5678 @bob
        """.trimIndent()

        val sections = ReleaseNotesParser.parse(releaseNotes)

        assertEquals(1, sections.size)
        assertEquals("🚀 Features", sections.single().heading)
        assertEquals(
            listOf(
                ReleaseNoteItem.Bullet(text = "新增 ICS 导入。", metadata = "abc1234 @alice"),
                ReleaseNoteItem.Bullet(text = "支持深色模式。", metadata = "def5678 @bob")
            ),
            sections.single().items
        )
    }

    @Test
    fun parse_multipleHashesAndAuthors_keepsDocumentOrder() {
        val releaseNotes = """
            ## 🚀 Features

            - 同一改动跨多个提交。 abc1234 def5678 @alice @bob
        """.trimIndent()

        val item = ReleaseNotesParser.parse(releaseNotes).single().items.single()

        assertEquals(
            ReleaseNoteItem.Bullet(
                text = "同一改动跨多个提交。",
                metadata = "abc1234 def5678 @alice @bob"
            ),
            item
        )
    }

    @Test
    fun parse_boldGroupLine_becomesGroupTitle() {
        val releaseNotes = """
            ## 🐛 Fixes

            **课表**

            - 修复网格错位。 1a2b3c4 @alice
        """.trimIndent()

        val items = ReleaseNotesParser.parse(releaseNotes).single().items

        assertEquals(
            listOf(
                ReleaseNoteItem.GroupTitle(text = "课表"),
                ReleaseNoteItem.Bullet(text = "修复网格错位。", metadata = "1a2b3c4 @alice")
            ),
            items
        )
    }

    @Test
    fun parse_inlineMarkers_arePreservedForTheRenderer() {
        val releaseNotes = """
            ## 🐛 Fixes

            - 修复 **启动** 时 `StateFlow` 崩溃。 1a2b3c4 @alice
        """.trimIndent()

        val item = ReleaseNotesParser.parse(releaseNotes).single().items.single()

        assertEquals(
            ReleaseNoteItem.Bullet(
                text = "修复 **启动** 时 `StateFlow` 崩溃。",
                metadata = "1a2b3c4 @alice"
            ),
            item
        )
    }

    @Test
    fun parse_linksAndImages_areReducedToReadableText() {
        val releaseNotes = """
            ## ✨ Highlights

            - 详见 [文档](https://example.com/docs) 与 ![截图](https://example.com/a.png)。
        """.trimIndent()

        val text = (ReleaseNotesParser.parse(releaseNotes).single().items.single()
            as ReleaseNoteItem.Bullet).text

        assertTrue(text, text.contains("文档"))
        assertFalse(text, text.contains("!["))
        assertFalse(text, text.contains("](http"))
    }

    @Test
    fun parse_genericsInsideCodeSpans_areNotTreatedAsHtml() {
        val releaseNotes = """
            ## ⚡ Perf

            - 使用 `Map<String, Int>` 缓存。 1a2b3c4 @alice
        """.trimIndent()

        val text = (ReleaseNotesParser.parse(releaseNotes).single().items.single()
            as ReleaseNoteItem.Bullet).text

        assertTrue(text, text.contains("Map<String, Int>"))
    }

    @Test
    fun parse_wrappedBulletLine_isMergedIntoPreviousEntry() {
        val releaseNotes = """
            ## ✨ Highlights

            - 第一条很长
              的描述。
        """.trimIndent()

        val item = ReleaseNotesParser.parse(releaseNotes).single().items.single()

        assertEquals(ReleaseNoteItem.Bullet(text = "第一条很长 的描述。", metadata = ""), item)
    }

    @Test
    fun parse_bulletWithoutDescriptions_keepsOriginalText() {
        val releaseNotes = """
            ## ✨ Highlights

            - abc1234 @alice
        """.trimIndent()

        val item = ReleaseNotesParser.parse(releaseNotes).single().items.single()

        assertEquals(ReleaseNoteItem.Bullet(text = "abc1234 @alice", metadata = ""), item)
    }

    @Test
    fun parse_bodyWithoutHeadings_fallsBackToSingleUntitledSection() {
        val releaseNotes = """
            - 第一条
            - 第二条
        """.trimIndent()

        val sections = ReleaseNotesParser.parse(releaseNotes)

        assertEquals(1, sections.size)
        assertEquals("", sections.single().heading)
        assertEquals(2, sections.single().items.size)
    }

    @Test
    fun parse_blankBody_returnsNoSections() {
        assertEquals(emptyList<ReleaseNoteSection>(), ReleaseNotesParser.parse("   \n  "))
    }
}
