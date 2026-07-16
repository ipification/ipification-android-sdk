package com.ipification.mobile.sdk.sms

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.SubmitErrorService
import com.ipification.mobile.sdk.ip.exception.IPificationError
import com.ipification.mobile.sdk.sms.callback.SMSCallback
import com.ipification.mobile.sdk.sms.response.SMSAuthResponse
import com.ipification.mobile.sdk.sms.response.SMSTokenResponse
import com.ipification.mobile.sdk.ip.utils.IPLogs
import com.ipification.mobile.sdk.ip.utils.LogUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SMS Verification Services for CIBA-based OTP phone number verification.
 *
 * This service provides SMS-based verification using the CIBA (Client-Initiated Backchannel Authentication)
 * flow with SMS channel. The auth server sends an OTP code to the user's phone via SMS,
 * and the user enters the code in the app to complete verification.
 *
 * ## Architecture
 *
 * The SDK handles communication with your backend, which then communicates with the IPification auth server:
 *
 * ```
 * App → SDK → Your Backend → IPification Auth Server
 *              ↓
 *           SMS Gateway → User's Phone (OTP)
 *              ↓
 * User enters OTP → App → SDK → Your Backend → Auth Server (callback + token)
 * ```
 *
 * ## Usage
 *
 * ### 1. Configure SMS
 * ```kotlin
 * IPConfiguration.getInstance().apply {
 *     // SMS Backend URLs
 *     SMS_BACKEND_URL_SANDBOX = "https://stage.your-backend-api.com"
 *     SMS_BACKEND_URL_PRODUCTION = "https://your-backend-api.com"
 *     SMS_AUTH_PATH = "/sms/auth"
 *     SMS_TOKEN_PATH = "/sms/token"
 *
 *     ENV = IPEnvironment.SANDBOX
 * }
 * ```
 *
 * ### 2. Start SMS Verification
 * ```kotlin
 * SMSServices.startVerification(
 *     activity = this,
 *     phoneNumber = "+1234567890",
 *     callback = object : SMSCallback {
 *         override fun onAuthInitiated(response: SMSAuthResponse) {
 *             // Save auth_req_id and nonce for token exchange
 *             // Show OTP input dialog to user
 *         }
 *         override fun onSuccess(response: SMSTokenResponse) {
 *             if (response.phoneNumberVerified) {
 *                 // Phone number verified successfully
 *             }
 *         }
 *         override fun onError(error: IPificationError) {
 *             // Handle error
 *         }
 *     }
 * )
 * ```
 *
 * ### 3. Complete Verification with OTP
 * ```kotlin
 * SMSServices.verifyOTP(
 *     activity = this,
 *     otpCode = "123456",
 *     authReqId = savedAuthReqId,
 *     nonce = savedNonce,
 *     callback = object : SMSCallback {
 *         override fun onSuccess(response: SMSTokenResponse) {
 *             // Verification complete
 *         }
 *         override fun onError(error: IPificationError) {
 *             // Handle error
 *         }
 *     }
 * )
 * ```
 */
class SMSServices {

