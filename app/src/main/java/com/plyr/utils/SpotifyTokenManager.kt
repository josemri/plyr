package com.plyr.utils

import android.content.Context
import android.util.Log
import com.plyr.network.SpotifyRepository
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * SpotifyTokenManager - Gestor centralizado de tokens de Spotify
 *
 * Este manager se encarga de:
 * - Verificar automáticamente si los tokens han expirado
 * - Renovar tokens automáticamente cuando sea necesario
 * - Proporcionar tokens válidos para todas las llamadas a la API
 * - Manejar la renovación de forma thread-safe
 * - Evitar múltiples renovaciones simultáneas
 */
object SpotifyTokenManager {

    private const val TAG = "SpotifyTokenManager"

    // Margen de seguridad: renovar 5 minutos antes de que expire
    private const val EXPIRATION_BUFFER_MS = 5 * 60 * 1000L // 5 minutos

    // Control de renovación en progreso
    private val isRefreshing = AtomicBoolean(false)
    private var refreshDeferred: Deferred<String?>? = null

    /**
     * Obtiene un token de acceso válido, renovándolo automáticamente si es necesario.
     *
     * @param context Contexto de la aplicación
     * @return Token de acceso válido o null si no se pudo obtener/renovar
     */
    suspend fun getValidAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Verificar si tenemos un token actual válido
            val currentToken = Config.getSpotifyAccessToken(context)
            if (currentToken != null && !isTokenExpired(context)) {
                Log.d(TAG, "Token actual es válido")
                return@withContext currentToken
            }

            // 2. El token ha expirado o no existe, necesitamos renovarlo
            Log.d(TAG, "Token expirado o inexistente, iniciando renovación")
            return@withContext refreshTokenSafely(context)

        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo token válido", e)
            return@withContext null
        }
    }

    /**
     * Verifica si el token actual ha expirado o está cerca de expirar.
     *
     * @param context Contexto de la aplicación
     * @return true si el token ha expirado o está cerca de expirar
     */
    private fun isTokenExpired(context: Context): Boolean {
        val tokenTimestamp = Config.getSpotifyTokenTimestamp(context)
        val expiresIn = Config.getSpotifyTokenExpiresIn(context)

        if (tokenTimestamp == 0L || expiresIn == 0) {
            Log.d(TAG, "No hay información de expiración del token")
            return true
        }

        val expirationTime = tokenTimestamp + (expiresIn * 1000L)
        val currentTime = System.currentTimeMillis()
        val timeToExpiry = expirationTime - currentTime

        val isExpired = timeToExpiry <= EXPIRATION_BUFFER_MS

        Log.d(TAG, "Verificación de expiración: tiempo restante = ${timeToExpiry / 1000}s, expirado = $isExpired")

        return isExpired
    }

    /**
     * Renueva el token de forma thread-safe, evitando múltiples renovaciones simultáneas.
     *
     * @param context Contexto de la aplicación
     * @return Token renovado o null si falló
     */
    private suspend fun refreshTokenSafely(context: Context): String? = withContext(Dispatchers.IO) {
        // Si ya hay una renovación en progreso, esperar a que termine
        if (isRefreshing.get()) {
            Log.d(TAG, "Renovación ya en progreso, esperando...")
            return@withContext refreshDeferred?.await()
        }

        // Iniciar nueva renovación
        if (isRefreshing.compareAndSet(false, true)) {
            Log.d(TAG, "Iniciando nueva renovación de token")

            refreshDeferred = async {
                try {
                    refreshTokenInternal(context)
                } finally {
                    isRefreshing.set(false)
                    refreshDeferred = null
                }
            }

            return@withContext refreshDeferred!!.await()
        } else {
            // Otra corrutina ganó la carrera, esperar su resultado
            return@withContext refreshDeferred?.await()
        }
    }

    /**
     * Realiza la renovación real del token.
     *
     * @param context Contexto de la aplicación
     * @return Token renovado o null si falló
     */
    private suspend fun refreshTokenInternal(context: Context): String? = suspendCoroutine { continuation ->
        val refreshToken = Config.getSpotifyRefreshToken(context)

        if (refreshToken == null) {
            Log.e(TAG, "No hay refresh token disponible")
            continuation.resume(null)
            return@suspendCoroutine
        }

        Log.d(TAG, "Enviando solicitud de renovación a Spotify...")

        SpotifyRepository.refreshAccessToken(context, refreshToken) { newAccessToken, error ->
            if (error != null) {
                Log.e(TAG, "Error renovando token: $error")
                continuation.resume(null)
            } else if (newAccessToken != null) {
                // Guardar el nuevo token con timestamp actual
                val expiresIn = 3600 // Spotify tokens duran 1 hora
                Config.setSpotifyTokens(context, newAccessToken, refreshToken, expiresIn)

                Log.d(TAG, "Token renovado exitosamente")
                continuation.resume(newAccessToken)
            } else {
                Log.e(TAG, "Respuesta inesperada al renovar token")
                continuation.resume(null)
            }
        }
    }

    /**
     * Invalida el token actual, forzando una renovación en la próxima solicitud.
     * Útil cuando sabemos que el token ha sido revocado o es inválido.
     *
     * @param context Contexto de la aplicación
     */
    fun invalidateCurrentToken(context: Context) {
        Log.d(TAG, "Invalidando token actual")
        Config.clearSpotifyAccessToken(context)
    }

    /**
     * Verifica si hay credenciales de Spotify configuradas.
     *
     * @param context Contexto de la aplicación
     * @return true si hay refresh token disponible
     */
    fun hasValidCredentials(context: Context): Boolean {
        return Config.getSpotifyRefreshToken(context) != null
    }

    /**
     * Ejecuta una operación con un token válido, manejando automáticamente la renovación.
     *
     * @param context Contexto de la aplicación
     * @param operation Operación a ejecutar con el token
     * @return Resultado de la operación o null si no se pudo obtener token válido
     */
    suspend fun <T> withValidToken(
        context: Context,
        operation: suspend (token: String) -> T?
    ): T? {
        val token = getValidAccessToken(context)
        return if (token != null) {
            try {
                operation(token)
            } catch (e: Exception) {
                Log.e(TAG, "Error ejecutando operación con token", e)
                null
            }
        } else {
            Log.e(TAG, "No se pudo obtener token válido para la operación")
            null
        }
    }
}
