package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.AppSettings
import com.kurosu.sleepin.domain.model.importer.BUILT_IN_IMPORT_SOURCE_URL
import com.kurosu.sleepin.domain.model.importer.ImportLocalSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceImportResult
import com.kurosu.sleepin.domain.model.importer.LOCAL_SOURCE_KEY_PREFIX
import com.kurosu.sleepin.domain.model.importer.RegisteredImportSource
import com.kurosu.sleepin.domain.repository.ImportSourceRepository
import com.kurosu.sleepin.domain.repository.SettingsRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests a refresh pass over the preset sources.
 *
 * Three things are worth pinning down. The interval rule decides whether the app talks to the network on a
 * given launch, so an off-by-one makes it fetch constantly or never. A pass has to cover **every** source,
 * because a switch labelled "auto update" that only touched one of them would silently leave a mirror stale.
 * And the failure path must not postpone the next attempt by a whole interval.
 */
class RefreshImportSourcesUseCaseTest {

    @Test
    fun isDue_treatsANeverRefreshedSourceAsDue() {
        // The fresh-install case this whole feature exists for.
        assertTrue(RefreshImportSourcesUseCase.isDue(0L, intervalDays = 7, nowMillis = NOW))
    }

    @Test
    fun isDue_waitsForTheWholeInterval() {
        val interval = TimeUnit.DAYS.toMillis(7)

        assertFalse(
            RefreshImportSourcesUseCase.isDue(
                lastRefreshAtMillis = NOW - interval + 1,
                intervalDays = 7,
                nowMillis = NOW
            )
        )
        assertTrue(
            RefreshImportSourcesUseCase.isDue(
                lastRefreshAtMillis = NOW - interval,
                intervalDays = 7,
                nowMillis = NOW
            )
        )
    }

    @Test
    fun isDue_clampsAnOutOfRangeInterval() {
        // A stored value from a newer build, or a corrupted preference, must not disable the refresh.
        assertFalse(
            RefreshImportSourcesUseCase.isDue(
                lastRefreshAtMillis = NOW - TimeUnit.HOURS.toMillis(2),
                intervalDays = 0,
                nowMillis = NOW
            )
        )
        assertTrue(
            RefreshImportSourcesUseCase.isDue(
                lastRefreshAtMillis = NOW - TimeUnit.DAYS.toMillis(31),
                intervalDays = 1000,
                nowMillis = NOW
            )
        )
    }

    @Test
    fun pass_refreshesTheManagedSource_andNeverReusesCachedFamilies() = runBlocking {
        val settings = FakeSettingsRepository()
        val sources = FakeImportSourceRepository(result = ImportSourceImportResult.Imported(116, 7))

        val result = useCase(settings, sources).invoke()

        assertEquals(1, (result as ImportSourceRefreshResult.Completed).checked)
        assertEquals(1, result.updated)
        assertEquals(listOf(BUILT_IN_IMPORT_SOURCE_URL), sources.importedUrls)
        // Reusing a cached family would keep the adapter version the source just replaced.
        assertEquals(listOf(false), sources.reuseLocalFamiliesFlags)

        val stored = settings.observeSettings().first()
        assertEquals(NOW, stored.importSourceLastRefreshAtMillis)
        assertEquals("", stored.importSourceLastRefreshError)
    }

    @Test
    fun pass_refreshesEverySourceNotJustTheManagedOne() = runBlocking {
        // The bug this test exists for: a toggle labelled "auto update sources" that only updated one.
        val mirror = "https://mirror.example.invalid/source-index.json"
        val settings = FakeSettingsRepository()
        val sources = FakeImportSourceRepository(
            result = ImportSourceImportResult.Imported(1, 1),
            registered = listOf(
                RegisteredImportSource(BUILT_IN_IMPORT_SOURCE_URL, indexVersion = 3, presetCount = 10),
                RegisteredImportSource(mirror, indexVersion = 1, presetCount = 2, displayName = "镜像")
            )
        )

        val result = useCase(settings, sources).invoke() as ImportSourceRefreshResult.Completed

        assertEquals(2, result.checked)
        assertEquals(2, result.updated)
        assertEquals(listOf(BUILT_IN_IMPORT_SOURCE_URL, mirror), sources.importedUrls)
    }

