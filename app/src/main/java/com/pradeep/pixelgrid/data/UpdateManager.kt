package com.pradeep.pixelgrid.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class UpdateInfo(
    val version: String,       // e.g. "v1.0.2"
    val changelog: String,     // release body notes
    val downloadUrl: String,   // APK download link
    val forceShow: Boolean = false // Bypass 24h snooze (e.g. on manual checks)
)

object UpdateManager {

    private const val PREFS_NAME = "pixelvault_update_prefs"
    private const val KEY_DISMISSED_VERSION = "dismissed_version"
    private const val KEY_DISMISSED_TIME = "dismissed_time"
    private const val SNOOZE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 Hours

    // Fetch the current app's version name
    fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    // Compare two semantic versions. Returns true if latest is newer than current.
    fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurrent = current.replace("v", "").trim()
        val cleanLatest = latest.replace("v", "").trim()
        if (cleanCurrent == cleanLatest) return false
        
        val currParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val lateParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val size = maxOf(currParts.size, lateParts.size)
        
        for (i in 0 until size) {
            val currVal = currParts.getOrNull(i) ?: 0
            val lateVal = lateParts.getOrNull(i) ?: 0
            if (lateVal > currVal) return true
            if (currVal > lateVal) return false
        }
        return false
    }

    // Snooze update popup for 24 hours
    fun snoozeUpdate(context: Context, version: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DISMISSED_VERSION, version)
            .putLong(KEY_DISMISSED_TIME, System.currentTimeMillis())
            .apply()
    }

    // Check if the update is snoozed (less than 24 hours since dismissal of same version)
    private fun isUpdateSnoozed(context: Context, version: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissedVersion = prefs.getString(KEY_DISMISSED_VERSION, null)
        val dismissedTime = prefs.getLong(KEY_DISMISSED_TIME, 0L)
        
        if (dismissedVersion == version) {
            val timePassed = System.currentTimeMillis() - dismissedTime
            return timePassed < SNOOZE_DURATION_MS
        }
        return false
    }

    // Query GitHub API for latest release. Parses JSON using Regex to avoid JSON imports.
    suspend fun checkForUpdates(context: Context, isManualCheck: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/Pradeep1234a/pixel/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "PixelVault-App")

            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Parse version (tag_name)
                val tagMatcher = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(json)
                if (!tagMatcher.find()) return@withContext null
                val latestVersion = tagMatcher.group(1) ?: return@withContext null
                
                // Parse changelog (body)
                val bodyMatcher = Pattern.compile("\"body\"\\s*:\\s*\"([^\"]+)\"").matcher(json)
                val rawChangelog = if (bodyMatcher.find()) bodyMatcher.group(1) else "No release notes provided."
                val cleanChangelog = rawChangelog
                    .replace("\\r\\n", "\n")
                    .replace("\\n", "\n")
                    .replace("\\t", "    ")
                    .replace("\\\"", "\"")

                // Parse APK download URL (look for assets containing browser_download_url ending with .apk)
                val apkUrlMatcher = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"").matcher(json)
                if (!apkUrlMatcher.find()) return@withContext null
                val downloadUrl = apkUrlMatcher.group(1) ?: return@withContext null

                val currentVersion = getCurrentVersionName(context)
                
                if (isNewerVersion(currentVersion, latestVersion)) {
                    val snoozed = isUpdateSnoozed(context, latestVersion)
                    // Show popup if not snoozed OR if triggered manually by user
                    if (!snoozed || isManualCheck) {
                        return@withContext UpdateInfo(
                            version = latestVersion,
                            changelog = cleanChangelog,
                            downloadUrl = downloadUrl,
                            forceShow = isManualCheck
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    // Stream download of the APK file reporting progress from 0.0f to 1.0f
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (Exception) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
            }

            val fileLength = connection.contentLength
            val apkFile = File(context.externalCacheDir, "pixelvault_update.apk")
            if (apkFile.exists()) apkFile.delete()

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val data = ByteArray(4096)
                    var total: Long = 0
                    var count: Int
                    
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            onProgress(total.toFloat() / fileLength)
                        }
                        output.write(data, 0, count)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                onSuccess(apkFile)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError(e)
            }
        }
    }

    // Launch Android Package Installer
    fun triggerInstall(context: Context, apkFile: File) {
        try {
            // Check unknown source settings on Oreo+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
