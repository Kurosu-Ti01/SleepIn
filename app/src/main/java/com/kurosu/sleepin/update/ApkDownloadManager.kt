package com.kurosu.sleepin.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

/**
 * Starts APK downloads through system DownloadManager.
 */
object ApkDownloadManager {

    /**
     * Enqueues an APK download request and returns the DownloadManager task id.
     */
    fun enqueueApkDownload(
        context: Context,
        downloadUrl: String,
        versionTag: String,
        title: String = "SleepIn 更新包"
    ): Long {
        val safeVersion = versionTag.ifBlank { "latest" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "sleepin-$safeVersion.apk"

        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle(title)
            setDescription("正在下载 $safeVersion")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }
}