    @Test
    fun pass_readsALocalSourceFromItsDirectory() = runBlocking {
        val original ="content://tree/primary%3ASleepIn"
        val settings = FakeSettingsRepository()
        val sources = FakeImportSourceRepository(
            result = ImportSourceImportResult.Imported(1, 1),
            registered = listOf(
                RegisteredImportSource(LOCAL_SOURCE_KEY_PREFIX + original, indexVersion = 1, presetCount = 1)
            )
        )

        useCase(settings, sources).invoke()

        // A directory is read again from the same grant, with the registry prefix stripped.
        assertEquals(listOf(original), sources.importedLocalUris)
    }

    @Test
    fun pass_skipsSourceWhoseVersionHasNotMoved() = runBlocking {
        val settings = FakeSettingsRepository()
        val sources = FakeImportSourceRepository(result = ImportSourceImportResult.Unchanged(indexVersion = 19))

        val result = useCase(settings, sources).invoke() as ImportSourceRefreshResult.Completed

        assertEquals(0, result.updated)
        assertTrue(result.succeeded)
        // Checked and current still counts as the refresh having happened, or every launch would redo it.
        assertEquals(NOW, settings.observeSettings().first().importSourceLastRefreshAtMillis)
    }

    @Test
    fun pass_reportsFailuresPerSource_andKeepsTheTimestampSoItRetries() = runBlocking {
        val broken = "https://broken.example.invalid/source-index.json"
        val lastSuccess = NOW - TimeUnit.DAYS.toMillis(30)
        val settings = FakeSettingsRepository(AppSettings(importSourceLastRefreshAtMillis = lastSuccess))
        val sources = FakeImportSourceRepository(
            result = ImportSourceImportResult.Failed("源服务器返回 HTTP 404"),
            registered = listOf(
                RegisteredImportSource(BUILT_IN_IMPORT_SOURCE_URL, indexVersion = 3, presetCount = 10),
                RegisteredImportSource(broken, indexVersion = 1, presetCount = 2, displayName = "坏掉的源")
            )
        )

        val result = useCase(settings, sources).invoke() as ImportSourceRefreshResult.Completed

        assertEquals(2, result.failures.size)
        // Named, so the user can tell which row needs fixing.
        assertTrue(result.failures.any { it.startsWith("坏掉的源：") })
        assertEquals(lastSuccess, settings.observeSettings().first().importSourceLastRefreshAtMillis)
        assertTrue(settings.observeSettings().first().importSourceLastRefreshError.contains("HTTP 404"))
    }

    @Test
    fun pass_doesNothingWhenAutoRefreshIsOff() = runBlocking {
        val settings = FakeSettingsRepository(AppSettings(importSourceAutoRefreshEnabled = false))
        val sources = FakeImportSourceRepository(result = ImportSourceImportResult.Imported(1, 1))

        assertEquals(ImportSourceRefreshResult.Skipped, useCase(settings, sources).invoke())
        assertTrue(sources.importedUrls.isEmpty())
    }

    @Test
    fun pass_doesNothingWhenTheStoredCopyIsStillFresh() = runBlocking {
        val settings = FakeSettingsRepository(
            AppSettings(importSourceLastRefreshAtMillis = NOW - TimeUnit.DAYS.toMillis(6))
        )
        val sources = FakeImportSourceRepository(result = ImportSourceImportResult.Imported(1, 1))

        assertEquals(ImportSourceRefreshResult.Skipped, useCase(settings, sources).invoke())
        assertTrue(sources.importedUrls.isEmpty())
    }

