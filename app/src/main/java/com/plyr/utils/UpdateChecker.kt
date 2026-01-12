package com.plyr.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility to check for updates from GitHub releases
 */
object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/josemri/plyr/releases/latest"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

    data class UpdateInfo(
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val isUpdateAvailable: Boolean
    )

    /**
     * Check if an update is available
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val currentVersion = getCurrentVersion(context)
            val lastCheck = getLastCheckTime(context)
            val now = System.currentTimeMillis()

            // For debugging: Always fetch fresh data (comment out cache check)
            // Check if we need to check again (avoid too frequent checks)
            // if (now - lastCheck < CHECK_INTERVAL_MS) {
            //     // Return cached result if available
            //     val cached = getCachedUpdateInfo(context)
            //     return@withContext cached
            // }

            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val tagName = json.optString("tag_name", "")
                val releaseName = json.optString("name", "")

                // Handle both "latest" tag and version tags like "v1.0.3"
                val latestVersion = if (tagName == "latest" || tagName.isEmpty()) {
                    // If tag is "latest" or empty, try to get version from name field
                    // Try to extract version from name (e.g., "Release 1.0.3" -> "1.0.3" or "1.0.3" -> "1.0.3")
                    val versionRegex = Regex("""(\d+\.\d+(?:\.\d+)?)""")
                    val matchResult = versionRegex.find(releaseName)
                    if (matchResult != null) {
                        matchResult.value
                    } else {
                        // Last resort: assume latest means a very high version to trigger update
                        "999.999.999"
                    }
                } else {
                    tagName.removePrefix("v")
                }

                val downloadUrl = json.optJSONArray("assets")?.let { assets ->
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            return@let asset.optString("browser_download_url", "")
                        }
                    }
                    ""
                } ?: ""

                val releaseNotes = json.optString("body", "")
                val isUpdateAvailable = isNewerVersion(currentVersion, latestVersion)

                val updateInfo = UpdateInfo(
                    latestVersion = latestVersion,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes,
                    isUpdateAvailable = isUpdateAvailable
                )

                saveLastCheckTime(context, now)
                cacheUpdateInfo(context, updateInfo)

                return@withContext updateInfo
            } else {
                return@withContext getCachedUpdateInfo(context)
            }
        } catch (e: Exception) {
            return@withContext getCachedUpdateInfo(context)
        }
    }

    /**
     * Get current app version
     */
    private fun getCurrentVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    /**
     * Compare two version strings
     */
    private fun isNewerVersion(current: String, latest: String): Boolean {
        try {
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLength = maxOf(currentParts.size, latestParts.size)

            for (i in 0 until maxLength) {
                val currentPart = currentParts.getOrNull(i) ?: 0
                val latestPart = latestParts.getOrNull(i) ?: 0

                if (latestPart > currentPart) {
                    return true
                }
                if (latestPart < currentPart) {
                    return false
                }
            }

            return false
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Get last check time from SharedPreferences
     */
    private fun getLastCheckTime(context: Context): Long {
        val prefs = context.getSharedPreferences("update_checker", Context.MODE_PRIVATE)
        return prefs.getLong("last_check", 0)
    }

    /**
     * Save last check time to SharedPreferences
     */
    private fun saveLastCheckTime(context: Context, time: Long) {
        val prefs = context.getSharedPreferences("update_checker", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_check", time).apply()
    }

    /**
     * Cache update info
     */
    private fun cacheUpdateInfo(context: Context, info: UpdateInfo) {
        val prefs = context.getSharedPreferences("update_checker", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("latest_version", info.latestVersion)
            .putString("download_url", info.downloadUrl)
            .putString("release_notes", info.releaseNotes)
            .putBoolean("is_update_available", info.isUpdateAvailable)
            .apply()
    }

    /**
     * Get cached update info
     */
    private fun getCachedUpdateInfo(context: Context): UpdateInfo? {
        val prefs = context.getSharedPreferences("update_checker", Context.MODE_PRIVATE)
        val latestVersion = prefs.getString("latest_version", null) ?: return null

        return UpdateInfo(
            latestVersion = latestVersion,
            downloadUrl = prefs.getString("download_url", "") ?: "",
            releaseNotes = prefs.getString("release_notes", "") ?: "",
            isUpdateAvailable = prefs.getBoolean("is_update_available", false)
        )
    }

    /**
     * Clear cached update info (useful for forcing a new check)
     */
    fun clearCache(context: Context) {
        val prefs = context.getSharedPreferences("update_checker", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
