package com.kurosu.sleepin.update

import com.kurosu.sleepin.ui.component.update.ReleaseNoteItem
import com.kurosu.sleepin.ui.component.update.ReleaseNotesParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the debug-only update dialog simulation.
 *
 * The sample release note is a fixture for the dialog, so it is asserted through the production
 * parser: if the template or the parser drifts, this test fails before someone ships an unreadable
 * debug dialog.
 */
class DebugUpdateSimulatorTest {

    @After
    fun tearDown() {
        DebugUpdateSimulator.setEnabled(false)
    }

    @Test
    fun setEnabled_false_clearsSimulation() {
        DebugUpdateSimulator.setEnabled(true)
        DebugUpdateSimulator.setEnabled(false)

        assertNull(DebugUpdateSimulator.simulatedRelease.value)
    }

    @Test
    fun setEnabled_true_exposesSampleWithoutDownloadUrl() {
        DebugUpdateSimulator.setEnabled(true)

        val release = DebugUpdateSimulator.simulatedRelease.value

        assertNotNull(release)
        assertEquals("v9.9.9-debug", release?.versionTag)
        assertTrue(
            "模拟态不能带 APK 地址，否则点下载会真的排队下载",
            release?.apkDownloadUrl.isNullOrBlank()
        )
        assertTrue(release?.releasePageUrl?.startsWith("https://") == true)
    }

    @Test
    fun sampleReleaseNotes_parseIntoExpectedSections() {
        DebugUpdateSimulator.setEnabled(true)
        val releaseNotes = requireNotNull(DebugUpdateSimulator.simulatedRelease.value).releaseNotes

        val sections = ReleaseNotesParser.parse(releaseNotes)

        assertEquals(4, sections.size)
        assertTrue(sections[0].heading.endsWith("Highlights"))
        assertTrue(sections[1].heading.endsWith("Features"))
        assertTrue(sections[2].heading.endsWith("Fixes"))
        assertTrue(sections[3].heading.endsWith("Build"))
    }

    @Test
    fun sampleReleaseNotes_renderOnlyChineseBlock() {
        DebugUpdateSimulator.setEnabled(true)
        val releaseNotes = requireNotNull(DebugUpdateSimulator.simulatedRelease.value).releaseNotes

        val flattened = ReleaseNotesParser.parse(releaseNotes)
            .flatMap { section -> section.items }
            .joinToString(separator = "\n") { item ->
                when (item) {
                    is ReleaseNoteItem.Bullet -> item.text
                    is ReleaseNoteItem.GroupTitle -> item.text
                }
            }

        assertTrue(flattened.contains("从教务系统直接导入课表"))
        assertFalse(flattened, flattened.contains("Import the timetable straight from the school system"))
    }

    @Test
    fun sampleReleaseNotes_carryInlineMarkupAndSplitMetadata() {
        DebugUpdateSimulator.setEnabled(true)
        val releaseNotes = requireNotNull(DebugUpdateSimulator.simulatedRelease.value).releaseNotes

        val sections = ReleaseNotesParser.parse(releaseNotes)
        val highlights = sections[0].items.filterIsInstance<ReleaseNoteItem.Bullet>().first()
        val features = sections[1].items.filterIsInstance<ReleaseNoteItem.Bullet>()
        val fixes = sections[2].items

        assertTrue("Highlights 保留 **加粗** 标记", highlights.text.contains("**从教务系统直接导入课表**"))
        assertEquals("", highlights.metadata)
        assertEquals("953983b @Kurosu-Ti01", features.first().metadata)
        assertTrue("Features 保留 `code` 标记", features.first().text.contains("`schema 1`"))
        assertTrue("**课表** 应成为分组小标题", fixes.first() is ReleaseNoteItem.GroupTitle)
    }
}
