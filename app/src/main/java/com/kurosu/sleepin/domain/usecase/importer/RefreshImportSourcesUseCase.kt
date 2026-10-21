package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.AppSettings
import com.kurosu.sleepin.domain.model.importer.BUILT_IN_IMPORT_SOURCE_URL
import com.kurosu.sleepin.domain.model.importer.ImportLocalSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceImportResult
import com.kurosu.sleepin.domain.model.importer.LOCAL_SOURCE_KEY_PREFIX
import com.kurosu.sleepin.domain.model.importer.MAX_IMPORT_SOURCE_REFRESH_INTERVAL_DAYS
import com.kurosu.sleepin.domain.model.importer.MIN_IMPORT_SOURCE_REFRESH_INTERVAL_DAYS
import com.kurosu.sleepin.domain.model.importer.RegisteredImportSource
import com.kurosu.sleepin.domain.repository.ImportSourceRepository
import com.kurosu.sleepin.domain.repository.SettingsRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** What one refresh pass did. */
sealed interface ImportSourceRefreshResult {

    /**
     * Nothing was attempted: the feature is off, the current copy is not old enough, or there is no
     * source to check.
     *
     * Not a failure — it is the normal outcome of the check that runs on every launch.
     */
    data object Skipped : ImportSourceRefreshResult

    /**
     * A pass ran.
     *
     * @param checked how many sources were looked at, including the ones that needed no work
     * @param updated how many actually downloaded and registered something new
     * @param failures user-facing reasons, prefixed with the source they belong to
     */
    data class Completed(
        val checked: Int,
        val updated: Int,
        val failures: List<String>
    ) : ImportSourceRefreshResult {
        val succeeded: Boolean get() = failures.isEmpty()
    }
}

/**
 * Keeps every preset source up to date.
 *
 * The app ships no presets of its own, so a fresh install has an empty list until something imports a
 * source. This is what does it: one pass on launch, then the same work repeated on a schedule.
 *
 * A pass covers **all** sources the device uses, not just the built-in one. A user who points the app at
 * a mirror, or adds a second registry, means those too — a switch labelled "auto update" that quietly
 * updated only one of them would be a trap.
 *
 * Whether a pass is due is decided **here** rather than by the caller, because both callers need the same
 * answer from different places: the launch check has no idea when the last one ran, and the scheduled
 * worker must not skip a refresh the user asked for by shortening the interval.
 *
 * @param clock injectable so the interval rule can be tested without waiting a week.
 */
class RefreshImportSourcesUseCase(
    private val settingsRepository: SettingsRepository,
    private val importSourceRepository: ImportSourceRepository,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /**
     * Refreshes every source when [force] is set, when the feature is enabled, and when the stored copy
     * is older than the configured interval.
     */
    suspend operator fun invoke(force: Boolean = false): ImportSourceRefreshResult {
        val settings = settingsRepository.observeSettings().first()

        if (!force && !settings.importSourceAutoRefreshEnabled) return ImportSourceRefreshResult.Skipped
        if (!force && !isDue(settings.importSourceLastRefreshAtMillis, settings.importSourceRefreshIntervalDays, clock())) {
            return ImportSourceRefreshResult.Skipped
        }

        val targets = sourcesToRefresh()
        if (targets.isEmpty()) return ImportSourceRefreshResult.Skipped

        var updated = 0
        val failures = mutableListOf<String>()

        targets.forEach { target ->
            when (val result = refreshOne(target)) {
                is ImportSourceImportResult.Imported -> updated += 1
                is ImportSourceImportResult.Unchanged -> Unit
                is ImportSourceImportResult.Rejected -> failures += "${labelOf(target)}：${result.message}"
                is ImportSourceImportResult.Failed -> failures += "${labelOf(target)}：${result.message}"
            }
        }

        // The timestamp advances only when nothing failed. A launch that happens to be offline must not
        // postpone the next attempt by a whole interval; a source that keeps failing is retried on the next
        // launch at the cost of one request, while the sources that do work are refreshed in the same pass.
        if (failures.isEmpty()) {
            settingsRepository.updateSettings(
                settings.copy(
                    importSourceLastRefreshAtMillis = clock(),
                    importSourceLastRefreshError = ""
                )
            )
        } else {
            settingsRepository.updateSettings(
                settings.copy(importSourceLastRefreshError = failures.joinToString("\n"))
            )
        }

        return ImportSourceRefreshResult.Completed(
            checked = targets.size,
            updated = updated,
            failures = failures
        )
    }

    /**
     * The built-in source first, then everything else the device has imported, without duplicates.
     *
     * The built-in row is built from the constant rather than from the registry: on a fresh install it is
     * the only one, and it is not registered until its first successful import. Its address is deliberately
     * not a setting — a user cannot re-point it, so there is nothing to read here.
     */
    private suspend fun sourcesToRefresh(): List<RegisteredImportSource> {
        val registered = importSourceRepository.registeredSources()

        val targets = mutableListOf(
            registered.firstOrNull { source -> source.sourceKey == BUILT_IN_IMPORT_SOURCE_URL }
                ?: RegisteredImportSource(
                    sourceKey = BUILT_IN_IMPORT_SOURCE_URL,
                    indexVersion = 0,
                    presetCount = 0
                )
        )
        registered
            .filter { source -> source.sourceKey != BUILT_IN_IMPORT_SOURCE_URL }
            .forEach { source -> targets += source }
        return targets
    }

    /**
     * Re-reads one source.
     *
     * Cached families are never reused here: the source is the authoritative copy of its own adapters, and
     * keeping the previous version would defeat the point of updating at all.
     */
    private suspend fun refreshOne(target: RegisteredImportSource): ImportSourceImportResult =
        if (target.isLocal) {
        importSourceRepository.importLocalSource(
            ImportLocalSourceDraft(
                rootUri = target.sourceKey.removePrefix(LOCAL_SOURCE_KEY_PREFIX),
                reuseLocalFamilies = false
            )
        )
    } else {
        importSourceRepository.importSource(
            ImportSourceDraft(indexUrl = target.sourceKey, reuseLocalFamilies = false)
        )
    }

    private fun labelOf(target: RegisteredImportSource): String =
        target.displayName?.takeIf { it.isNotBlank() } ?: target.sourceKey

    companion object {

        /**
         * Whether a copy from [lastRefreshAtMillis] is old enough to be replaced at [nowMillis].
         *
         * A timestamp of zero means "never refreshed", which is the fresh-install case this feature
         * exists for.
         */
        fun isDue(lastRefreshAtMillis: Long, intervalDays: Int, nowMillis: Long): Boolean {
            if (lastRefreshAtMillis <= 0L) return true
            val intervalMillis = intervalDays
                .coerceIn(MIN_IMPORT_SOURCE_REFRESH_INTERVAL_DAYS, MAX_IMPORT_SOURCE_REFRESH_INTERVAL_DAYS) *
                TimeUnit.DAYS.toMillis(1)
            return nowMillis - lastRefreshAtMillis >= intervalMillis
        }
    }
}
