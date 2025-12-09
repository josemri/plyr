package com.plyr.assistant

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Simple chat message model
data class ChatMessage(
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

object AssistantStorage {
    private const val PREFS = "assistant_prefs"
    private const val KEY_CHAT = "assistant_chat"

    private val gson = Gson()

    fun loadChat(context: Context): List<ChatMessage> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CHAT, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            gson.fromJson<List<ChatMessage>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveChat(context: Context, messages: List<ChatMessage>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = gson.toJson(messages)
        prefs.edit().putString(KEY_CHAT, json).apply()
    }
}

