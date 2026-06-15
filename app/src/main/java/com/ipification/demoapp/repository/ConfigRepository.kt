package com.ipification.demoapp.repository

import android.util.Log
import com.google.gson.Gson
import com.ipification.demoapp.BuildConfig
import com.ipification.demoapp.model.config.ApiConfigResponse
import com.ipification.demoapp.model.config.ClientConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class ConfigRepository {
    
    private val client = OkHttpClient()
    private val gson = Gson()
    
    companion object {
        private const val TAG = "ConfigRepository"
    }
    
    suspend fun fetchConfig(): Result<ApiConfigResponse> = withContext(Dispatchers.IO) {
        try {
            val configUrl = BuildConfig.BASE_URL + BuildConfig.CONFIG_PATH
            Log.d(TAG, "Fetching config from: $configUrl")
            
            val request = Request.Builder()
                .url(configUrl)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch config: ${response.code}")
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                
                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    Log.e(TAG, "Empty response body")
                    return@withContext Result.failure(IOException("Empty response"))
                }
                
                try {
                    val config = gson.fromJson(body, ApiConfigResponse::class.java)
                    Log.d(TAG, "Config fetched successfully: ${config.clients?.size} clients")
                    Result.success(config)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse config", e)
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching config", e)
            Result.failure(e)
        }
    }
}
