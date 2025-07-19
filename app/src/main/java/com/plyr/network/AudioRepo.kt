package com.plyr.network

import com.plyr.model.AudioItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.IOException

object AudioRepository {

    private val client = OkHttpClient()

    fun requestAudioUrl(id: String, callback: (String?) -> Unit) {
        println("AudioRepository: Solicitando audio para ID: $id")
        
        val url = "https://607fd70ee495.ngrok-free.app/audio?id=$id"
        println("AudioRepository: URL del request: $url")
        
        val request = Request.Builder().url(url).get().build()

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
                    println("AudioRepository: Error HTTP ${response.code} - ${response.message}")
                    callback(null)
                }
            }
        })
    }

    fun searchAudios(query: String, callback: (List<AudioItem>?, String?) -> Unit) {
        val url = "https://607fd70ee495.ngrok-free.app/search?q=ytsearch:$query&n=50"
        val request = Request.Builder().url(url).get().build()

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
                    callback(null, "Respuesta inválida")
                }
            }
        })
    }
}
