package com.ipification.mobile.sdk.ts43

import android.content.Context
import android.util.Log
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.exception.IPificationError
import com.ipification.mobile.sdk.ts43.helper.CarrierHintHelper
import com.ipification.mobile.sdk.ts43.request.TS43AuthRequest
import com.ipification.mobile.sdk.ts43.request.TS43TokenRequest
import com.ipification.mobile.sdk.ts43.response.TS43AuthResponse
import com.ipification.mobile.sdk.ts43.response.TS43TokenResponse
import com.ipification.mobile.sdk.ip.utils.DeviceUtils
import com.ipification.mobile.sdk.ip.utils.DebugMetricContext
import com.ipification.mobile.sdk.ip.utils.DebugNetworkMetrics
import com.ipification.mobile.sdk.ip.utils.IPLogs
import com.ipification.mobile.sdk.ip.utils.LogUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Internal service class for TS43 API operations.
 * Handles network requests to the backend TS43 endpoints.
 * 
 * Note: All network methods use async callbacks for background safety.
 */
internal class TS43InternalService(private val context: Context) {

    private val ipConfig = IPConfiguration.getInstance()
    private val debug = ipConfig.debug

    /**
     * Perform TS43 CIBA authentication request asynchronously.
     * 
     * @param request The authentication request parameters.
     * @param onSuccess Callback with TS43AuthResponse on success.
     * @param onError Callback with Exception on failure.
     */
    fun performAuthRequest(
        request: TS43AuthRequest,
        onSuccess: (TS43AuthResponse) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val url = "${ipConfig.getTS43BackendUrl()}${ipConfig.TS43_AUTH_PATH}"
        val jsonBody = request.toJsonString()
        val metricContext = if (debug) {
            DebugMetricContext(
                sessionId = UUID.randomUUID().toString(),
                stage = "ts43_auth",
                transport = "okhttp",
                url = url
            )
        } else {
            null
        }

        onLog("========== TS43 AUTH REQUEST ==========")
        onLog("URL: $url")
        onLog("Method: POST")
        if (debug) {
            onLog("Body bytes: ${DebugNetworkMetrics.utf8Size(jsonBody)}")
            onLog("Request body:\n$jsonBody")
        }
        onLog("Operation: ${request.operation.value}")
        onLog("========================================")

        val client = createOkHttpClient()
        val requestBody = jsonBody.toRequestBody(CONTENT_TYPE_JSON.toMediaType())
        
        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", CONTENT_TYPE_JSON)
            .build()
        val requestHeadersBytes = if (debug) httpRequest.headers.byteCount() else 0L
        val requestBodyBytes = if (debug) DebugNetworkMetrics.utf8Size(jsonBody) else 0L

        client.newCall(httpRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                metricContext?.let {
                    DebugNetworkMetrics.logFailureMetric(
                        context = it,
                        requestHeadersBytes = requestHeadersBytes,
                        requestBodyBytes = requestBodyBytes,
                        errorMessage = e.localizedMessage
                    )
                }
                onLog("========== TS43 AUTH ERROR ==========")
                onLog("Error: ${e.message}")
                onLog("=====================================")
                onError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string() ?: ""
                    val responseCode = it.code
                    metricContext?.let { context ->
                        DebugNetworkMetrics.logHttpMetric(
                            context = context,
                            requestHeadersBytes = requestHeadersBytes,
                            requestBodyBytes = requestBodyBytes,
                            responseHeadersBytes = it.headers.byteCount(),
                            responseBodyBytes = DebugNetworkMetrics.utf8Size(responseBody),
                            httpCode = responseCode
                        )
                    }

                    onLog("========== TS43 AUTH RESPONSE ==========")
                    onLog("Status Code: $responseCode")
                    if (debug) {
                        onLog("Response bytes: ${DebugNetworkMetrics.utf8Size(responseBody)}")
                        onLog("Response body:\n$responseBody")
                    }
                    onLog("=========================================")

                    if (!it.isSuccessful) {
                        onError(IOException("TS43 Auth failed with code $responseCode: $responseBody"))
                        return
                    }

                    try {
                        val authResponse = TS43AuthResponse.fromJson(responseBody)
                        onSuccess(authResponse)
                    } catch (e: Exception) {
                        onError(e)
                    }
                }
            }
        })
    }

    /**
     * Perform TS43 token exchange request asynchronously.
     * 
     * @param request The token exchange request parameters.
     * @param onSuccess Callback with TS43TokenResponse on success.
     * @param onError Callback with Exception on failure.
     */
    fun performTokenExchange(
        request: TS43TokenRequest,
        onSuccess: (TS43TokenResponse) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val url = "${ipConfig.getTS43BackendUrl()}${ipConfig.TS43_TOKEN_PATH}"
        val jsonBody = request.toJsonString()
        val metricContext = if (debug) {
            DebugMetricContext(
                sessionId = UUID.randomUUID().toString(),
                stage = "ts43_token_exchange",
                transport = "okhttp",
                url = url
            )
        } else {
            null
        }

        onLog("========== TS43 TOKEN EXCHANGE REQUEST ==========")
        onLog("URL: $url")
        onLog("Method: POST")
        if (debug) {
            onLog("Body bytes: ${DebugNetworkMetrics.utf8Size(jsonBody)}")
            onLog("Request body:\n$jsonBody")
        }
        onLog("=================================================")

        val client = createOkHttpClient()
        val requestBody = jsonBody.toRequestBody(CONTENT_TYPE_JSON.toMediaType())

        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", CONTENT_TYPE_JSON)
            .build()
        val requestHeadersBytes = if (debug) httpRequest.headers.byteCount() else 0L
        val requestBodyBytes = if (debug) DebugNetworkMetrics.utf8Size(jsonBody) else 0L

        client.newCall(httpRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                metricContext?.let {
                    DebugNetworkMetrics.logFailureMetric(
                        context = it,
                        requestHeadersBytes = requestHeadersBytes,
                        requestBodyBytes = requestBodyBytes,
                        errorMessage = e.localizedMessage
                    )
                }
                onLog("========== TS43 TOKEN EXCHANGE ERROR ==========")
                onLog("Error: ${e.message}")
                onLog("===============================================")
                onError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string() ?: ""
                    val responseCode = it.code
                    metricContext?.let { context ->
                        DebugNetworkMetrics.logHttpMetric(
                            context = context,
                            requestHeadersBytes = requestHeadersBytes,
                            requestBodyBytes = requestBodyBytes,
                            responseHeadersBytes = it.headers.byteCount(),
                            responseBodyBytes = DebugNetworkMetrics.utf8Size(responseBody),
                            httpCode = responseCode
                        )
                    }

                    onLog("========== TS43 TOKEN EXCHANGE RESPONSE ==========")
                    onLog("Status Code: $responseCode")
                    if (debug) {
                        onLog("Response bytes: ${DebugNetworkMetrics.utf8Size(responseBody)}")
                        onLog("Response body:\n$responseBody")
                    }
                    onLog("==================================================")

                    if (!it.isSuccessful) {
                        onError(IOException("TS43 Token exchange failed with code $responseCode: $responseBody"))
                        return
                    }

                    try {
                        val tokenResponse = TS43TokenResponse.fromJson(responseBody)
                        onSuccess(tokenResponse)
                    } catch (e: Exception) {
                        onError(e)
                    }
                }
            }
        })
    }

    /**
     * Extract VP token from Credential Manager response JSON.
     * 
     * The usual credential response has structure:
     * {"protocol":"...","data":{"vp_token":{"ipification.com":["token_value"]}}}
     *
     * Some providers return a different verifier key, a plain string, or an
     * array directly under vp_token. Keep extraction tolerant and avoid logging
     * the token value itself.
     * 
     * @param credentialJson The JSON string from Credential Manager.
     * @return Extracted VP token string, or null if extraction fails.
     */
    fun extractVpToken(credentialJson: String): String? {
        return try {
            val root = JSONObject(credentialJson)
            val token = root.optJSONObject("data")
                ?.let { extractVpTokenValue(it.opt("vp_token"), "data.vp_token") }
                ?: extractVpTokenValue(root.opt("vp_token"), "vp_token")
                
            if (token != null) {
                onLog("VP Token extracted successfully from ${token.path} (length: ${token.value.length})")
            } else {
                onLog("Error: Failed to extract VP token from credential JSON (${describeCredentialShape(root)})")
            }
            
            token?.value
        } catch (e: Exception) {
            onLog("Error extracting VP token: ${e.message}")
            null
        }
    }

    private data class VpTokenValue(val value: String, val path: String)

    private fun extractVpTokenValue(value: Any?, path: String): VpTokenValue? {
        if (value == null || value == JSONObject.NULL) {
            return null
        }

        return when (value) {
            is String -> value
                .takeIf(String::isNotBlank)
                ?.let { VpTokenValue(it, path) }
            is JSONArray -> firstTokenInArray(value, path)
            is JSONObject -> firstTokenInObject(value, path)
            else -> value.toString()
                .takeIf(String::isNotBlank)
                ?.let { VpTokenValue(it, path) }
        }
    }

    private fun firstTokenInArray(array: JSONArray, path: String): VpTokenValue? {
        for (index in 0 until array.length()) {
            extractVpTokenValue(array.opt(index), "$path[$index]")?.let { return it }
        }
        return null
    }

    private fun firstTokenInObject(jsonObject: JSONObject, path: String): VpTokenValue? {
        val preferredKeys = listOf("ipification.com", "https://ipification.com")
        for (key in preferredKeys) {
            if (jsonObject.has(key)) {
                extractVpTokenValue(jsonObject.opt(key), "$path.$key")?.let { return it }
            }
        }

        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            extractVpTokenValue(jsonObject.opt(key), "$path.$key")?.let { return it }
        }
        return null
    }

    private fun describeCredentialShape(root: JSONObject): String {
        val rootKeys = root.keys().asSequence().toList().joinToString(",")
        val dataKeys = root.optJSONObject("data")
            ?.keys()
            ?.asSequence()
            ?.toList()
            ?.joinToString(",")
            .orEmpty()
        return "root_keys=[$rootKeys] data_keys=[$dataKeys]"
    }

    /**
     * Get carrier hint (MCC+MNC) from the device.
     * Uses the voice SIM's network operator.
     * 
     * @return MCC+MNC string (e.g., "310260"), or default carrier hint if unavailable.
     */
    fun getCarrierHint(): String {
        return try {
            val deviceUtils = DeviceUtils.getInstance(context)
            val simOperator = deviceUtils.activeSimOperator()
            val mcc = simOperator.getMCC()
            val mnc = simOperator.getMNC()
                
            if (mcc.isNotBlank() && mnc.isNotBlank()) {
                val carrierHint = "$mcc$mnc"
                onLog("Carrier hint from device: $carrierHint")
                carrierHint
            } else {
                onLog("Using default carrier hint: ${ipConfig.TS43_DEFAULT_CARRIER_HINT}")
                ipConfig.TS43_DEFAULT_CARRIER_HINT
            }
        } catch (e: Exception) {
            onLog("Error getting carrier hint: ${e.message}")
            ipConfig.TS43_DEFAULT_CARRIER_HINT
        }
    }

    /**
     * Get carrier hint (MCC+MNC) from phone number using CarrierHintHelper.
     * 
     * @param phoneNumber Phone number with country code (e.g., "84932383421")
     * @return MCC+MNC string if found, or null if not recognized.
     */
    fun getCarrierHintFromPhoneNumber(phoneNumber: String?): String? {
        return CarrierHintHelper.getCarrierHint(phoneNumber)
    }

    companion object {
        private const val TAG = "TS43InternalService"
        private const val CONTENT_TYPE_JSON = "application/json"
    }

    /**
     * Create configured OkHttpClient for TS43 requests.
     */
    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(ipConfig.AUTH_CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(ipConfig.AUTH_READ_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(ipConfig.AUTH_READ_TIMEOUT, TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * Create IPificationError from exception.
     */
    fun createError(
        errorCode: Int,
        errorMessage: String,
        exception: Exception? = null,
        httpCode: Int? = null
    ): IPificationError {
        return IPificationError().apply {
            this.sdkErrorCode = errorCode
            this.serverDescription = errorMessage
            this.exception = exception
            this.httpCode = httpCode
        }
    }

    private fun onLog(message: String) {
        if (debug) {
            Log.d(TAG, message)
            IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - $TAG - $message\n"
        }
    }
}
