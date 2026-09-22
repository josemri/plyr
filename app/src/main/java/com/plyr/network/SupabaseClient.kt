package com.plyr.network

import android.content.Context
import android.util.Log
import com.plyr.model.Group
import com.plyr.model.GroupMember
import com.plyr.model.Recommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object SupabaseClient {
    private const val TAG = "SupabaseClient"

    private const val SUPABASE_URL = "https://mpfioblwpghlkulsryzu.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_CBiixr2FXsfTXwMhRFB5Qg_c5T7ePQ8"

    private const val GROUPS_TABLE = "groups"
    private const val GROUP_MEMBERS_TABLE = "group_members"
    private const val RECOMMENDATIONS_TABLE = "recommendations"

    // Groups operations
    suspend fun getGroups(): List<Group> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Fetching groups from Supabase...")
            val url = URL("$SUPABASE_URL/rest/v1/$GROUPS_TABLE?select=*")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")

            val responseCode = connection.responseCode
            Log.d(TAG, "📡 Response code: $responseCode")

            // Handle error responses
            if (responseCode != 200) {
                val errorStream = connection.errorStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
                Log.e(TAG, "❌ HTTP $responseCode Error response: $errorResponse")
                return@withContext emptyList()
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "📦 Raw response: $response")

            val jsonArray = JSONArray(response)
            Log.d(TAG, "📊 Number of groups found: ${jsonArray.length()}")

            val groups = mutableListOf<Group>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val group = Group(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    inviteCode = json.optString("invite_code", null),
                    groupType = json.getString("group_type"),
                    createdAt = parseTimestamp(json.optString("created_at", ""))
                )
                Log.d(TAG, "  ✅ Group: ${group.name} (${group.groupType}) - ID: ${group.id}")
                groups.add(group)
            }
            Log.d(TAG, "✨ Total groups loaded: ${groups.size}")
            groups
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching groups: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun createGroup(name: String, groupType: String, inviteCode: String? = null): Group? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🆕 Creating group: $name (type: $groupType, code: $inviteCode)")
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

            Log.d(TAG, "📤 Request body: ${json.toString()}")

            connection.outputStream.write(json.toString().toByteArray())

            val responseCode = connection.responseCode
            Log.d(TAG, "📡 Response code: $responseCode")

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "📦 Response: $response")

            val jsonArray = JSONArray(response)

            if (jsonArray.length() > 0) {
                val result = jsonArray.getJSONObject(0)
                val group = Group(
                    id = result.getString("id"),
                    name = result.getString("name"),
                    inviteCode = result.optString("invite_code", null),
                    groupType = result.getString("group_type"),
                    createdAt = parseTimestamp(result.optString("created_at", ""))
                )
                Log.d(TAG, "✅ Group created successfully: ${group.name}")
                group
            } else {
                Log.w(TAG, "⚠️ No group returned from creation")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating group: ${e.message}", e)
            null
        }
    }

    suspend fun joinGroup(inviteCode: String, nickname: String): GroupMember? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔗 Joining group with code: $inviteCode as $nickname")

            // First, find the group by invite code
            val groupUrl = URL("$SUPABASE_URL/rest/v1/$GROUPS_TABLE?invite_code=eq.$inviteCode&select=id")
            val groupConnection = groupUrl.openConnection() as HttpURLConnection
            groupConnection.requestMethod = "GET"
            groupConnection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            groupConnection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")

            val groupResponse = groupConnection.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "📦 Group search response: $groupResponse")

            val groupArray = JSONArray(groupResponse)

            if (groupArray.length() == 0) {
                Log.w(TAG, "⚠️ No group found with invite code: $inviteCode")
                return@withContext null
            }

            val groupId = groupArray.getJSONObject(0).getString("id")
            Log.d(TAG, "✅ Found group ID: $groupId")

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

            Log.d(TAG, "📤 Adding member request: ${json.toString()}")

            connection.outputStream.write(json.toString().toByteArray())

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "📦 Member add response: $response")

            val jsonArray = JSONArray(response)

            if (jsonArray.length() > 0) {
                val result = jsonArray.getJSONObject(0)
                val member = GroupMember(
                    id = result.getString("id"),
                    groupId = result.getString("group_id"),
                    nickname = result.getString("nickname"),
                    joinedAt = parseTimestamp(result.optString("joined_at", ""))
                )
                Log.d(TAG, "✅ Member added successfully: ${member.nickname}")
                member
            } else {
                Log.w(TAG, "⚠️ No member returned from join")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error joining group: ${e.message}", e)
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

            Log.d(TAG, "🔄 Fetching recommendations for group: ${groupId ?: "ALL"}")
            Log.d(TAG, "📡 URL: $urlString")

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")

            val responseCode = connection.responseCode
            Log.d(TAG, "📡 Response code: $responseCode")

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "📦 Raw response: $response")

            val jsonArray = JSONArray(response)
            Log.d(TAG, "📊 Number of recommendations found: ${jsonArray.length()}")

            val recommendations = mutableListOf<Recommendation>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val recommendation = Recommendation(
                    id = json.getString("id"),
                    groupId = json.getString("group_id"),
                    nickname = json.getString("nickname"),
                    url = json.getString("url"),
                    comment = json.optString("comment", null),
                    likes = json.optInt("likes", 0),
                    dislikes = json.optInt("dislikes", 0),
                    reportCount = json.optInt("report_count", 0),
                    createdAt = parseTimestamp(json.optString("created_at", ""))
                )
                Log.d(TAG, "  ✅ Recommendation by ${recommendation.nickname}: ${recommendation.url}")
                recommendations.add(recommendation)
            }
            Log.d(TAG, "✨ Total recommendations loaded: ${recommendations.size}")
            recommendations
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching recommendations: ${e.message}", e)
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
            Log.d(TAG, "🆕 Creating recommendation by $nickname in group $groupId")
            Log.d(TAG, "   URL: $url")
            Log.d(TAG, "   Comment: $comment")

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

            Log.d(TAG, "📤 Request body: ${json.toString()}")

            connection.outputStream.write(json.toString().toByteArray())

            val responseCode = connection.responseCode
            Log.d(TAG, "📡 Response code: $responseCode")

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "📦 Response: $response")

            val jsonArray = JSONArray(response)

            if (jsonArray.length() > 0) {
                val result = jsonArray.getJSONObject(0)
                val recommendation = Recommendation(
                    id = result.getString("id"),
                    groupId = result.getString("group_id"),
                    nickname = result.getString("nickname"),
                    url = result.getString("url"),
                    comment = result.optString("comment", null),
                    likes = result.optInt("likes", 0),
                    dislikes = result.optInt("dislikes", 0),
                    reportCount = result.optInt("report_count", 0),
                    createdAt = parseTimestamp(result.optString("created_at", ""))
                )
                Log.d(TAG, "✅ Recommendation created successfully!")
                recommendation
            } else {
                Log.w(TAG, "⚠️ No recommendation returned from creation")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating recommendation: ${e.message}", e)
            null
        }
    }

    private fun parseTimestamp(timestamp: String): Long {
        return try {
            if (timestamp.isBlank()) return System.currentTimeMillis()

            // Supabase devuelve timestamps en formato ISO 8601: "2025-01-12T10:30:00.000Z"
            val formats = listOf(
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", java.util.Locale.US),
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US),
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US),
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            )

            for (format in formats) {
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                try {
                    return format.parse(timestamp)?.time ?: continue
                } catch (_: Exception) {
                    continue
                }
            }

            System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing timestamp: $timestamp", e)
            System.currentTimeMillis()
        }
    }

    fun generateInviteCode(): String {
        return UUID.randomUUID().toString().substring(0, 8).uppercase()
    }
}
