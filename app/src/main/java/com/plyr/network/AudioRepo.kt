package com.plyr.network

import com.plyr.model.AudioItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.IOException

object AudioRepository {

    private val client = OkHttpClient()

    fun requestAudioUrl(id: String, baseUrl: String, apiKey: String, callback: (String?) -> Unit) {
        println("AudioRepository: Solicitando audio para ID: $id")
        
        val url = "$baseUrl/audio?id=$id"
        println("AudioRepository: URL del request: $url")
        
        val request = Request.Builder()
            .url(url)
            .addHeader("X-API-KEY", apiKey)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("AudioRepository: Error en la red - ${e.message}")
                callback(null)
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val result = response.body?.string()
                    println("AudioRepository: Respuesta del servidor: '$result'")
                    println("AudioRepository: Código de respuesta: ${response.code}")
                    
                    if (result != null && result.trim().isNotEmpty()) {
                        val trimmedResult = result.trim()
                        println("AudioRepository: URL limpia: '$trimmedResult'")
                        callback(trimmedResult)
                    } else {
                        println("AudioRepository: Respuesta vacía o nula")
                        callback(null)
                    }
                } else {
                    val errorBody = response.body?.string()
                    println("AudioRepository: Error HTTP ${response.code} - ${response.message}")
                    println("AudioRepository: Error body: $errorBody")
                    callback(null)
                }
            }
        })
    }

    fun searchAudios(query: String, baseUrl: String, apiKey: String, callback: (List<AudioItem>?, String?) -> Unit) {
        val url = "$baseUrl/search?q=ytsearch:$query&n=50"
        val request = Request.Builder()
            .url(url)
            .addHeader("X-API-KEY", apiKey)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e.message)
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    try {
                        val listType = object : TypeToken<List<AudioItem>>() {}.type
                        val list = Gson().fromJson<List<AudioItem>>(json, listType)
                        callback(list, null)
                    } catch (e: Exception) {
                        callback(null, "Fallo al parsear la respuesta")
                    }
                } else {
                    val errorBody = response.body?.string()
                    println("AudioRepository: Search error HTTP ${response.code} - ${response.message}")
                    println("AudioRepository: Search error body: $errorBody")
                    callback(null, "Error HTTP ${response.code}: ${errorBody ?: response.message}")
                }
            }
        })
    }

    fun whoami(baseUrl: String, apiKey: String, callback: (String?, String?) -> Unit) {
        val url = "$baseUrl/whoami"
        val request = Request.Builder()
            .url(url)
            .addHeader("X-API-KEY", apiKey)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e.message)
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful) {
                    // Intentar parsear el JSON para obtener el usuario
                    try {
                        val json = Gson().fromJson(body, Map::class.java)
                        val user = json["user"] as? String
                        callback(user, null)
                    } catch (e: Exception) {
                        // Si no es JSON válido, devolver el body tal como está
                        callback(body, null)
                    }
                } else {
                    // Para errores HTTP, devolver el body como error
                    callback(null, body ?: "Error HTTP ${response.code}")
                }
            }
        })
    }
}
