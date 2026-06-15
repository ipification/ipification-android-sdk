package com.ipification.mobile.sdk.ip

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.SubmitErrorService
import com.ipification.mobile.sdk.ip.common.internal.CellularCallback
import com.ipification.mobile.sdk.im.IMLocale
import com.ipification.mobile.sdk.im.IMTheme
import com.ipification.mobile.sdk.ip.callback.IPAuthCallback
import com.ipification.mobile.sdk.ip.callback.IPCoverageCallback
import com.ipification.mobile.sdk.ip.callback.IPificationCallback
import com.ipification.mobile.sdk.ip.callback.MultiAuthCallback
import com.ipification.mobile.sdk.ip.exception.CellularException
import com.ipification.mobile.sdk.ip.exception.IPificationError
import com.ipification.mobile.sdk.ip.network.NetworkManager
import com.ipification.mobile.sdk.ip.request.AuthRequest
import com.ipification.mobile.sdk.ip.response.AuthApiResponse
import com.ipification.mobile.sdk.ip.response.CoverageResponse
import com.ipification.mobile.sdk.ip.response.IPAuthResponse
import com.ipification.mobile.sdk.ip.response.toIPAuthResponse
import com.ipification.mobile.sdk.sms.SMSServices
import com.ipification.mobile.sdk.sms.callback.SMSCallback
import com.ipification.mobile.sdk.ts43.TS43Services
import com.ipification.mobile.sdk.ts43.callback.TS43Callback
import com.ipification.mobile.sdk.ts43.exception.TS43ErrorCode
import com.ipification.mobile.sdk.ts43.exception.TS43ErrorMessage
import com.ipification.mobile.sdk.ts43.response.TS43TokenResponse
import com.ipification.mobile.sdk.ip.utils.IPLogs
import com.ipification.mobile.sdk.ip.utils.LogUtils
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Public facade for IP, TS.43, and SMS authentication operations. */
class IPificationServices {