    companion object {
        private const val TAG = "SMSServices"

        // Track if a request is in progress to prevent duplicate SMS network calls.
        private val isRequestInProgress = AtomicBoolean(false)

        // Deliver all public callbacks on the main thread.
        private val mainHandler = Handler(Looper.getMainLooper())

        // HTTP client with the SDK auth timeouts.
        private val httpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(IPConfiguration.getInstance().AUTH_CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(IPConfiguration.getInstance().AUTH_READ_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(IPConfiguration.getInstance().AUTH_READ_TIMEOUT, TimeUnit.MILLISECONDS)
                .build()
        }

        private val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()

        /**
         * Start SMS verification flow.
         *
         * This initiates the CIBA flow with SMS channel. The backend will request
         * the auth server to send an OTP to the provided phone number.
         *
         * @param activity Activity used for callbacks and SDK error reporting.
         * @param phoneNumber Phone number to verify. E.164 format is recommended.
         * @param scope OAuth scope. Defaults to `openid ip:phone_verify`.
         * @param callback Callback to receive auth initiation result or errors.
         */
        @JvmStatic
        @JvmOverloads
        fun startVerification(
            activity: Activity,
            phoneNumber: String,
            scope: String = "openid ip:phone_verify",
            callback: SMSCallback
        ) {
            val config = IPConfiguration.getInstance()
            val loginHint = phoneNumber.filter(Char::isDigit)
            val finalScope = scope.ifBlank { config.SMS_SCOPE_VERIFY_PHONE }

            if (loginHint.isBlank()) {
                val error = createError(
                    SMSErrorCode.INVALID_REQUEST,
                    "SMS verification requires a valid phone number."
                )
                mainHandler.post { callback.onError(error) }
                return
            }

            if (!isConfigured()) {
                val error = createError(
                    SMSErrorCode.CONFIGURATION_ERROR,
                    "SMS not properly configured. Set SMS backend URL, auth path, token path, and CLIENT_ID in IPConfiguration."
                )
                mainHandler.post { callback.onError(error) }
                return
            }

            if (!isRequestInProgress.compareAndSet(false, true)) {
                val error = createError(
                    SMSErrorCode.REQUEST_IN_PROGRESS,
                    SMSErrorMessage.REQUEST_IN_PROGRESS
                )
                mainHandler.post { callback.onError(error) }
                return
            }

            onLog("SMSServices.startVerification: phoneNumberLength=${loginHint.length}")

            val authEndpoint = "${config.getSMSBackendUrl()}${config.SMS_AUTH_PATH}"

            // Build request body
            val requestJson = JSONObject().apply {
                put("client_id", config.CLIENT_ID)
                put("login_hint", loginHint)
                put("scope", finalScope)
                put("channel", "sms")
            }

            onLog("SMSServices: Sending auth request to $authEndpoint")
            if (config.debug) {
                onLog("SMSServices: Auth request bytes: ${requestJson.toString().toByteArray(Charsets.UTF_8).size}")
                onLog("SMSServices: Auth request body:\n$requestJson")
            }

            val requestBody = requestJson.toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url(authEndpoint)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    isRequestInProgress.set(false)
                    onLog("SMSServices: Auth request failed - ${e.message}")
                    val error = createError(
                        SMSErrorCode.NETWORK_ERROR,
                        "Failed to initiate SMS verification: ${e.message}",
                        e
                    )
                    safeReportError(activity, error)
                    mainHandler.post { callback.onError(error) }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val responseBody = it.body?.string()
                        onLog("SMSServices: Auth response - ${it.code}")
                        if (config.debug) {
                            onLog("SMSServices: Auth response bytes: ${responseBody?.toByteArray(Charsets.UTF_8)?.size ?: 0}")
                            onLog("SMSServices: Auth response body:\n${responseBody.orEmpty()}")
                        }

                        if (!it.isSuccessful || responseBody == null) {
                            isRequestInProgress.set(false)
                            val error = parseErrorResponse(it.code, responseBody)
                            safeReportError(activity, error)
                            mainHandler.post { callback.onError(error) }
                            return
                        }

                        try {
                            val authResponse = SMSAuthResponse.fromJson(responseBody)
                            onLog("SMSServices: Auth initiated successfully")
                            isRequestInProgress.set(false)
                            mainHandler.post { callback.onAuthInitiated(authResponse) }
                        } catch (e: Exception) {
                            isRequestInProgress.set(false)
                            onLog("SMSServices: Failed to parse auth response - ${e.message}")
                            val error = createError(
                                SMSErrorCode.PARSE_ERROR,
                                "Failed to parse auth response: ${e.message}",
                                e
                            )
                            safeReportError(activity, error)
                            mainHandler.post { callback.onError(error) }
                        }
                    }
                }
            })
        }

        /**
         * Verify the OTP code entered by the user.
         *
         * This completes the SMS verification flow by:
         * 1. Sending the OTP, auth request ID, and nonce to the configured SMS token endpoint.
         * 2. Returning the backend token verification response to [SMSCallback.onSuccess].
         *
         * @param activity Activity used for callbacks and SDK error reporting.
         * @param otpCode OTP code entered by the user.
         * @param authReqId Auth request ID returned by [startVerification].
         * @param nonce Nonce returned by [startVerification].
         * @param callback Callback to receive verification result or errors.
         */
        @JvmStatic
        fun verifyOTP(
            activity: Activity,
            otpCode: String,
            authReqId: String,
            nonce: String,
            callback: SMSCallback
        ) {
            val code = otpCode.trim()
            val requestId = authReqId.trim()
            val requestNonce = nonce.trim()

            val validationError = when {
                code.isBlank() -> "SMS OTP code is required."
                requestId.isBlank() -> "SMS auth request ID is required."
                requestNonce.isBlank() -> "SMS nonce is required."
                else -> null
            }
            if (validationError != null) {
                val error = createError(SMSErrorCode.INVALID_REQUEST, validationError)
                mainHandler.post { callback.onError(error) }
                return
            }

            if (!isConfigured()) {
                val error = createError(
                    SMSErrorCode.CONFIGURATION_ERROR,
                    "SMS not properly configured."
                )
                mainHandler.post { callback.onError(error) }
                return
            }

            if (!isRequestInProgress.compareAndSet(false, true)) {
                val error = createError(
                    SMSErrorCode.REQUEST_IN_PROGRESS,
                    SMSErrorMessage.REQUEST_IN_PROGRESS
                )
                mainHandler.post { callback.onError(error) }
                return
            }

            onLog("SMSServices.verifyOTP: authReqIdLength=${requestId.length}")

            val config = IPConfiguration.getInstance()
            val tokenEndpoint = "${config.getSMSBackendUrl()}${config.SMS_TOKEN_PATH}"

            // Build request body
            val requestJson = JSONObject().apply {
                put("code", code)
                put("auth_req_id", requestId)
                put("client_id", config.CLIENT_ID)
                put("nonce", requestNonce)
            }

            onLog("SMSServices: Sending token request to $tokenEndpoint")
            if (config.debug) {
                onLog("SMSServices: Token request bytes: ${requestJson.toString().toByteArray(Charsets.UTF_8).size}")
                onLog("SMSServices: Token request body:\n$requestJson")
            }

            val requestBody = requestJson.toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url(tokenEndpoint)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    isRequestInProgress.set(false)
                    onLog("SMSServices: Token request failed - ${e.message}")
                    val error = createError(
                        SMSErrorCode.NETWORK_ERROR,
                        "Failed to verify OTP: ${e.message}",
                        e
                    )
                    safeReportError(activity, error)
                    mainHandler.post { callback.onError(error) }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        isRequestInProgress.set(false)
                        val responseBody = it.body?.string()
                        onLog("SMSServices: Token response - ${it.code}")
                        if (config.debug) {
                            onLog("SMSServices: Token response bytes: ${responseBody?.toByteArray(Charsets.UTF_8)?.size ?: 0}")
                            onLog("SMSServices: Token response body:\n${responseBody.orEmpty()}")
                        }

                        if (!it.isSuccessful || responseBody == null) {
                            val error = parseErrorResponse(it.code, responseBody)
                            safeReportError(activity, error)
                            mainHandler.post { callback.onError(error) }
                            return
                        }

                        try {
                            val tokenResponse = SMSTokenResponse.fromJson(responseBody)
                            onLog("SMSServices: Verification successful - phoneNumberVerified=${tokenResponse.phoneNumberVerified}")
                            mainHandler.post { callback.onSuccess(tokenResponse) }
                        } catch (e: Exception) {
                            onLog("SMSServices: Failed to parse token response - ${e.message}")
                            val error = createError(
                                SMSErrorCode.PARSE_ERROR,
                                "Failed to parse token response: ${e.message}",
                                e
                            )
                            safeReportError(activity, error)
                            mainHandler.post { callback.onError(error) }
                        }
                    }
                }
            })
        }

        /**
         * Reset the request state.
         * Call this if you need to force-reset the in-progress state.
         */
        @JvmStatic
        fun reset() {
            isRequestInProgress.set(false)
        }

        /**
         * Check if SMS is properly configured.
         *
         * @return true if configuration is valid, false otherwise.
         */
        @JvmStatic
        fun isConfigured(): Boolean {
            val config = IPConfiguration.getInstance()
            val backendUrl = config.getSMSBackendUrl()
            return backendUrl.isNotBlank() &&
                config.SMS_AUTH_PATH.isNotBlank() &&
                config.SMS_TOKEN_PATH.isNotBlank() &&
                config.CLIENT_ID.isNotBlank()
        }

        /**
         * Safely submit error report to server.
         * Wrapped in try/catch so reporting failures never crash the SDK.
         */
        private fun safeReportError(activity: Activity, error: IPificationError) {
            try {
                val ex = error.exception
                val sb = StringBuilder()
                sb.append("sdk_error_code=${error.sdkErrorCode}")
                sb.append(" | desc=${error.serverDescription ?: ""}")
                if (ex != null) {
                    sb.append(" | exType=${ex.javaClass.name}")
                    sb.append(" | exMessage=${ex.message ?: ""}")
                    ex.cause?.message?.let { sb.append(" | exCause=$it") }
                    sb.append(" | stackTrace=${ex.stackTraceToString()}")
                }
                SubmitErrorService().sendErrorReport(
                    activity,
                    IPConfiguration.getInstance().SMS_API_STR,
                    sb.toString(),
                    "${error.sdkErrorCode}|${error.serverErrorCode ?: ""}",
                    null,
                    "sms_verification"
                )
            } catch (e: Exception) {
                onLog("safeReportError failed: ${e.message}")
            }
        }

        /**
         * Create an IPificationError with SMS-specific error code.
         */
        private fun createError(
            errorCode: Int,
            message: String,
            exception: Exception? = null,
            httpCode: Int? = null
        ): IPificationError {
            return IPificationError().apply {
                sdkErrorCode = errorCode
                serverDescription = message
                this.exception = exception
                this.httpCode = httpCode
            }
        }

        /**
         * Parse error response from backend.
         */
        private fun parseErrorResponse(httpCode: Int, responseBody: String?): IPificationError {
            val message = try {
                responseBody?.let {
                    val json = JSONObject(it)
                    json.optString("error_description", "")
                        .takeIf(String::isNotBlank)
                        ?: json.optString("message", "")
                            .takeIf(String::isNotBlank)
                        ?: json.optString("error", "")
                            .takeIf(String::isNotBlank)
                        ?: "HTTP $httpCode"
                } ?: "HTTP $httpCode"
            } catch (e: Exception) {
                "HTTP $httpCode"
            }

            val errorCode = when (httpCode) {
                400 -> SMSErrorCode.INVALID_REQUEST
                401 -> SMSErrorCode.UNAUTHORIZED
                404 -> SMSErrorCode.NOT_FOUND
                500, 502, 503, 504 -> SMSErrorCode.SERVER_ERROR
                else -> SMSErrorCode.UNKNOWN_ERROR
            }

            return createError(errorCode, message, httpCode = httpCode)
        }

        private fun onLog(message: String) {
            if (IPConfiguration.getInstance().debug) {
                Log.d(TAG, message)
                IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - $TAG - $message\n"
            }
        }
    }
}

/**
 * SMS Error Codes
 */
object SMSErrorCode {
    const val CONFIGURATION_ERROR = 8001
    const val REQUEST_IN_PROGRESS = 8002
    const val NETWORK_ERROR = 8003
    const val PARSE_ERROR = 8004
    const val INVALID_REQUEST = 8005
    const val UNAUTHORIZED = 8006
    const val NOT_FOUND = 8007
    const val SERVER_ERROR = 8008
    const val UNKNOWN_ERROR = 8009
}

/**
 * SMS Error Messages
 */
object SMSErrorMessage {
    const val CONFIGURATION_ERROR = "SMS configuration error"
    const val REQUEST_IN_PROGRESS = "Request already in progress"
    const val NETWORK_ERROR = "Network error"
    const val PARSE_ERROR = "Failed to parse response"
    const val INVALID_REQUEST = "Invalid request"
    const val UNAUTHORIZED = "Unauthorized"
    const val NOT_FOUND = "Not found"
    const val SERVER_ERROR = "Server error"
    const val UNKNOWN_ERROR = "Unknown error"
}