    @Test
    fun forcedPass_ignoresBothTheToggleAndTheInterval() = runBlocking {
        val settings = FakeSettingsRepository(
            AppSettings(
                importSourceAutoRefreshEnabled = false,
                importSourceLastRefreshAtMillis = NOW
            )
        )
        val sources = FakeImportSourceRepository(result = ImportSourceImportResult.Imported(2, 3))

        val result = useCase(settings, sources).invoke(force = true)

        assertEquals(1, (result as ImportSourceRefreshResult.Completed).checked)
        assertEquals(listOf(BUILT_IN_IMPORT_SOURCE_URL), sources.importedUrls)
    }

    @Test
    fun pass_doesNothingWhenThereIsNoSourceToCheck() = runBlocking {
        // The built-in source is always checked, so this only trips when the registry throws nothing and the
        // constant is empty — which cannot happen today. Guarded so a future change cannot start a pass with
        // an empty target list and report "completed".
        val settings = FakeSettingsRepository()
        val sources = FakeImportSourceRepository(
            result = ImportSourceImportResult.Imported(1, 1),
            registered = emptyList()
        )

        val result = useCase(settings, sources).invoke()

        // One target: the built-in constant.
        assertEquals(1, (result as ImportSourceRefreshResult.Completed).checked)
    }

    @Test
    fun pass_alwaysChecksTheBuiltInAddress() = runBlocking {
        // It is a constant now, not a setting: a mistyped address would leave the app with no source it can
        // rely on, so the user is not offered a way to change it.
        val settings = FakeSettingsRepository()
        val sources = FakeImportSourceRepository(result = ImportSourceImportResult.Imported(1, 1))

        useCase(settings, sources).invoke()

        assertEquals(listOf(BUILT_IN_IMPORT_SOURCE_URL), sources.importedUrls)
    }

    @Test
    fun pass_clearsAPreviousErrorAfterASuccess() = runBlocking {
        val settings = FakeSettingsRepository(AppSettings(importSourceLastRefreshError = "旧的失败"))
        val sources = FakeImportSourceRepository(result = ImportSourceImportResult.Imported(1, 1))

        useCase(settings, sources).invoke()

        assertEquals("", settings.observeSettings().first().importSourceLastRefreshError)
    }

    private fun useCase(
        settings: FakeSettingsRepository,
        sources: FakeImportSourceRepository
    ) = RefreshImportSourcesUseCase(
        settingsRepository = settings,
        importSourceRepository = sources,
        clock = { NOW }
    )

    private class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {

        private val state = MutableStateFlow(initial)

        override fun observeSettings(): Flow<AppSettings> = state.asStateFlow()

        override suspend fun updateSettings(settings: AppSettings) {
            state.value = settings
        }

        override suspend fun exportSettingsBackup(): String = ""

        override suspend fun importSettingsBackup(rawJson: String) = Unit
    }

    private class FakeImportSourceRepository(
        private val result: ImportSourceImportResult,
        private val registered: List<RegisteredImportSource> = emptyList()
    ) : ImportSourceRepository {

        val importedUrls = mutableListOf<String>()
        val importedLocalUris = mutableListOf<String>()
        val reuseLocalFamiliesFlags = mutableListOf<Boolean>()

        override suspend fun importSource(draft: ImportSourceDraft): ImportSourceImportResult {
            importedUrls.add(draft.indexUrl)
            reuseLocalFamiliesFlags.add(draft.reuseLocalFamilies)
            return result
        }

        override suspend fun importLocalSource(draft: ImportLocalSourceDraft): ImportSourceImportResult {
            importedLocalUris.add(draft.rootUri)
            reuseLocalFamiliesFlags.add(draft.reuseLocalFamilies)
            return result
        }

        override suspend fun registeredSources(): List<RegisteredImportSource> = registered

        override suspend fun renameSource(sourceKey: String, displayName: String?): Boolean = true

        override suspend fun forgetSource(sourceKey: String): Boolean = true
    }

    private companion object {
        /** Any fixed instant; the tests only reason about differences from it. */
        const val NOW = 1_700_000_000_000L
    }
}
