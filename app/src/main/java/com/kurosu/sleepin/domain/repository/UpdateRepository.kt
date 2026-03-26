package com.kurosu.sleepin.domain.repository

import com.kurosu.sleepin.domain.model.AppUpdateCheckResult

/**
 * Checks the latest GitHub release and compares it with current app version.
 */
interface UpdateRepository {
    /**
     * Checks latest GitHub release against [currentVersionName] and returns normalized result.
     */
    suspend fun checkForUpdate(currentVersionName: String): AppUpdateCheckResult
}


