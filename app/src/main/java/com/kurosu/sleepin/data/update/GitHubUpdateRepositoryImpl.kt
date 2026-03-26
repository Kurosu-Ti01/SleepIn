package com.kurosu.sleepin.data.update

import com.kurosu.sleepin.domain.model.AppReleaseInfo
import com.kurosu.sleepin.domain.model.AppUpdateCheckResult
import com.kurosu.sleepin.domain.repository.UpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub REST API implementation for release-based update checks.
 */
class GitHubUpdateRepositoryImpl(
    private val apiUrl: String = "https://api.github.com/repos/Kurosu-Ti01/SleepIn/releases/latest"
) : UpdateRepository {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches latest release metadata and maps it to app-level update states.
     */
    override suspend fun checkForUpdate(currentVersionName: String): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val responseBody = requestLatestReleaseJson()
            val root = json.parseToJsonElement(responseBody).jsonObject

            val isPreRelease = root["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false
            if (isPreRelease) {
                return@runCatching AppUpdateCheckResult.UpToDate(currentVersionName)
            }

            val remoteTag = root["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (remoteTag.isBlank()) {
                return@runCatching AppUpdateCheckResult.Failed("Missing release tag from GitHub API")
            }

            val compareResult = VersionNameComparator.compare(remoteTag, currentVersionName)
            if (compareResult <= 0) {
                return@runCatching AppUpdateCheckResult.UpToDate(remoteTag)
            }

            val assets = root["assets"]?.jsonArray.orEmpty()
            val preferredAsset = assets.firstOrNull { asset ->
                val name = asset.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                name.endsWith(".apk") && name.contains("universal", ignoreCase = true)
            } ?: assets.firstOrNull { asset ->
                val name = asset.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                name.endsWith(".apk")
            }

            val apkDownloadUrl = preferredAsset
                ?.jsonObject
                ?.get("browser_download_url")
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()

            if (apkDownloadUrl.isBlank()) {
                return@runCatching AppUpdateCheckResult.Failed("No APK asset found in latest GitHub release")
            }

            val releaseName = root["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val releaseNotes = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val releasePageUrl = root["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()

            AppUpdateCheckResult.UpdateAvailable(
                AppReleaseInfo(
                    versionTag = remoteTag,
                    releaseName = releaseName.ifBlank { remoteTag },
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkDownloadUrl,
                    releasePageUrl = releasePageUrl
                )
            )
        }.getOrElse { throwable ->
            AppUpdateCheckResult.Failed(throwable.message ?: "Unknown update check error")
        }
    }

    private fun requestLatestReleaseJson(): String {
        val connection = URL(apiUrl).openConnection() as HttpURLConnection
        return try {
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "SleepIn-Android")
            }

            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { reader -> reader.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(
                    "GitHub API error: HTTP ${connection.responseCode} ${connection.responseMessage ?: ""} $body"
                )
            }
            body
        } finally {
            connection.disconnect()
        }
    }
}