    companion object Factory {
        private val mainHandler = Handler(Looper.getMainLooper())
        val TAG = "IPificationServices"

        /** Appearance applied when an authentication flow opens the IM verification screen. */
        var theme: IMTheme? = null

        /** Text and locale overrides applied to the IM verification screen. */
        var locale: IMLocale? = null

        /**
         * Starts authentication using the channels configured in [IPConfiguration.AUTH_CHANNELS].
         *
         * @param activity Activity used to run interactive authentication when required.
         * @param authRequest Authorization request parameters.
         * @param callback Receives the first successful result or final error.
         */
        @JvmStatic
        fun startAuthentication(
            activity: Activity,
            authRequest: AuthRequest,
            callback: IPAuthCallback
        ) {
            processAuthChannel(
                activity = activity,
                channels = IPConfiguration.getInstance().AUTH_CHANNELS,
                index = 0,
                lastError = null,
                callback = callback,
                authRequest = authRequest
            )
        }

        /**
         * Starts configured-channel authentication and returns a TS.43-compatible result.
         */
        @JvmStatic
        fun startAuthentication(
            activity: Activity,
            authRequest: AuthRequest,
            callback: TS43Callback
        ) {
            val phoneNumber = authRequest.queryParameters?.get("login_hint")
            val ipCallback = object : IPAuthCallback {
                override fun onSuccess(response: IPAuthResponse) {
                    val ts43Response = response.ts43TokenResponse
                    if (ts43Response != null) {
                        callback.onSuccess(ts43Response)
                    } else {
                        callback.onSuccess(TS43TokenResponse(phoneNumber ?: "", false, response.fullResponse))
                    }
                }

                override fun onError(error: IPificationError) {
                    callback.onError(error)
                }
            }
            processAuthChannel(
                activity = activity,
                channels = IPConfiguration.getInstance().AUTH_CHANNELS,
                index = 0,
                lastError = null,
                callback = ipCallback,
                authRequest = authRequest
            )
        }

        /**
         * Start SMS verification flow.
         * This is a convenience wrapper around SMSServices.startVerification for API consistency.
         *
         * @param activity The activity context
         * @param phoneNumber The phone number to verify
         * @param scope The OAuth scope (default: "openid ip:phone_verify")
         * @param callback SMSCallback to handle auth initiation, success, and errors
         */
        @JvmStatic
        @JvmOverloads
        fun startSMSAuthentication(
            activity: Activity,
            phoneNumber: String,
            scope: String = "openid ip:phone_verify",
            callback: SMSCallback
        ) {
            SMSServices.startVerification(activity, phoneNumber, scope, callback)
        }

        /**
         * Complete SMS verification by verifying the OTP code.
         * This is a convenience wrapper around SMSServices.verifyOTP for API consistency.
         *
         * @param activity The activity context
         * @param otpCode The OTP code entered by the user
         * @param authReqId The auth_req_id from startSMSAuthentication response
         * @param nonce The nonce from startSMSAuthentication response
         * @param callback SMSCallback to handle verification result
         */
        @JvmStatic
        fun verifySMSOTP(
            activity: Activity,
            otpCode: String,
            authReqId: String,
            nonce: String,
            callback: SMSCallback
        ) {
            SMSServices.verifyOTP(activity, otpCode, authReqId, nonce, callback)
        }

        /**
         * Attempts authentication through the configured channels in order.
         *
         * A successful channel completes the request. When a channel fails with a fallback-eligible
         * error, the function advances to the next channel. If every channel is exhausted, the most
         * recent error is returned through [callback].
         *
         * @param activity Activity used by channels that require an interactive flow.
         * @param channels Ordered authentication channels to attempt.
         * @param index Index of the channel currently being processed.
         * @param lastError Most recent fallback-eligible error, when available.
         * @param callback Receives the first successful response or final error.
         * @param authRequest Authentication parameters shared by all channel attempts.
         */
        private fun processAuthChannel(
            activity: Activity,
            channels: List<AuthChannel>,
            index: Int,
            lastError: IPificationError?,
            callback: IPAuthCallback,
            authRequest: AuthRequest
        ) {
            val phoneNumber = authRequest.queryParameters?.get("login_hint")
            val scope = authRequest.scope
            val state = authRequest.state

            onLog("AUTH_CHANNELS: $channels")
            onLog(
                "processAuthChannel index=$index channel=${channels.getOrNull(index)} " +
                    "hasLoginHint=${!phoneNumber.isNullOrBlank()} hasScope=${!scope.isNullOrBlank()} " +
                    "ipTokenUrlSet=${IPConfiguration.getInstance().IP_TOKEN_URL.isNotBlank()}"
            )

            if (index >= channels.size) {
                callback.onError(lastError ?: IPificationError().apply {
                    serverDescription = "No authentication channel available"
                })
                return
            }

            val currentChannel = channels[index]
            when (currentChannel) {
                AuthChannel.TS43 -> {
                    val config = IPConfiguration.getInstance()
                    val ts43BackendConfigured = config.getTS43BackendUrl().isNotBlank()
                    val ts43PathsConfigured = config.TS43_AUTH_PATH.isNotBlank() && config.TS43_TOKEN_PATH.isNotBlank()
                    val ts43RuntimeAvailable = try {
                        Class.forName("androidx.credentials.CredentialManager")
                        true
                    } catch (_: Throwable) {
                        false
                    }

                    onLog("TS43 attempt backendConfigured=$ts43BackendConfigured pathsConfigured=$ts43PathsConfigured runtimeAvailable=$ts43RuntimeAvailable")

                    if (!ts43BackendConfigured || !ts43PathsConfigured) {
                        val error = IPificationError().apply {
                            channel = AuthChannel.TS43.name
                            sdkErrorCode = TS43ErrorCode.MISSING_TS43_ENDPOINT
                            serverDescription = TS43ErrorMessage.MISSING_ENDPOINT
                        }
                        callback.onError(error)
                        return
                    }

                    if (!ts43RuntimeAvailable) {
                        val error = IPificationError().apply {
                            channel = AuthChannel.TS43.name
                            sdkErrorCode = TS43ErrorCode.MISSING_CREDENTIAL_MANAGER
                            serverDescription = TS43ErrorMessage.MISSING_CREDENTIAL_MANAGER
                        }
                        onLog("TS43 CredentialManager missing, trying next channel")
                        processAuthChannel(activity, channels, index + 1, error, callback, authRequest)
                        return
                    }

                    val ts43Callback = object : TS43Callback {
                        override fun onSuccess(response: TS43TokenResponse) {
                            val ipAuthResponse = IPAuthResponse(
                                code = "",
                                state = state,
                                fullResponse = response.rawResponse,
                                ts43TokenResponse = response
                            )
                            callback.onSuccess(ipAuthResponse)
                        }

                        override fun onError(error: IPificationError) {
                            error.channel = AuthChannel.TS43.name
                            val shouldFallback = error.sdkErrorCode in listOf(
                                TS43ErrorCode.ANDROID_GO_NOT_SUPPORTED,
                                TS43ErrorCode.AUTH_REQUEST_FAILED,
                                TS43ErrorCode.CREDENTIAL_MANAGER_ERROR,
                                TS43ErrorCode.CREDENTIAL_MANAGER_FAILED,
                                TS43ErrorCode.CREDENTIAL_CANCELLED,
                                TS43ErrorCode.CREDENTIAL_INTERRUPTED,
                                TS43ErrorCode.NO_CREDENTIAL_AVAILABLE,
                                TS43ErrorCode.VP_TOKEN_EXTRACTION_FAILED,
                                TS43ErrorCode.CREDENTIAL_RESPONSE_INVALID,
                                TS43ErrorCode.CREDENTIAL_TYPE_MISMATCH,
                                TS43ErrorCode.TOKEN_EXCHANGE_FAILED,
                                TS43ErrorCode.UNKNOWN_ERROR
                            )
                            if (shouldFallback) {
                                onLog("TS43 failed: ${error.getErrorMessage()}, trying next channel")
                                processAuthChannel(activity, channels, index + 1, error, callback, authRequest)
                            } else {
                                onLog("TS43 failed (no fallback): ${error.getErrorMessage()}")
                                callback.onError(error)
                            }
                        }
                    }

                    val customParams = authRequest.queryParameters?.filterKeys { key ->
                        key != "login_hint" && key != "client_id" && key != "scope" &&
                        key != "redirect_uri" && key != "response_type" && key != "state"
                    }

                    val tokenCustomParams = authRequest.ts43TokenCustomParams

                    if (phoneNumber != null && phoneNumber != IPConfiguration.getInstance().TS43_DEFALT_LOGIN_HINT_SCOPE_GET_PHONE) {
                        onLog("TS43 start operation=VERIFY_PHONE_NUMBER customParams=${customParams?.keys} tokenCustomParams=${tokenCustomParams?.keys}")
                        TS43Services.verifyPhoneNumber(activity, phoneNumber, customParams, tokenCustomParams, ts43Callback)
                    } else {
                        onLog("TS43 start operation=GET_PHONE_NUMBER customParams=${customParams?.keys} tokenCustomParams=${tokenCustomParams?.keys}")
                        TS43Services.getPhoneNumber(activity, customParams, tokenCustomParams, ts43Callback)
                    }
                }
                AuthChannel.IP -> {
                    onLog(
                        "IP attempt shouldExchangeToken=" +
                            IPConfiguration.getInstance().IP_TOKEN_URL.isNotBlank()
                    )

                    performIPAuthentication(activity, authRequest, object : IPAuthCallback {
                        override fun onSuccess(response: IPAuthResponse) {
                            // Check if IP_TOKEN_URL is configured to determine if we should exchange code for token
                            val shouldExchangeToken =
                                IPConfiguration.getInstance().IP_TOKEN_URL.isNotBlank()

                            onLog("IP auth success codeLength=${response.code.length} shouldExchangeToken=$shouldExchangeToken")

                            if (shouldExchangeToken) {
                                onLog("IP token exchange start")
                                performIPTokenExchange(
                                    code = response.code,
                                    onSuccess = { tokenResponse ->
                                        onLog("IP token exchange success rawLength=${tokenResponse.rawResponse.length}")
                                        response.fullResponse = tokenResponse.rawResponse
                                        callback.onSuccess(response)
                                    },
                                    onError = { e ->
                                        val error = IPificationError().apply {
                                            channel = AuthChannel.IP.name
                                            sdkErrorCode = TS43ErrorCode.TOKEN_EXCHANGE_FAILED
                                            serverDescription =
                                                "IP token exchange failed: ${e.message}"
                                            exception = e
                                        }
                                        try {
                                            SubmitErrorService().sendErrorReport(
                                                activity,
                                                IPConfiguration.getInstance().TOKEN_API_STR,
                                                error.getErrorMessage(),
                                                "${error.sdkErrorCode}|${error.serverErrorCode ?: ""}",
                                                phoneNumber,
                                                IPConfiguration.getInstance().IP_TOKEN_URL
                                            )
                                        } catch (reportError: Exception) {
                                            onLog("IP token exchange error report failed: ${reportError.message}")
                                        }
                                        onLog("IP token exchange failed: ${error.getErrorMessage()}, trying next channel")
                                        processAuthChannel(
                                            activity,
                                            channels,
                                            index + 1,
                                            error,
                                            callback,
                                            authRequest
                                        )
                                    }
                                )
                            } else {
                                callback.onSuccess(response)
                            }
                        }

                        override fun onError(error: IPificationError) {
                            error.channel = AuthChannel.IP.name
                            onLog("IP auth failed: ${error.getErrorMessage()}")
                            processAuthChannel(
                                activity,
                                channels,
                                index + 1,
                                error,
                                callback,
                                authRequest
                            )
                        }
                    })
                }
                AuthChannel.SMS -> {
                    onLog("SMS channel - startVerification")

                    if (callback !is MultiAuthCallback) {
                        val error = IPificationError().apply {
                            channel = AuthChannel.SMS.name
                            sdkErrorCode = 8001
                            serverDescription = "SMS channel requires MultiAuthCallback to return OTP initiation. " +
                                "Use MultiAuthCallback.onOTPRequired(), then call verifySMSOTP() to complete."
                        }
                        onLog("SMS in multi-channel requires MultiAuthCallback: ${error.serverDescription}")
                        processAuthChannel(activity, channels, index + 1, error, callback, authRequest)
                        return
                    }

                    if (phoneNumber.isNullOrBlank() || phoneNumber == "anonymous") {
                        val error = IPificationError().apply {
                            channel = AuthChannel.SMS.name
                            sdkErrorCode = 8002
                            serverDescription = "SMS channel requires a valid login_hint phone number."
                        }
                        onLog("SMS missing phone number: ${error.serverDescription}")
                        processAuthChannel(activity, channels, index + 1, error, callback, authRequest)
                        return
                    }

                    SMSServices.startVerification(
                        activity = activity,
                        phoneNumber = phoneNumber,
                        scope = scope ?: IPConfiguration.getInstance().SMS_SCOPE_VERIFY_PHONE,
                        callback = object : SMSCallback {
                            override fun onAuthInitiated(response: com.ipification.mobile.sdk.sms.response.SMSAuthResponse) {
                                onLog("SMS auth initiated, OTP required")
                                callback.onOTPRequired(response)
                            }

                            override fun onSuccess(response: com.ipification.mobile.sdk.sms.response.SMSTokenResponse) {
                                onLog("SMS verification success")
                                callback.onSuccess(
                                    IPAuthResponse(
                                        code = "",
                                        state = state,
                                        fullResponse = response.rawResponse ?: ""
                                    )
                                )
                            }

                            override fun onError(error: IPificationError) {
                                error.channel = AuthChannel.SMS.name
                                onLog("SMS auth failed: ${error.getErrorMessage()}")
                                processAuthChannel(activity, channels, index + 1, error, callback, authRequest)
                            }
                        }
                    )
                }
            }
        }

        /**
         * Perform IP authorization code exchange for token.
         * Used when TS43Callback entrypoint falls back to IP flow.
         *
         * @param code The auth code from IP flow.
         * @param onSuccess Callback with TS43TokenResponse on success.
         * @param onError Callback with Exception on failure.
         */
        private fun performIPTokenExchange(
            code: String,
            onSuccess: (TS43TokenResponse) -> Unit,
            onError: (Exception) -> Unit
        ) {
            val url = IPConfiguration.getInstance().IP_TOKEN_URL

            if (url.isBlank()) {
                onError(IllegalStateException("IP_TOKEN_URL is not configured"))
                return
            }

            val redirectUri = IPConfiguration.getInstance().REDIRECT_URI

            if (redirectUri == null) {
                onError(IllegalStateException("REDIRECT_URI is required for IP token exchange"))
                return
            }

            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", IPConfiguration.getInstance().CLIENT_ID)
                .add("redirect_uri", redirectUri.toString())
                .add("code", code)
                .build()

            onLog("IP token exchange request started")

            val client = OkHttpClient.Builder()
                .connectTimeout(IPConfiguration.getInstance().AUTH_CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(IPConfiguration.getInstance().AUTH_READ_TIMEOUT, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(IPConfiguration.getInstance().retryOnConnectionFailure)
                .build()

            val httpRequest = Request.Builder()
                .url(url)
                .post(formBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build()

            client.newCall(httpRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onLog("IP token exchange request failed: ${e.message}")
                    onError(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val responseBody = it.body?.string() ?: ""
                        val responseCode = it.code

                        onLog("IP token exchange completed with HTTP $responseCode")

                        if (!it.isSuccessful) {
                            onError(IOException("IP token exchange failed with HTTP $responseCode"))
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

        /** Tracks the active coverage request when single-request mode is enabled. */
        private val isCoverageRequestInProgress = AtomicBoolean(false)

        /** Tracks the active IP authentication request when single-request mode is enabled. */
        private val isAuthRequestInProgress = AtomicBoolean(false)

        private fun performIPAuthentication(
            activity: Activity,
            authRequest: AuthRequest,
            callback: IPAuthCallback
        ) {
            if (!acquireRequest(isAuthRequestInProgress, "Authentication")) return

            val hasDeliveredResult = AtomicBoolean(false)
            val cb = object : CellularCallback<AuthApiResponse> {
                override fun onSuccess(response: AuthApiResponse) {
                    finishOnce(hasDeliveredResult, isAuthRequestInProgress) {
                        val ipAuthResponse = response.toIPAuthResponse()
                        if (ipAuthResponse != null) {
                            callback.onSuccess(ipAuthResponse)
                        } else {
                            callback.onError(IPificationError().apply {
                                serverDescription = response.getErrorDescription()
                                serverErrorCode = response.getErrorCode()
                            })
                        }
                    }
                }

                override fun onError(error: CellularException) {
                    finishOnce(hasDeliveredResult, isAuthRequestInProgress) {
                        callback.onError(error.toIPificationError())
                        reportAuthError(activity, authRequest, error)
                    }
                }

                override fun onIMCancel() {
                    finishOnce(hasDeliveredResult, isAuthRequestInProgress)
                }
            }

            val authService = InternalService<AuthApiResponse>(activity)
            authService.imTheme = theme
            authService.imLocale = locale
            authService.performAuthentication(activity, authRequest, cb)
        }

        /** Checks whether the current cellular network supports IP authentication. */
        @JvmStatic
        fun startCheckCoverage(
            context: Context,
            callback: IPCoverageCallback
        ) {
            checkCoverage(phoneNumber = null, context = context, callback = callback)
        }

        /** Checks IP authentication coverage for [phoneNumber]. */
        @JvmStatic
        fun startCheckCoverage(
            phoneNumber: String,
            context: Context,
            callback: IPCoverageCallback
        ) {
            checkCoverage(phoneNumber = phoneNumber, context = context, callback = callback)
        }

        private fun checkCoverage(
            phoneNumber: String?,
            context: Context,
            callback: IPCoverageCallback
        ) {
            onLog("Coverage request started")
            if (!acquireRequest(isCoverageRequestInProgress, "Coverage")) return

            val hasDeliveredResult = AtomicBoolean(false)
            val timeoutRunnable = Runnable {
                finishOnce(hasDeliveredResult, isCoverageRequestInProgress) {
                    onLog("Coverage request timed out")
                    callback.onError(IPificationError().apply {
                        serverErrorCode = "TIMEOUT"
                        serverDescription = "Coverage request timed out"
                    })
                    SubmitErrorService().sendErrorReport(
                        context = context,
                        apiType = IPConfiguration.getInstance().COVERAGE_API_STR,
                        errorDescription = "Coverage request timed out",
                        errorCode = "TIMEOUT",
                        phoneNumber = phoneNumber
                    )
                }
            }

            mainHandler.postDelayed(timeoutRunnable, COVERAGE_TIMEOUT_MILLIS)

            val serviceCallback = object : CellularCallback<CoverageResponse> {
                override fun onSuccess(response: CoverageResponse) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    finishOnce(hasDeliveredResult, isCoverageRequestInProgress) {
                        onLog("Coverage request succeeded")
                        callback.onSuccess(response)
                    }
                }

                override fun onError(error: CellularException) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    finishOnce(hasDeliveredResult, isCoverageRequestInProgress) {
                        onLog("Coverage request failed")
                        callback.onError(error.toIPificationError())
                        SubmitErrorService().sendErrorReport(
                            context = context,
                            apiType = IPConfiguration.getInstance().COVERAGE_API_STR,
                            errorDescription = error.getErrorMessage(),
                            errorCode = "${error.sdkErrorCode}|${error.errorCode.orEmpty()}",
                            phoneNumber = phoneNumber
                        )
                    }
                }

                override fun onIMCancel() {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    finishOnce(hasDeliveredResult, isCoverageRequestInProgress)
                }
            }

            val coverageService = InternalService<CoverageResponse>(context)
            if (phoneNumber != null) {
                coverageService.checkCoverage(phoneNumber, serviceCallback)
            } else {
                coverageService.checkCoverage(serviceCallback)
            }
        }

        /**
         * Starts legacy IM-capable IP authentication.
         *
         * Use the [IPAuthCallback] overload for new integrations.
         */
        @Deprecated(
            message = "Use startAuthentication with IPAuthCallback.",
            replaceWith = ReplaceWith(
                expression = "startAuthentication(activity, authRequest, callback as IPAuthCallback)",
                imports = ["com.ipification.mobile.sdk.ip.callback.IPAuthCallback"]
            ),
            DeprecationLevel.WARNING
        )
        @JvmStatic
        fun startAuthentication(
            activity: Activity,
            authRequest: AuthRequest,
            callback: IPificationCallback
        ) {
            performIPAuthentication(activity, authRequest, callback)
        }

        /** Atomically reserves an operation when single-request mode is enabled. */
        private fun acquireRequest(requestState: AtomicBoolean, operation: String): Boolean {
            if (!IPConfiguration.getInstance().enableSingleRequest) {
                requestState.set(true)
                return true
            }

            val acquired = requestState.compareAndSet(false, true)
            if (!acquired) {
                onLog("$operation request is already in progress")
            }
            return acquired
        }

        /** Delivers the first terminal event and releases its in-progress state. */
        private inline fun finishOnce(
            hasDeliveredResult: AtomicBoolean,
            requestState: AtomicBoolean,
            action: () -> Unit = {}
        ) {
            if (!hasDeliveredResult.compareAndSet(false, true)) return

            requestState.set(false)
            action()
        }

        /** Submits diagnostics for a failed IP authentication request. */
        private fun reportAuthError(
            activity: Activity,
            authRequest: AuthRequest,
            error: CellularException
        ) {
            SubmitErrorService().sendErrorReport(
                context = activity,
                apiType = IPConfiguration.getInstance().AUTH_API_STR,
                errorDescription = error.getErrorMessage(),
                errorCode = "${error.sdkErrorCode}|${error.errorCode.orEmpty()}",
                phoneNumber = authRequest.queryParameters?.get("login_hint")
            )
        }

        /** Manually releases the registered cellular network when automatic release is disabled. */
        @JvmStatic
        fun unregisterNetwork(context: Context): Boolean {
            if (!IPConfiguration.getInstance().autoUnregisterNetwork) {
                return NetworkManager.getInstance(context).unregister()
            }
            return false
        }

        /** Generates and stores a new OAuth state value. */
        @JvmStatic
        fun generateState(): String {
            return IPConfiguration.getInstance().generateState()
        }

        /** Writes an SDK diagnostic message and retains it when debug logging is enabled. */
        fun onLog(message: String) {
            Log.d(TAG, message)
            if (IPConfiguration.getInstance().debug) {
                IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - $TAG - $message.\n"
            }
        }

        private const val COVERAGE_TIMEOUT_MILLIS = 25_000L
    }
}
