package com.ipification.demoapp.im

import android.util.Log
import com.ipification.demoapp.manager.ConfigManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class IMHelper {
    companion object {
        private const val TAG = "IMHelper"
        var currentState: String? = null
        var deviceToken: String? = null
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()

        fun registerDevice(dToken: String? = deviceToken, state: String? = currentState) {
            Log.d(TAG, "========================================")
            Log.d(TAG, "registerDevice() CALLED")
            Log.d(TAG, "deviceToken: $dToken")
            Log.d(TAG, "state: $state")
            Log.d(TAG, "========================================")
            
            if (dToken.isNullOrEmpty() || state.isNullOrEmpty()) {
                Log.e(TAG, "Registration failed - deviceToken or state is null/empty")
                Log.e(TAG, "deviceToken isEmpty: ${dToken.isNullOrEmpty()}")
                Log.e(TAG, "state isEmpty: ${state.isNullOrEmpty()}")
                return
            }

            val url = "${ConfigManager.getBackendUrl()}/device/register"
            val json = JSONObject().apply {
                put("device_id", state)
                put("device_token", dToken)
                put("device_type", "android")
            }.toString()
            
            Log.d(TAG, "Registering device to: $url")
            Log.d(TAG, "Request body: $json")

            val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url(url).post(requestBody).build()

            val client = OkHttpClient.Builder().build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "========================================")
                    Log.e(TAG, "Device registration network failure")
                    Log.e(TAG, "Error: ${e.message}")
                    Log.e(TAG, "========================================", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful) {
                        Log.d(TAG, "========================================")
                        Log.d(TAG, "Device registration successful")
                        Log.d(TAG, "Status code: ${response.code}")
                        Log.d(TAG, "Response: $responseBody")
                        Log.d(TAG, "========================================")
                    } else {
                        Log.e(TAG, "========================================")
                        Log.e(TAG, "Device registration failed")
                        Log.e(TAG, "Status code: ${response.code}")
                        Log.e(TAG, "Response: $responseBody")
                        Log.e(TAG, "========================================")
                    }
                }
            })
        }

        fun signIn(state: String?, callback: (success: Boolean, response: String) -> Unit) {
            val url = "${ConfigManager.getBackendUrl()}/auth/s2s/signin"
            val jsonObject = JSONObject().apply {
                put("state", state ?: "")
            }
            Log.e(TAG, "SignIn state: $url $state")
            val body = jsonObject.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url(url).post(body).build()

            val client = OkHttpClient.Builder().build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val error = e.localizedMessage ?: "SignIn request failed"
                    Log.e(TAG, "SignIn onFailure: $error", e)
                    callback(false, "SignIn FAILED: $error")
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val responseBody = response.body?.string() ?: ""

                        if (response.isSuccessful) {
                            Log.d(TAG, "SignIn successful: $responseBody")
                            callback(true, responseBody)
                        } else {
                            Log.e(TAG, "SignIn failed: ${response.code} - $responseBody")
                            callback(false, "SignIn FAILED [${response.code}]: $responseBody")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing response", e)
                        callback(false, e.localizedMessage ?: "Error processing response")
                    }
                }
            })
        }
    }
}
