package com.plyr.desktop.utils

import com.plyr.core.util.Logger
import com.plyr.desktop.PLYR_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Comprobación de actualizaciones para escritorio (M15).
 *
 * Adapta `com.plyr.utils.UpdateChecker` de Android: en vez de un `.apk` busca el
 * instalable del SO actual (`.deb`/`.rpm`/`.AppImage`, `.msi` o `.dmg`) entre los
 * assets de la última release de GitHub y, si no lo encuentra, cae a la página
 * de releases. En escritorio las actualizaciones llegan por el gestor de
 * paquetes, así que la URL sirve sobre todo de aviso.
 *
 * El resultado se cachea en memoria durante 24 h para no consultar la API en
 * cada arranque; [parseRelease] es puro (sin red) y por eso se puede testear.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"

    /** Versión del paquete de escritorio, generada desde `versionName` de `:app` (M34). */
    const val CURRENT_VERSION = PLYR_VERSION

    private const val GITHUB_API_URL = "https://api.github.com/repos/josemri/plyr/releases/latest"

    /** Página de releases, usada cuando la release no trae un instalable. */
    const val RELEASES_URL = "https://github.com/josemri/plyr/releases/latest"

    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val osName: String = System.getProperty("os.name").orEmpty().lowercase()

    private var lastCheck: Long = 0L
    private var cached: UpdateInfo? = null

    data class UpdateInfo(
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val isUpdateAvailable: Boolean,
    )

    /** Consulta la última release, con caché de 24 h salvo [force]. */
    suspend fun checkForUpdate(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < CHECK_INTERVAL_MS) return@withContext cached
        val json = fetchLatestReleaseJson() ?: return@withContext cached
        parseRelease(json, CURRENT_VERSION, preferredExtensions()).also {
            lastCheck = now
            cached = it
        }
    }

    fun clearCache() {
        lastCheck = 0L
        cached = null
    }

    /**
     * Interpreta el JSON de la release. Puro para poder testearlo sin red:
     * [extensions] son las extensiones de instalable preferidas, en orden.
     */
    fun parseRelease(json: String, currentVersion: String, extensions: List<String>): UpdateInfo {
        val root = JSONObject(json)
        val latestVersion = resolveLatestVersion(root, currentVersion)
        return UpdateInfo(
            latestVersion = latestVersion,
            downloadUrl = findDownloadUrl(root, extensions) ?: RELEASES_URL,
            releaseNotes = root.optString("body", ""),
            isUpdateAvailable = isNewerVersion(currentVersion, latestVersion),
        )
    }

    /** `1.2.0` < `1.3.0`; compara por componentes numéricos. */
    fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(currentParts.size, latestParts.size)) {
            val newer = latestParts.getOrElse(index) { 0 }
            val older = currentParts.getOrElse(index) { 0 }
            if (newer != older) return newer > older
        }
        return false
    }

    /** `v1.0.3` -> `1.0.3`; si el tag no es legible, se usa el nombre o la actual. */
    private fun resolveLatestVersion(root: JSONObject, currentVersion: String): String {
        val tagName = root.optString("tag_name", "")
        if (tagName.isNotEmpty() && tagName != "latest") return tagName.removePrefix("v")
        val match = Regex("""(\d+\.\d+(?:\.\d+)?)""").find(root.optString("name", ""))
        return match?.value ?: currentVersion
    }

    /** URL del primer asset con alguna de las extensiones preferidas. */
    private fun findDownloadUrl(root: JSONObject, extensions: List<String>): String? {
        val assets = root.optJSONArray("assets") ?: return null
        val names = (0 until assets.length()).map { assets.getJSONObject(it) }
        return extensions.firstNotNullOfOrNull { extension ->
            names.firstOrNull { it.optString("name").endsWith(extension, ignoreCase = true) }
        }?.optString("browser_download_url")?.takeIf { it.isNotBlank() }
    }

    private fun preferredExtensions(): List<String> = when {
        osName.contains("win") -> listOf(".msi", ".exe")
        osName.contains("mac") -> listOf(".dmg")
        else -> listOf(".deb", ".rpm", ".AppImage")
    }

    private fun fetchLatestReleaseJson(): String? = try {
        val request = Request.Builder()
            .url(GITHUB_API_URL)
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body.string() else null
        }
    } catch (e: Exception) {
        Logger.w(TAG, "No se pudo consultar la última release: ${e.message}")
        null
    }
}
