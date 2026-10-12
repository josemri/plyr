package com.plyr.network

import com.plyr.core.NetworkConfig
import com.plyr.core.util.Logger
import com.plyr.model.Group
import com.plyr.model.GroupMember
import com.plyr.model.Recommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.ParsePosition
import java.util.UUID

object SupabaseClient {
    private const val TAG = "SupabaseClient"

    private val SUPABASE_URL: String get() = NetworkConfig.supabaseUrl
    private val SUPABASE_ANON_KEY: String get() = NetworkConfig.supabaseAnonKey

    private const val GROUPS_TABLE = "groups"
    private const val GROUP_MEMBERS_TABLE = "group_members"
    private const val RECOMMENDATIONS_TABLE = "recommendations"

    // Groups operations
    suspend fun getGroups(): List<Group> = withContext(Dispatchers.IO) {
        try {
            Logger.d(TAG, "🔄 Fetching groups from Supabase...")
            val url = URL("$SUPABASE_URL/rest/v1/$GROUPS_TABLE?select=*")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")

            val responseCode = connection.responseCode
            Logger.d(TAG, "📡 Response code: $responseCode")

            // Handle error responses
            if (responseCode != 200) {
                val errorStream = connection.errorStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
                Logger.e(TAG, "❌ HTTP $responseCode Error response: ${redact(errorResponse)}")
                return@withContext emptyList()
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Logger.d(TAG, "📦 Raw response: ${describeBody(response)}")

            val jsonArray = JSONArray(response)
            Logger.d(TAG, "📊 Number of groups found: ${jsonArray.length()}")

            val groups = mutableListOf<Group>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val group = Group(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    inviteCode = json.optNullableString("invite_code"),
                    groupType = json.getString("group_type"),
                    createdAt = parseTimestamp(json.optString("created_at", ""))
                )
                Logger.d(TAG, "  ✅ Group: ${redact(group.name)} (${group.groupType}) - ID: ${redact(group.id)}")
                groups.add(group)
            }
            Logger.d(TAG, "✨ Total groups loaded: ${groups.size}")
            groups
        } catch (e: Exception) {
            Logger.e(TAG, "❌ Error fetching groups: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun createGroup(name: String, groupType: String, inviteCode: String? = null): Group? = withContext(Dispatchers.IO) {
        try {
            Logger.d(TAG, "🆕 Creating group: ${redact(name)} (type: $groupType, code: ${redact(inviteCode)})")
            val url = URL("$SUPABASE_URL/rest/v1/$GROUPS_TABLE")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=representation")
            connection.doOutput = true

            val json = JSONObject().apply {
                put("name", name)
                put("group_type", groupType)
                if (inviteCode != null) put("invite_code", inviteCode)
            }

            Logger.d(TAG, "📤 Request body: ${describeBody(json.toString())}")

            connection.outputStream.write(json.toString().toByteArray())

            val responseCode = connection.responseCode
            Logger.d(TAG, "📡 Response code: $responseCode")

            // B33: los 4xx no lanzan FileNotFoundException; se lee el cuerpo y se
            // devuelve null (antes: "error: null" sin más diagnóstico).
            if (responseCode !in 200..299) {
                Logger.e(TAG, "❌ HTTP $responseCode al crear grupo: ${redact(readBody(connection))}")
                return@withContext null
            }

            val response = readBody(connection)
            Logger.d(TAG, "📦 Response: ${describeBody(response)}")

            val jsonArray = JSONArray(response)

            if (jsonArray.length() > 0) {
                val result = jsonArray.getJSONObject(0)
                val group = Group(
                    id = result.getString("id"),
                    name = result.getString("name"),
                    inviteCode = result.optNullableString("invite_code"),
                    groupType = result.getString("group_type"),
                    createdAt = parseTimestamp(result.optString("created_at", ""))
                )
                Logger.d(TAG, "✅ Group created successfully: ${redact(group.name)}")
                group
            } else {
                Logger.w(TAG, "⚠️ No group returned from creation")
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "❌ Error creating group: ${e.message}", e)
            null
        }
    }

    suspend fun joinGroup(inviteCode: String, nickname: String): GroupMember? = withContext(Dispatchers.IO) {
        try {
            Logger.d(TAG, "🔗 Joining group with code: ${redact(inviteCode)} as ${redact(nickname)}")

            // First, find the group by invite code (B33: el código se URL-encodea
            // para no romper la semántica del filtro si lleva &, # o ,)
            val encodedInvite = URLEncoder.encode(inviteCode, "UTF-8")
            val groupUrl = URL("$SUPABASE_URL/rest/v1/$GROUPS_TABLE?invite_code=eq.$encodedInvite&select=id")
            val groupConnection = groupUrl.openConnection() as HttpURLConnection
            groupConnection.requestMethod = "GET"
            groupConnection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            groupConnection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")

            val groupResponse = readBody(groupConnection)
            Logger.d(TAG, "📦 Group search response: ${describeBody(groupResponse)}")

            val groupArray = JSONArray(groupResponse)

            if (groupArray.length() == 0) {
                Logger.w(TAG, "⚠️ No group found with invite code: ${redact(inviteCode)}")
                return@withContext null
            }

            val groupId = groupArray.getJSONObject(0).getString("id")
            Logger.d(TAG, "✅ Found group ID: ${redact(groupId)}")

            // Then add the member
            val url = URL("$SUPABASE_URL/rest/v1/$GROUP_MEMBERS_TABLE")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=representation")
            connection.doOutput = true

            val json = JSONObject().apply {
                put("group_id", groupId)
                put("nickname", nickname)
            }

            Logger.d(TAG, "📤 Adding member request: ${describeBody(json.toString())}")

            connection.outputStream.write(json.toString().toByteArray())

            val responseCode = connection.responseCode
            Logger.d(TAG, "📡 Response code: $responseCode")

            // B33: no lanzar y leer el cuerpo en fallos
            if (responseCode !in 200..299) {
                Logger.e(TAG, "❌ HTTP $responseCode al añadir miembro: ${redact(readBody(connection))}")
                return@withContext null
            }

            val response = readBody(connection)
            Logger.d(TAG, "📦 Member add response: ${describeBody(response)}")

            val jsonArray = JSONArray(response)

            if (jsonArray.length() > 0) {
                val result = jsonArray.getJSONObject(0)
                val member = GroupMember(
                    id = result.getString("id"),
                    groupId = result.getString("group_id"),
                    nickname = result.getString("nickname"),
                    joinedAt = parseTimestamp(result.optString("joined_at", ""))
                )
                Logger.d(TAG, "✅ Member added successfully: ${redact(member.nickname)}")
                member
            } else {
                Logger.w(TAG, "⚠️ No member returned from join")
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "❌ Error joining group: ${e.message}", e)
            null
        }
    }

    // Recommendations operations
    suspend fun getRecommendations(groupId: String? = null): List<Recommendation> = withContext(Dispatchers.IO) {
        try {
            val urlString = if (groupId != null) {
                "$SUPABASE_URL/rest/v1/$RECOMMENDATIONS_TABLE?group_id=eq.$groupId&select=*&order=created_at.desc"
            } else {
                "$SUPABASE_URL/rest/v1/$RECOMMENDATIONS_TABLE?select=*&order=created_at.desc"
            }

            Logger.d(TAG, "🔄 Fetching recommendations for group: ${groupId ?: "ALL"}")
            Logger.d(TAG, "📡 URL: $urlString")

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")

            val responseCode = connection.responseCode
            Logger.d(TAG, "📡 Response code: $responseCode")

            val response = readBody(connection)
            Logger.d(TAG, "📦 Raw response: ${describeBody(response)}")

            val jsonArray = JSONArray(response)
            Logger.d(TAG, "📊 Number of recommendations found: ${jsonArray.length()}")

            val recommendations = mutableListOf<Recommendation>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val recommendation = Recommendation(
                    id = json.getString("id"),
                    groupId = json.getString("group_id"),
                    nickname = json.getString("nickname"),
                    url = json.getString("url"),
                    comment = json.optNullableString("comment"),
                    likes = json.optInt("likes", 0),
                    dislikes = json.optInt("dislikes", 0),
                    reportCount = json.optInt("report_count", 0),
                    createdAt = parseTimestamp(json.optString("created_at", ""))
                )
                Logger.d(TAG, "  ✅ Recommendation by ${redact(recommendation.nickname)}: ${redact(recommendation.url)}")
                recommendations.add(recommendation)
            }
            Logger.d(TAG, "✨ Total recommendations loaded: ${recommendations.size}")
            recommendations
        } catch (e: Exception) {
            Logger.e(TAG, "❌ Error fetching recommendations: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun createRecommendation(
        groupId: String,
        nickname: String,
        url: String,
        comment: String? = null
    ): Recommendation? = withContext(Dispatchers.IO) {
        try {
            Logger.d(TAG, "🆕 Creating recommendation by ${redact(nickname)} in group ${redact(groupId)}")
            Logger.d(TAG, "   URL: ${redact(url)}")
            Logger.d(TAG, "   Comment: ${redact(comment)}")

            val urlObj = URL("$SUPABASE_URL/rest/v1/$RECOMMENDATIONS_TABLE")
            val connection = urlObj.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "return=representation")
            connection.doOutput = true

            val json = JSONObject().apply {
                put("group_id", groupId)
                put("nickname", nickname)
                put("url", url)
                if (comment != null) put("comment", comment)
            }

            Logger.d(TAG, "📤 Request body: ${describeBody(json.toString())}")

            connection.outputStream.write(json.toString().toByteArray())

            val responseCode = connection.responseCode
            Logger.d(TAG, "📡 Response code: $responseCode")

            // B33: no lanzar y leer el cuerpo en fallos
            if (responseCode !in 200..299) {
                Logger.e(TAG, "❌ HTTP $responseCode al recomendar: ${redact(readBody(connection))}")
                return@withContext null
            }

            val response = readBody(connection)
            Logger.d(TAG, "📦 Response: ${describeBody(response)}")

            val jsonArray = JSONArray(response)

            if (jsonArray.length() > 0) {
                val result = jsonArray.getJSONObject(0)
                val recommendation = Recommendation(
                    id = result.getString("id"),
                    groupId = result.getString("group_id"),
                    nickname = result.getString("nickname"),
                    url = result.getString("url"),
                    comment = result.optNullableString("comment"),
                    likes = result.optInt("likes", 0),
                    dislikes = result.optInt("dislikes", 0),
                    reportCount = result.optInt("report_count", 0),
                    createdAt = parseTimestamp(result.optString("created_at", ""))
                )
                Logger.d(TAG, "✅ Recommendation created successfully!")
                recommendation
            } else {
                Logger.w(TAG, "⚠️ No recommendation returned from creation")
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "❌ Error creating recommendation: ${e.message}", e)
            null
        }
    }

    /**
     * Redacta un valor potencialmente identificativo (B... S7): nicknames,
     * códigos de invitación, nombres de grupo, IDs, URLs de recomendación y
     * comentarios no deben acabar en logcat. Se conserva solo la longitud para
     * que el log siga sirviendo de diagnóstico sin filtrar el contenido.
     */
    private fun redact(value: String?): String =
        if (value.isNullOrBlank()) "<vacío>" else "<redactado:${value.length}>"

    /** Describe un cuerpo JSON sin volcar su contenido (S7). */
    private fun describeBody(body: String): String =
        if (body.isBlank()) "<vacío>" else "<cuerpo de ${body.length} caracteres>"

    /**
     * Lee el cuerpo de una respuesta HTTP sin lanzar `FileNotFoundException` en
     * los 4xx (B33): `inputStream` solo existe en 2xx; el cuerpo del error está
     * en `errorStream` y se pierde si no se lee a propósito.
     */
    private fun readBody(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }

    /** `optString(k, null)` devuelve la cadena "null" si el JSON es null (B33). */
    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key, null)

    // Formatos ISO 8601 que emite Supabase (timestamptz). Un `ThreadLocal` por
    // hilo: SimpleDateFormat no es thread-safe y estos métodos se llaman desde
    // varios hilos de Dispatchers.IO.
    private val timestampFormats: ThreadLocal<List<java.text.SimpleDateFormat>> =
        object : ThreadLocal<List<java.text.SimpleDateFormat>>() {
            override fun initialValue(): List<java.text.SimpleDateFormat> = listOf(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
            ).map { pattern ->
                java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
            }
        }

    /**
     * Parsea un timestamp de Supabase de forma estricta (B25). Soporta tanto
     * `Z` como `+HH:MM` (patrón `XXX`), consume TODA la cadena (no ignora texto
     * sobrante) y no fabrica un "ahora" falso cuando algo falla: devuelve `0L`
     * para que el timestamp ilegible no se disfrace de recién creado.
     */
    private fun parseTimestamp(timestamp: String): Long {
        if (timestamp.isBlank()) return 0L
        for (format in timestampFormats.get()) {
            val pos = ParsePosition(0)
            val date = format.parse(timestamp, pos) ?: continue
            if (pos.index == timestamp.length) return date.time
        }
        return 0L
    }

    fun generateInviteCode(): String {
        return UUID.randomUUID().toString().substring(0, 8).uppercase()
    }
}
