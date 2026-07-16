@file:OptIn(androidx.credentials.ExperimentalDigitalCredentialApi::class)

package com.ipification.mobile.sdk.ts43

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.DigitalCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetDigitalCredentialOption
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.NoCredentialException
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.SubmitErrorService
import com.ipification.mobile.sdk.ip.exception.IPificationError
import com.ipification.mobile.sdk.ts43.callback.TS43Callback
import com.ipification.mobile.sdk.ts43.exception.TS43ErrorCode
import com.ipification.mobile.sdk.ts43.exception.TS43ErrorMessage
import com.ipification.mobile.sdk.ts43.request.TS43AuthRequest
import com.ipification.mobile.sdk.ts43.request.TS43TokenRequest
import com.ipification.mobile.sdk.ts43.response.TS43AuthResponse
import com.ipification.mobile.sdk.ts43.response.TS43TokenResponse
import com.ipification.mobile.sdk.ip.utils.DeviceUtils
import com.ipification.mobile.sdk.ip.utils.IPLogs
import com.ipification.mobile.sdk.ip.utils.LogUtils
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * Main entry point for TS43 CIBA (Client-Initiated Backchannel Authentication) flow.
 *
 * TS43 uses the Android Credential Manager digital credential API with the
 * IPification backend `/ts43/auth` and `/ts43/token` endpoints configured in
 * [IPConfiguration]. The SDK performs the backend calls, launches Credential
 * Manager, extracts the VP token, and returns a [TS43TokenResponse].
 *
 * Prerequisites:
 * - Credential Manager dependencies are packaged by the host app.
 * - TS43 backend URL and paths are configured in [IPConfiguration].
 * - Android Go devices are not supported by this flow.
 *
 * Configure TS43:
 * ```kotlin
 * IPConfiguration.getInstance().apply {
 *     TS43_BACKEND_URL_SANDBOX = "https://stage.your-backend-api.com"
 *     TS43_BACKEND_URL_PRODUCTION = "https://your-backend-api.com"
 *     ENV = IPEnvironment.SANDBOX
 * }
 * ```
 *
 * Verify a known phone number:
 * ```kotlin
 * TS43Services.verifyPhoneNumber(
 *     activity = this,
 *     phoneNumber = "84932383421",
 *     callback = object : TS43Callback {
 *         override fun onSuccess(response: TS43TokenResponse) {
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
 * Get the SIM phone number without asking the user to enter one:
 * ```kotlin
 * TS43Services.getPhoneNumber(
 *     activity = this,
 *     callback = object : TS43Callback {
 *         override fun onSuccess(response: TS43TokenResponse) {
 *             val phoneNumber = response.loginHint
 *             // Use the retrieved phone number
 *         }
 *         override fun onError(error: IPificationError) {
 *             // Handle error
 *         }
 *     }
 * )
 * ```
 */
class TS43Services {

    companion object {
        private const val TAG = "TS43Services"

        // Track if a request is in progress to prevent multiple simultaneous requests
        private val isRequestInProgress = AtomicBoolean(false)

        // Main thread handler for callbacks
        private val mainHandler = Handler(Looper.getMainLooper())

        /**
         * Extract VP token from Credential Manager response JSON.
         *
         * The credential response has structure:
         * {"protocol":"...","data":{"vp_token":{"ipification.com":["token_value"]}}}
         *
         * @param credentialJson The JSON string from Credential Manager.
         * @return Extracted VP token string, or null if extraction fails.
         */
        @JvmStatic
        fun extractVpToken(credentialJson: String): String? {
            return try {
                val root = org.json.JSONObject(credentialJson)

                // Primary path: data.vp_token["ipification.com"][0]
                val primaryToken = root
                    .optJSONObject("data")
                    ?.optJSONObject("vp_token")
                    ?.optJSONArray("ipification.com")
                    ?.optString(0)
                    ?.takeIf(String::isNotBlank)

                if (primaryToken != null) {
                    onLog("VP Token extracted via primary path: data.vp_token[\"ipification.com\"][0] (length: ${primaryToken.length})")
                    return primaryToken
                }

                // Fallback path: vp_token["ipification.com"][0]
                val fallbackToken = root
                    .optJSONObject("vp_token")
                    ?.optJSONArray("ipification.com")
                    ?.optString(0)
                    ?.takeIf(String::isNotBlank)

                if (fallbackToken != null) {
                    onLog("VP Token extracted via fallback path: vp_token[\"ipification.com\"][0] (length: ${fallbackToken.length})")
                } else {
                    onLog("Error: Failed to extract VP token from credential JSON (neither path matched)")
                }

                fallbackToken
            } catch (e: Exception) {
                onLog("Error extracting VP token: ${e.message}")
                null
            }
        }

        /**
         * Verify a phone number using TS43 CIBA flow.
         * 
         * This method:
         * 1. Calls your backend /ts43/auth endpoint
         * 2. Launches Android Credential Manager for user approval
         * 3. Exchanges the credential for verification result via /ts43/token
         * 
         * @param activity The activity context (required for Credential Manager).
         * @param phoneNumber Phone number to verify in E.164 format without '+' (e.g., "1234567890").
         * @param customParams Extra parameters sent to the /ts43/auth endpoint.
         * @param tokenCustomParams Extra parameters sent to the /ts43/token endpoint.
         * @param callback Callback to receive the verification result.
         */
        @JvmStatic
        fun verifyPhoneNumber(
            activity: Activity,
            phoneNumber: String,
            customParams: Map<String, String>? = null,
            tokenCustomParams: Map<String, String>? = null,
            callback: TS43Callback
        ) {
            onLog("TS43Services.verifyPhoneNumber: phoneNumberLength=${phoneNumber.length} hasCustomParams=${customParams != null} hasTokenCustomParams=${tokenCustomParams != null}")
            startTS43Flow(activity, phoneNumber, TS43Operation.VERIFY_PHONE_NUMBER, customParams, tokenCustomParams, callback)
        }

        /**
         * Get phone number from SIM using TS43 CIBA flow.
         * 
         * This method retrieves the phone number associated with the active SIM card
         * without requiring the user to input it.
         * 
         * @param activity The activity context (required for Credential Manager).
         * @param customParams Extra parameters sent to the /ts43/auth endpoint.
         * @param tokenCustomParams Extra parameters sent to the /ts43/token endpoint.
         * @param callback Callback to receive the phone number result.
         */
        @JvmStatic
        fun getPhoneNumber(
            activity: Activity,
            customParams: Map<String, String>? = null,
            tokenCustomParams: Map<String, String>? = null,
            callback: TS43Callback
        ) {
            onLog("TS43Services.getPhoneNumber: hasCustomParams=${customParams != null} hasTokenCustomParams=${tokenCustomParams != null}")
            startTS43Flow(activity, IPConfiguration.getInstance().TS43_DEFALT_LOGIN_HINT_SCOPE_GET_PHONE, TS43Operation.GET_PHONE_NUMBER, customParams, tokenCustomParams, callback)
        }

        /**
         * Start TS43 authentication flow with custom request.
         * 
         * @param activity The activity context.
         * @param request Custom TS43AuthRequest with all parameters.
         * @param callback Callback to receive the result.
         */
        @JvmStatic
        fun startAuthentication(
            activity: Activity,
            request: TS43AuthRequest,
            callback: TS43Callback
        ) {
            clearProcessNetworkBinding(activity)

            // Check if Credential Manager dependencies are available
            if (!isCredentialManagerAvailable()) {
                val error = IPificationError().apply {
                    sdkErrorCode = TS43ErrorCode.MISSING_CREDENTIAL_MANAGER
                    serverDescription = TS43ErrorMessage.MISSING_CREDENTIAL_MANAGER
                }
                reportTS43Error(activity, error, null, "credential_manager")
                callback.onError(error)
                return
            }

            if (isRequestInProgress.getAndSet(true)) {
                onLog("TS43 request already in progress, ignoring")
                return
            }

            val config = IPConfiguration.getInstance()
            val service = TS43InternalService(activity)
            val hasCallbackSent = AtomicBoolean(false)

            // Validate configuration
            if (!isConfigured()) {
                isRequestInProgress.set(false)
                if (hasCallbackSent.compareAndSet(false, true)) {
                    val error = service.createError(
                        TS43ErrorCode.MISSING_TS43_ENDPOINT,
                        TS43ErrorMessage.MISSING_ENDPOINT
                    )
                    reportTS43Error(
                        activity,
                        error,
                        request.loginHint ?: "",
                        "${IPConfiguration.getInstance().getTS43BackendUrl()}${IPConfiguration.getInstance().TS43_AUTH_PATH}"
                    )
                    callback.onError(error)
                }
                return
            }

            // Add device info to logs
            if (config.debug) {
                IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - TS43 Flow Started\n"
                IPLogs.getInstance().LOG += DeviceUtils.getInstance(activity).generateHeaderLogs(request.loginHint ?: "")
            }

            // Step 1: Call backend /ts43/auth endpoint (async with OkHttp enqueue)
            onLog("Step 1: Calling TS43 auth endpoint...")
            service.performAuthRequest(
                request = request,
                onSuccess = { authResponse ->
                    onLog("Step 1 Complete: Received auth_req_id and digital_request")

                    // Step 2: Launch Credential Manager on main thread
                    mainHandler.post {
                        launchCredentialManager(
                            activity = activity,
                            authResponse = authResponse,
                            onSuccess = { credentialJson ->
                                onLog("Step 2 Complete: Received credential from Credential Manager")

                                // Step 3: Extract VP token
                                onLog("Step 3: Extracting VP token...")
                                val vpToken = service.extractVpToken(credentialJson)
                                if (vpToken == null) {
                                    isRequestInProgress.set(false)
                                    if (hasCallbackSent.compareAndSet(false, true)) {
                                        val error = service.createError(
                                            TS43ErrorCode.VP_TOKEN_EXTRACTION_FAILED,
                                            TS43ErrorMessage.VP_TOKEN_EXTRACTION_FAILED
                                        )
                                        reportTS43Error(activity, error, request.loginHint ?: "", "credential_manager")
                                        callback.onError(error)
                                    }
                                    return@launchCredentialManager
                                }
                                onLog("Step 3 Complete: VP token extracted")

                                // Step 4: Exchange token (async with OkHttp enqueue)
                                onLog("Step 4: Exchanging token...")
                                val tokenRequest = TS43TokenRequest(
                                    vpToken = vpToken,
                                    authReqId = authResponse.authReqId,
                                    clientId = request.clientId,
                                    customParams = request.tokenCustomParams
                                )
                                service.performTokenExchange(
                                    request = tokenRequest,
                                    onSuccess = { tokenResponse ->
                                        onLog("Step 4 Complete: Token exchange successful")

                                        // Success - callback on main thread
                                        isRequestInProgress.set(false)
                                        if (hasCallbackSent.compareAndSet(false, true)) {
                                            mainHandler.post {
                                                callback.onSuccess(tokenResponse)
                                            }
                                        }
                                    },
                                    onError = { e ->
                                        onLog("Token exchange error: ${e.message}")
                                        isRequestInProgress.set(false)
                                        if (hasCallbackSent.compareAndSet(false, true)) {
                                            val error = service.createError(
                                                TS43ErrorCode.TOKEN_EXCHANGE_FAILED,
                                                e.message ?: TS43ErrorMessage.TOKEN_EXCHANGE_FAILED,
                                                e
                                            )
                                            
                                            reportTS43Error(
                                                activity,
                                                error,
                                                request.loginHint ?: "",
                                                "${IPConfiguration.getInstance().getTS43BackendUrl()}${IPConfiguration.getInstance().TS43_TOKEN_PATH}"
                                            )
                                            
                                            mainHandler.post {
                                                callback.onError(error)
                                            }
                                        }
                                    }
                                )
                            },
                            onError = { error ->
                                isRequestInProgress.set(false)
                                if (hasCallbackSent.compareAndSet(false, true)) {
                                    reportTS43Error(activity, error, request.loginHint ?: "", "credential_manager")
                                    callback.onError(error)
                                }
                            }
                        )
                    }
                },
                onError = { e ->
                    onLog("TS43 auth error: ${e.message}")
                    isRequestInProgress.set(false)
                    if (hasCallbackSent.compareAndSet(false, true)) {
                        val error = service.createError(
                            TS43ErrorCode.AUTH_REQUEST_FAILED,
                            e.message ?: TS43ErrorMessage.AUTH_REQUEST_FAILED,
                            e
                        )
                        
                        reportTS43Error(
                            activity,
                            error,
                            request.loginHint ?: "",
                            "${IPConfiguration.getInstance().getTS43BackendUrl()}${IPConfiguration.getInstance().TS43_AUTH_PATH}"
                        )
                        
                        mainHandler.post {
                            callback.onError(error)
                        }
                    }
                }
            )
        }

        /**
         * Check if the device is an Android Go edition device.
         * Android Go devices are identified by low RAM feature.
         */
        private fun isAndroidGoDevice(activity: Activity): Boolean {
            val packageManager = activity.packageManager
            // Android Go devices have FEATURE_RAM_LOW
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                packageManager.hasSystemFeature(PackageManager.FEATURE_RAM_LOW)
            } else {
                false
            }
        }

        /**
         * Internal method to start TS43 flow.
         */
        private fun startTS43Flow(
            activity: Activity,
            phoneNumber: String?,
            operation: TS43Operation,
            customParams: Map<String, String>? = null,
            tokenCustomParams: Map<String, String>? = null,
            callback: TS43Callback
        ) {
            // Check if device is Android Go - TS43 is not supported on Android Go
            if (isAndroidGoDevice(activity)) {
                onLog("TS43Services: Android Go device detected, TS43 not supported")
                val error = createError(
                    TS43ErrorCode.ANDROID_GO_NOT_SUPPORTED,
                    TS43ErrorMessage.ANDROID_GO_NOT_SUPPORTED
                )
                mainHandler.post {
                    callback.onError(error)
                }
                return
            }

            val service = TS43InternalService(activity)

            val requestBuilder = TS43AuthRequest.Builder()
                .setLoginHint(phoneNumber)
                .setOperation(operation)
                
            customParams?.let { params ->
                for ((key, value) in params) {
                    requestBuilder.addCustomParam(key, value)
                }
            }
                
            tokenCustomParams?.let { params ->
                for ((key, value) in params) {
                    requestBuilder.addTokenCustomParam(key, value)
                }
            }

            try {
                val request = requestBuilder.build()
                startAuthentication(activity, request, callback)
            } catch (e: IllegalArgumentException) {
                // Map specific validation failures to appropriate error codes
                val errorCode = when {
                    e.message?.contains("login_hint", ignoreCase = true) == true -> 
                        TS43ErrorCode.MISSING_PHONE_NUMBER
                    e.message?.contains("CLIENT_ID", ignoreCase = true) == true -> 
                        TS43ErrorCode.MISSING_CLIENT_ID
                    else -> 
                        TS43ErrorCode.CONFIGURATION_ERROR
                }
                val errorMessage = when (errorCode) {
                    TS43ErrorCode.MISSING_PHONE_NUMBER -> TS43ErrorMessage.MISSING_PHONE_NUMBER
                    TS43ErrorCode.MISSING_CLIENT_ID -> TS43ErrorMessage.MISSING_CLIENT_ID
                    else -> e.message ?: TS43ErrorMessage.CONFIGURATION_ERROR
                }
                val error = service.createError(errorCode, errorMessage, e)
                
                reportTS43Error(
                    activity,
                    error,
                    phoneNumber ?: "",
                    "${IPConfiguration.getInstance().getTS43BackendUrl()}${IPConfiguration.getInstance().TS43_AUTH_PATH}"
                )
                
                callback.onError(error)
            }
        }

        /**
         * Launch Android Credential Manager to get digital credential.
         * Must be called from the main thread.
         * 
         * @param activity The activity context.
         * @param authResponse The auth response containing digital_request.
         * @param onSuccess Callback with credential JSON on success.
         * @param onError Callback with error on failure.
         */
        @androidx.credentials.ExperimentalDigitalCredentialApi
        private fun launchCredentialManager(
            activity: Activity,
            authResponse: TS43AuthResponse,
            onSuccess: (String) -> Unit,
            onError: (IPificationError) -> Unit
        ) {
            val credentialManager = CredentialManager.create(activity)
            val requestJson = authResponse.getCredentialManagerRequestJson()
            val service = TS43InternalService(activity)

            onLog("Step 2: Launching Credential Manager...")
            onLog("Credential Manager request bytes: ${requestJson.length}")
            onLog("Credential Manager request body:\n$requestJson")

            val option = GetDigitalCredentialOption(requestJson = requestJson)
            val request = GetCredentialRequest(listOf(option))

            // Use the main executor so app callbacks are delivered on the UI thread.
            credentialManager.getCredentialAsync(
                context = activity,
                request = request,
                cancellationSignal = null,
                executor = ContextCompat.getMainExecutor(activity),
                callback = object : androidx.credentials.CredentialManagerCallback<androidx.credentials.GetCredentialResponse, GetCredentialException> {
                    override fun onResult(result: androidx.credentials.GetCredentialResponse) {
                        when (val credential = result.credential) {
                            is DigitalCredential -> {
                                val credentialJson = credential.credentialJson
                                onLog("Credential Manager response bytes: ${credentialJson.length}")
                                onLog("Credential Manager response body:\n$credentialJson")

                                val credentialError = parseCredentialManagerError(credentialJson)
                                if (credentialError != null) {
                                    onLog("Credential Manager returned an error: $credentialError")
                                    onError(
                                        service.createError(
                                            errorCode = TS43ErrorCode.CREDENTIAL_MANAGER_FAILED,
                                            errorMessage = "${TS43ErrorMessage.CREDENTIAL_MANAGER_FAILED}: $credentialError",
                                            exception = IllegalStateException(credentialError)
                                        )
                                    )
                                    return
                                }

                                onSuccess(credentialJson)
                            }
                            else -> {
                                onLog("Unexpected credential type: ${credential.type}")
                                val error = service.createError(
                                    TS43ErrorCode.CREDENTIAL_TYPE_MISMATCH,
                                    "Unexpected credential type: ${credential.type}"
                                )
                                onError(error)
                            }
                        }
                    }

                    override fun onError(e: GetCredentialException) {
                        val error = when (e) {
                            is GetCredentialCancellationException -> {
                                onLog("Credential Manager: User cancelled")
                                service.createError(
                                    TS43ErrorCode.CREDENTIAL_CANCELLED,
                                    TS43ErrorMessage.CREDENTIAL_CANCELLED,
                                    e
                                )
                            }
                            is GetCredentialInterruptedException -> {
                                onLog("Credential Manager: Interrupted - ${e.message}")
                                service.createError(
                                    TS43ErrorCode.CREDENTIAL_INTERRUPTED,
                                    TS43ErrorMessage.CREDENTIAL_INTERRUPTED,
                                    e
                                )
                            }
                            is NoCredentialException -> {
                                onLog("Credential Manager: No credential available")
                                service.createError(
                                    TS43ErrorCode.NO_CREDENTIAL_AVAILABLE,
                                    TS43ErrorMessage.NO_CREDENTIAL_AVAILABLE,
                                    e
                                )
                            }
                            else -> {
                                onLog("========== CREDENTIAL MANAGER ERROR (UNKNOWN TYPE) ==========")
                                onLog("Exception Type: ${e.javaClass.name}")
                                onLog("Exception Message: ${e.message}")
                                onLog("Exception Cause: ${e.cause?.message}")
                                onLog("Exception Type: ${e.type}")
                                onLog("Exception String: $e")
                                if (IPConfiguration.getInstance().debug) {
                                    onLog("Stack Trace: ${e.stackTraceToString()}")
                                }
                                onLog("===============================================================")
                                service.createError(
                                    TS43ErrorCode.CREDENTIAL_MANAGER_ERROR,
                                    "${TS43ErrorMessage.CREDENTIAL_MANAGER_ERROR}: ${e.message}",
                                    e
                                )
                            }
                        }
                        onError(error)
                    }
                }
            )
        }

        private fun parseCredentialManagerError(credentialJson: String): String? {
            return runCatching {
                val response = JSONObject(credentialJson)
                val error = response.optString("error").takeIf(String::isNotBlank)
                    ?: return@runCatching null
                val description = response.optString("error_description")
                    .takeIf(String::isNotBlank)

                if (description != null) "$error - $description" else error
            }.getOrNull()
        }

        /**
         * Check if TS43 is properly configured.
         * 
         * @return true if configuration is valid, false otherwise.
         */
        @JvmStatic
        fun isConfigured(): Boolean {
            val config = IPConfiguration.getInstance()
            return config.getTS43BackendUrl().isNotBlank() &&
                config.TS43_AUTH_PATH.isNotBlank() &&
                config.TS43_TOKEN_PATH.isNotBlank()
        }

        /**
         * Check if Credential Manager dependencies are available.
         * Users must add these dependencies to use TS43:
         * - androidx.credentials:credentials
         * - androidx.credentials:credentials-play-services-auth
         * 
         * @return true if Credential Manager is available, false otherwise.
         */
        @JvmStatic
        fun isCredentialManagerAvailable(): Boolean {
            return try {
                Class.forName("androidx.credentials.CredentialManager")
                true
            } catch (e: Throwable) {
                false
            }
        }

        /**
         * Reset any in-progress request state.
         * Call this if you need to force-reset the request state.
         */
        @JvmStatic
        fun reset() {
            isRequestInProgress.set(false)
        }

        /**
         * TS43 backend calls use the default app network. A previous IP flow may leave the
         * process bound to cellular after VPN/socket failures, which can poison TS43 DNS.
         */
        private fun clearProcessNetworkBinding(activity: Activity) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return
            }

            runCatching {
                val manager = activity.applicationContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                if (IPConfiguration.getInstance().debug) {
                    onLog("TS43 active network before clear bind: ${describeActiveNetwork(manager)}")
                }
                manager.bindProcessToNetwork(null)
                onLog("Cleared process network binding before TS43 request")
            }.onFailure {
                onLog("Failed to clear process network binding before TS43 request: ${it.message}")
            }
        }

        private fun describeActiveNetwork(manager: ConnectivityManager): String {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                return "legacy=${manager.activeNetworkInfo}"
            }

            val network = manager.activeNetwork ?: return "network=null"
            val capabilities = manager.getNetworkCapabilities(network) ?: return "network=$network capabilities=null"
            val transports = buildList {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
            }.ifEmpty { listOf("unknown") }.joinToString("|")
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            return "network=$network transports=$transports internet=$hasInternet validated=$isValidated"
        }

        /**
         * Safely submit error report to server, matching the IP flow pattern.
         * Wrapped in try/catch so reporting failures never crash the SDK.
         */
        private fun reportTS43Error(
            activity: Activity,
            error: IPificationError,
            phoneNumber: String?,
            requestUrl: String
        ) {
            try {
                val ex = error.exception
                val sb = StringBuilder()
                sb.append("sdk_error_code=${error.sdkErrorCode}")
                sb.append(" | desc=${error.serverDescription ?: ""}")
                if (ex != null) {
                    sb.append(" | exType=${ex.javaClass.name}")
                    sb.append(" | exMessage=${ex.message ?: ""}")
                    ex.cause?.message?.let { sb.append(" | exCause=$it") }
                    if (ex is GetCredentialException) {
                        sb.append(" | credentialExType=${ex.type}")
                    }
                    // Stack trace is appended last because sendErrorReport may truncate it.
                    sb.append(" | stackTrace=${ex.stackTraceToString()}")
                }
                SubmitErrorService().sendErrorReport(
                    activity,
                    IPConfiguration.getInstance().TS43_API_STR,
                    sb.toString(),
                    "${error.sdkErrorCode}|${error.serverErrorCode ?: ""}",
                    phoneNumber,
                    requestUrl
                )
            } catch (e: Exception) {
                onLog("TS43 error report failed: ${e.message}")
            }
        }

        /**
         * Create an IPificationError with TS43-specific error code.
         */
        private fun createError(
            errorCode: Int,
            message: String,
            exception: Exception? = null
        ): IPificationError {
            return IPificationError().apply {
                sdkErrorCode = errorCode
                serverDescription = message
                this.exception = exception
            }
        }

        private fun onLog(message: String) {
            if (IPConfiguration.getInstance().debug) {
                Log.d(TAG, message)
                IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - $TAG - $message\n"
            }
        }
    }
}
