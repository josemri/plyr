package com.plyr.utils

import android.content.Context
import androidx.core.content.edit
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

            // Evita pedir datos en cada arranque: solo refresca una vez cada 24h.
            // La disponibilidad se recalcula siempre contra la versión instalada,
            // para que un resultado cacheado no pueda quedar obsoleto al actualizar.
            if (now - lastCheck < CHECK_INTERVAL_MS) {
                val cached = getCachedUpdateInfo(context) ?: return@withContext null
                return@withContext cached.copy(
                    isUpdateAvailable = isNewerVersion(currentVersion, cached.latestVersion)
                )
            }

            val json = fetchLatestReleaseJson() ?: return@withContext getCachedUpdateInfo(context)
            val updateInfo = parseReleaseInfo(json, currentVersion)

            saveLastCheckTime(context, now)
            cacheUpdateInfo(context, updateInfo)
            updateInfo
        } catch (e: Exception) {
            getCachedUpdateInfo(context)
        }
    }

    /** Descarga el JSON de la última release, o null si la respuesta no es 200. */
    private fun fetchLatestReleaseJson(): JSONObject? {
        val connection = (URL(GITHUB_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/vnd.github.v3+json")
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(response)
    }

    /** Interpreta la release de GitHub y decide si hay actualización. */
    private fun parseReleaseInfo(json: JSONObject, currentVersion: String): UpdateInfo {
        val latestVersion = resolveLatestVersion(json, currentVersion)
        return UpdateInfo(
            latestVersion = latestVersion,
            downloadUrl = findApkDownloadUrl(json),
            releaseNotes = json.optString("body", ""),
            isUpdateAvailable = isNewerVersion(currentVersion, latestVersion)
        )
    }

    /**
     * La versión viene del `tag_name` (`v1.0.3` -> `1.0.3`). Si la release usa el
     * tag `latest` o vacío, se intenta extraer del nombre; sin nada legible, se
     * considera que la app está al día devolviendo la versión instalada.
     */
    private fun resolveLatestVersion(json: JSONObject, currentVersion: String): String {
        val tagName = json.optString("tag_name", "")
        if (tagName != "latest" && tagName.isNotEmpty()) return tagName.removePrefix("v")

        val releaseName = json.optString("name", "")
        val matchResult = Regex("""(\d+\.\d+(?:\.\d+)?)""").find(releaseName)
        return matchResult?.value ?: currentVersion
    }

    /** URL de descarga del primer asset `.apk`, o cadena vacía. */
    private fun findApkDownloadUrl(json: JSONObject): String {
        val assets = json.optJSONArray("assets") ?: return ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name", "").endsWith(".apk")) {
                return asset.optString("browser_download_url", "")
            }
        }
        return ""
    }

    /**
     * Get current app version
     */
    private fun getCurrentVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfoCompat(context.packageName)
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
        prefs.edit { putLong("last_check", time) }
    }

    /**
     * Cache update info
     */
    private fun cacheUpdateInfo(context: Context, info: UpdateInfo) {
        val prefs = context.getSharedPreferences("update_checker", Context.MODE_PRIVATE)
        prefs.edit {
            putString("latest_version", info.latestVersion)
            putString("download_url", info.downloadUrl)
            putString("release_notes", info.releaseNotes)
            putBoolean("is_update_available", info.isUpdateAvailable)
        }
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
        prefs.edit { clear() }
    }
}
