package com.ipification.demoapp.model

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipification.demoapp.BuildConfig
import com.ipification.demoapp.manager.ConfigManager
import com.ipification.demoapp.manager.CustomInterceptor
import com.ipification.demoapp.util.Util
import com.ipification.mobile.sdk.ip.AuthChannel
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.IPificationServices
import com.ipification.mobile.sdk.ip.callback.IPAuthCallback
import com.ipification.mobile.sdk.ip.callback.MultiAuthCallback
import com.ipification.mobile.sdk.ip.exception.IPificationError
import com.ipification.mobile.sdk.ip.request.AuthRequest
import com.ipification.mobile.sdk.ip.response.IPAuthResponse
import com.ipification.mobile.sdk.sms.response.SMSAuthResponse
import com.ipification.mobile.sdk.ip.utils.IPLogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class ProcessNavigation {
    data class ToResult(val response: String?, val error: String?, val isTS43: Boolean) : ProcessNavigation()
    data class ToSmsOtp(val phoneNumber: String, val authReqId: String, val nonce: String, val clientId: String, val serverId: String) : ProcessNavigation()
    data object Idle : ProcessNavigation()
}
// Represents the state of the ProcessScreen
data class ProcessState(
    val isLoading: Boolean = true,
    val message: String = "Processing requests. please wait...",
    val isError: Boolean = false,
    val navigation: ProcessNavigation = ProcessNavigation.Idle,
    val isTS43Flow: Boolean = false,
    val authError: String? = null,
    val tokenExchangeError: String? = null,
    val ts43Error: String? = null
)

class ProcessViewModel : ViewModel() {
    private val TAG = "ProcessViewModel"
    private val _state = MutableStateFlow(ProcessState())
    val state = _state.asStateFlow()
    
    /**
     * Starts authentication from the selected user_flow in the stage config.
     */
    fun startAuthenticationWithUserFlow(activity: Activity, userFlow: String, phoneNumber: String?) {
        IPLogs.getInstance().LOG = ""
        
        val isMultiChannel = userFlow == "pvn_ip_multi_channel"
        val client = if (isMultiChannel) {
            ConfigManager.getClientByUserFlow("pvn_ip")
        } else {
            ConfigManager.getClientByUserFlow(userFlow)
        }
        if (client == null) {
            handleError("Client configuration not found for user_flow: $userFlow")
            return
        }
        
        configureDynamicIPification(client)
        
        // Check if this is an IM/backchannel flow
        val isIMFlow = userFlow.contains("_im")
        
        // Check if this is a TS43 flow (SIM-based flows use _sim suffix)
        val isTS43 = userFlow.contains("ts43") || userFlow.endsWith("_sim")
        
        // Check if this is an SMS flow
        val isSms = userFlow == "pvn_sms"
        
        _state.update { it.copy(isTS43Flow = isTS43) }
        
        val availableClients = ConfigManager.getAllClients()
        val hasPnvTs43 = availableClients.any {
            it.userFlow == "pvn_sim" || it.userFlow.contains("ts43") || it.userFlow.endsWith("_sim")
        }
        val hasPnvSms = availableClients.any { it.userFlow == "pvn_sms" }
        val multiChannelAuthChannels = buildList {
            if (hasPnvTs43) {
                add(AuthChannel.TS43)
            }
            add(AuthChannel.IP)
            if (hasPnvSms) {
                add(AuthChannel.SMS)
            }
        }

        // Set AUTH_CHANNELS based on the active flow
        IPConfiguration.getInstance().AUTH_CHANNELS = when {
            isMultiChannel -> multiChannelAuthChannels
            isTS43 -> listOf(AuthChannel.TS43)
            isSms  -> listOf(AuthChannel.SMS)
            else   -> listOf(AuthChannel.IP)
        }

        if (isMultiChannel) {
            if (phoneNumber != null) {
                callMultiChannelAuth(activity, phoneNumber, client.scope, client.clientId)
            } else {
                handleError("Phone number is required for multi-channel authentication")
            }
        } else if (isSms) {
            // SMS OTP flow - uses CIBA with SMS channel
            if (phoneNumber != null) {
                callSmsAuth(activity, phoneNumber, client)
            } else {
                handleError("Phone number is required for SMS verification")
            }
        } else if (isIMFlow) {
            // IM/Backchannel flow - uses IMServices
            callIMAuth(activity, client.scope)
        } else if (isTS43) {
            if (phoneNumber != null) {
                callTS43Auth(activity, phoneNumber, client.scope)
            } else {
                handleError("Phone number is required for TS43 authentication")
            }
        } else {
            doAuth(activity, client.scope, phoneNumber ?: "")
        }
    }

    /**
     * Configure the SDK with the client selected from the stage config response.
     */
    private fun configureDynamicIPification(client: com.ipification.demoapp.model.config.ClientConfig) {
        val config = IPConfiguration.getInstance()
        config.debug = true
        config.CLIENT_ID = client.clientId
        
        // Use the selected server ID from the toggle
        val serverId = ConfigManager.selectedServerId ?: "stage"
        
        // Set BASE_URL from the selected auth server
        val authServerUrl = ConfigManager.getSelectedServerUrl()
        if (authServerUrl != null) {
            config.BASE_URL = authServerUrl.replace("/auth", "")
        } else {
            config.BASE_URL = null
        }
        
        // Append server ID to redirect URI
        val redirectUriWithServerId = "${client.redirectUri}/$serverId"
        config.REDIRECT_URI = Uri.parse(redirectUriWithServerId)

        config.REALM = ConfigManager.getRealm()

        // TS43 backend configuration — SDK handles all HTTP + Credential Manager internally
        val backendUrl = ConfigManager.getBackendUrl()
        config.TS43_BACKEND_URL_SANDBOX = backendUrl
        config.TS43_BACKEND_URL_PRODUCTION = backendUrl
        config.TS43_AUTH_PATH = "/ts43/auth"
        config.TS43_TOKEN_PATH = "/ts43/token"
        config.TS43_DEFAULT_CARRIER_HINT = "51010"
        config.AUTH_READ_TIMEOUT = 10000
        config.AUTH_CONNECT_TIMEOUT = 10000

        // SMS backend configuration — SDK handles all HTTP internally
        config.SMS_BACKEND_URL_SANDBOX = backendUrl
        config.SMS_BACKEND_URL_PRODUCTION = backendUrl
        config.SMS_AUTH_PATH = "/sms/auth"
        config.SMS_TOKEN_PATH = "/sms/token"

        Log.d(TAG, "Configured IPification with client_id: ${client.clientId}, redirect_uri: $redirectUriWithServerId, scope: ${client.scope}, server_id: $serverId, BASE_URL: ${config.BASE_URL}")
    }

    private fun doAuth(activity: Activity, scope: String, phoneNumber: String?) {
        val authRequestBuilder = AuthRequest.Builder()
        authRequestBuilder.setScope(scope)
        if (phoneNumber != null && scope.contains("ip:phone_verify")) {
            authRequestBuilder.addQueryParam("login_hint", phoneNumber)
        }

        IPificationServices.startAuthentication(activity, authRequestBuilder.build(), object : IPAuthCallback {
            override fun onSuccess(response: IPAuthResponse) {
                exchangeToken(response.code)
            }

            override fun onError(error: IPificationError) {
                _state.update { it.copy(authError = error.getErrorMessage()) }
                handleError("Authentication Error: ${error.getErrorMessage()}")
            }
        })
    }

    private fun exchangeToken(code: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, message = "Exchanging token...") }
            val startTime = System.currentTimeMillis()
            try {
                val response = performTokenExchange(code)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                IPLogs.getInstance().LOG += "[ProcessViewModel] ${Util.getCurrentDate()} - TOKEN_EXCHANGE - COMPLETED in ${elapsed}s.\n"
                _state.update { it.copy(navigation = ProcessNavigation.ToResult(response, null, state.value.isTS43Flow)) }

            } catch (e: IOException) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                IPLogs.getInstance().LOG += "[ProcessViewModel] ${Util.getCurrentDate()} - TOKEN_EXCHANGE - FAILED in ${elapsed}s - ${e.message}.\n"
                _state.update { it.copy(tokenExchangeError = e.message) }
                handleError("Token Exchange Failed (${elapsed}s): ${e.message}")
            }
        }
    }


    private fun callTS43Auth(activity: Activity, loginHint: String, scope: String) {
        _state.update { it.copy(isLoading = true, message = "Calling TS43 Auth...") }

        val serverId = ConfigManager.selectedServerId ?: "stage"
        val authRequestBuilder = AuthRequest.Builder()
        if (loginHint.isNotEmpty() && loginHint != "anonymous") {
            authRequestBuilder.addQueryParam("login_hint", loginHint)
        }
        authRequestBuilder.addQueryParam("server_id", serverId)
        authRequestBuilder.addTS43TokenCustomParam("server_id", serverId)
        authRequestBuilder.setScope(scope)
        val authRequest = authRequestBuilder.build()

        IPificationServices.startAuthentication(activity, authRequest, object : IPAuthCallback {
            override fun onSuccess(response: IPAuthResponse) {
                _state.update { it.copy(isLoading = false) }
                if (response.ts43TokenResponse != null) {
                    Log.d(TAG, "TS43 SUCCESS: phoneNumberVerified=${response.ts43TokenResponse!!.phoneNumberVerified}")
                    IPLogs.getInstance().LOG += "[ProcessViewModel] ${Util.getCurrentDate()} - TS43_FLOW - SUCCESS\n"
                    _state.update { it.copy(navigation = ProcessNavigation.ToResult(response.fullResponse, null, true)) }
                } else if (response.code.isNotEmpty()) {
                    Log.d(TAG, "IP Fallback SUCCESS: code=${response.code}")
                    IPLogs.getInstance().LOG += "[ProcessViewModel] ${Util.getCurrentDate()} - IP_FALLBACK - Exchanging token\n"
                    exchangeToken(response.code)
                } else {
                    _state.update { it.copy(navigation = ProcessNavigation.ToResult(response.fullResponse, null, false)) }
                }
            }

            override fun onError(error: IPificationError) {
                IPLogs.getInstance().LOG += "[ProcessViewModel] ${Util.getCurrentDate()} - TS43_FLOW - ERROR: ${error.getErrorMessage()}\n"
                _state.update {
                    it.copy(
                        isLoading = false,
                        ts43Error = error.getErrorMessage(),
                        navigation = ProcessNavigation.ToResult(null, error.getErrorMessage(), true)
                    )
                }
            }
        })
    }

    private fun callMultiChannelAuth(activity: Activity, loginHint: String, scope: String, clientId: String) {
        _state.update { it.copy(isLoading = true, message = "Starting multi-channel authentication...") }

        val serverId = ConfigManager.selectedServerId ?: "stage"
        val authRequestBuilder = AuthRequest.Builder()
        authRequestBuilder.addQueryParam("login_hint", loginHint)
        authRequestBuilder.addQueryParam("server_id", serverId)
        authRequestBuilder.addTS43TokenCustomParam("server_id", serverId)
        authRequestBuilder.setScope(scope)
        val authRequest = authRequestBuilder.build()

        IPificationServices.startAuthentication(activity, authRequest, object : MultiAuthCallback {
            override fun onSuccess(response: IPAuthResponse) {
                _state.update { it.copy(isLoading = false) }
                when {
                    response.ts43TokenResponse != null -> {
                        IPLogs.getInstance().LOG += "[ProcessViewModel] ${Util.getCurrentDate()} - MULTI_CHANNEL_TS43 - SUCCESS\n"
                        _state.update { it.copy(navigation = ProcessNavigation.ToResult(response.fullResponse, null, true)) }
                    }
                    response.code.isNotEmpty() -> {
                        IPLogs.getInstance().LOG += "[ProcessViewModel] ${Util.getCurrentDate()} - MULTI_CHANNEL_IP - Exchanging token\n"
                        exchangeToken(response.code)
                    }
                    else -> {
                        _state.update { it.copy(navigation = ProcessNavigation.ToResult(response.fullResponse, null, false)) }
                    }
                }
            }

            override fun onError(error: IPificationError) {
                IPLogs.getInstance().LOG += "[ProcessViewModel] ${Util.getCurrentDate()} - MULTI_CHANNEL - ERROR: ${error.getErrorMessage()}\n"
                _state.update { it.copy(isLoading = false, authError = error.getErrorMessage()) }
                handleError("Multi-channel authentication failed: ${error.getErrorMessage()}")
            }

            override fun onOTPRequired(response: SMSAuthResponse) {
                val responseServerId = response.authServer?.id ?: serverId
                Log.d(TAG, "Multi-channel OTP required: auth_req_id=${response.authReqId}, nonce=${response.nonce}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        navigation = ProcessNavigation.ToSmsOtp(
                            phoneNumber = loginHint,
                            authReqId = response.authReqId,
                            nonce = response.nonce,
                            clientId = clientId,
                            serverId = responseServerId
                        )
                    )
                }
            }
        })
    }

    /**
     * Starts SMS OTP flow via SDK SMSServices.
     * SDK calls backend POST /sms/auth to trigger CIBA with SMS channel.
     * On auth initiated, navigates to the SMS OTP screen.
     */
    private fun callSmsAuth(activity: Activity, phoneNumber: String, client: com.ipification.demoapp.model.config.ClientConfig) {
        _state.update { it.copy(isLoading = true, message = "Sending SMS verification code...") }

        com.ipification.mobile.sdk.sms.SMSServices.startVerification(
            activity = activity,
            phoneNumber = phoneNumber,
            scope = client.scope,
            callback = object : com.ipification.mobile.sdk.sms.callback.SMSCallback {
                override fun onAuthInitiated(response: com.ipification.mobile.sdk.sms.response.SMSAuthResponse) {
                    val serverId = response.authServer?.id ?: ConfigManager.selectedServerId ?: "stage"
                    Log.d(TAG, "SMS auth initiated: auth_req_id=${response.authReqId}, nonce=${response.nonce}")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            navigation = ProcessNavigation.ToSmsOtp(
                                phoneNumber = phoneNumber,
                                authReqId = response.authReqId,
                                nonce = response.nonce,
                                clientId = client.clientId,
                                serverId = serverId
                            )
                        )
                    }
                }

                override fun onSuccess(response: com.ipification.mobile.sdk.sms.response.SMSTokenResponse) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            navigation = ProcessNavigation.ToResult(response.rawResponse, null, false)
                        )
                    }
                }

                override fun onError(error: IPificationError) {
                    Log.e(TAG, "SMS auth error: ${error.getErrorMessage()}")
                    handleError("SMS verification failed: ${error.getErrorMessage()}")
                }
            }
        )
    }

    /**
     * Submits the user-entered OTP code via SDK SMSServices.verifyOTP.
     * SDK calls backend POST /sms/token. On success, navigates to the result screen.
     */
    fun submitSmsOtp(activity: Activity, otpCode: String, authReqId: String, clientId: String, nonce: String, serverId: String) {
        _state.update { it.copy(isLoading = true, message = "Verifying OTP code...") }

        com.ipification.mobile.sdk.sms.SMSServices.verifyOTP(
            activity = activity,
            otpCode = otpCode,
            authReqId = authReqId,
            nonce = nonce,
            callback = object : com.ipification.mobile.sdk.sms.callback.SMSCallback {
                override fun onAuthInitiated(response: com.ipification.mobile.sdk.sms.response.SMSAuthResponse) {}

                override fun onSuccess(response: com.ipification.mobile.sdk.sms.response.SMSTokenResponse) {
                    Log.d(TAG, "SMS OTP verified: phoneNumberVerified=${response.phoneNumberVerified}")
                    _state.update {
                        it.copy(navigation = ProcessNavigation.ToResult(response.rawResponse, null, false))
                    }
                }

                override fun onError(error: IPificationError) {
                    Log.e(TAG, "SMS OTP error: ${error.getErrorMessage()}")
                    handleError("OTP verification failed: ${error.getErrorMessage()}")
                }
            }
        )
    }

    /**
     * Resends the SMS OTP code via SDK SMSServices.
     * SDK calls backend POST /sms/auth again and navigates to OTP screen with new auth data.
     */
    fun resendSmsOtp(activity: Activity, phoneNumber: String, clientId: String, scope: String, serverId: String) {
        _state.update { it.copy(isLoading = true, message = "Resending SMS code...") }

        com.ipification.mobile.sdk.sms.SMSServices.startVerification(
            activity = activity,
            phoneNumber = phoneNumber,
            scope = scope,
            callback = object : com.ipification.mobile.sdk.sms.callback.SMSCallback {
                override fun onAuthInitiated(response: com.ipification.mobile.sdk.sms.response.SMSAuthResponse) {
                    val resolvedServerId = response.authServer?.id ?: serverId
                    Log.d(TAG, "SMS resend initiated: auth_req_id=${response.authReqId}, nonce=${response.nonce}")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            navigation = ProcessNavigation.ToSmsOtp(
                                phoneNumber = phoneNumber,
                                authReqId = response.authReqId,
                                nonce = response.nonce,
                                clientId = clientId,
                                serverId = resolvedServerId
                            )
                        )
                    }
                }

                override fun onSuccess(response: com.ipification.mobile.sdk.sms.response.SMSTokenResponse) {}

                override fun onError(error: IPificationError) {
                    Log.e(TAG, "SMS resend error: ${error.getErrorMessage()}")
                    handleError("Failed to resend SMS: ${error.getErrorMessage()}")
                }
            }
        )
    }

    /**
     * Calls IM authentication flow
     */
    private fun callIMAuth(activity: Activity, scope: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, message = "Initializing IM authentication...") }
            try {
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "========================================")
                    Log.d(TAG, "IM AUTHENTICATION FLOW STARTED")
                    Log.d(TAG, "========================================")
                    
                    // Ensure Firebase is initialized before proceeding
                    val firebaseReady = com.ipification.demoapp.DemoApplication.ensureFirebaseInitialized(activity.applicationContext)
                    if (!firebaseReady) {
                        Log.e(TAG, "Firebase initialization failed - cannot proceed with IM auth")
                        handleError("Firebase initialization failed. Please restart the app.")
                        return@withContext
                    }
                    Log.d(TAG, "Firebase is ready")
                    
                    IPLogs.getInstance().LOG += "[IM] ${Util.getCurrentDate()} - IM_AUTH - START\n"
                    
                    // Configure IM settings
                    IPConfiguration.getInstance().apply {
                        IM_PRIORITY_APP_LIST = arrayOf("wa", "telegram", "viber") // WhatsApp
                    }
                    Log.d(TAG, "IM configuration set")
                    
                    // Generate state and register device token
                    val state = com.ipification.mobile.sdk.ip.IPificationServices.generateState()
                    com.ipification.demoapp.im.IMHelper.currentState = state
                    Log.d(TAG, "State generated: $state")
                    
                    // Register device token if available
                    val deviceToken = com.ipification.demoapp.im.IMHelper.deviceToken
                    Log.d(TAG, "Checking device token availability...")
                    Log.d(TAG, "Device token: ${if (deviceToken != null) "AVAILABLE (${deviceToken.take(20)}...)" else "NULL"}")
                    
                    if (deviceToken != null) {
                        Log.d(TAG, "Calling registerDevice()")
                        com.ipification.demoapp.im.IMHelper.registerDevice()
                        IPLogs.getInstance().LOG += "[IM] ${Util.getCurrentDate()} - Device registration initiated\n"
                    } else {
                        Log.e(TAG, "FCM token not available - device registration skipped")
                        IPLogs.getInstance().LOG += "[IM] ${Util.getCurrentDate()} - WARNING: FCM token not available yet\n"
                    }
                    
                    withTimeout(60000L) {
                        performIMAuth(activity, scope, state)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "IM authentication error: ${e.message}", e)
                IPLogs.getInstance().LOG += "[IM] ${Util.getCurrentDate()} - IM_AUTH - ERROR: ${e.message}\n"
                handleError("IM authentication failed: ${e.message}")
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
    
    private suspend fun performIMAuth(
        activity: Activity,
        scope: String,
        state: String
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        val authRequestBuilder = com.ipification.mobile.sdk.ip.request.AuthRequest.Builder()
        authRequestBuilder.setState(state)
        authRequestBuilder.setScope(scope)
        authRequestBuilder.addQueryParam("channel", "wa telegram viber")
        
        val callback = object : com.ipification.mobile.sdk.ip.callback.IPificationCallback {
            override fun onSuccess(response: IPAuthResponse) {
                if (continuation.isActive) {
                    IPLogs.getInstance().LOG += "[IM] ${Util.getCurrentDate()} - IM_AUTH - SUCCESS\n"
                    
                    // Call signIn to complete the flow and get the token
                    val responseState = response.state
                    if (responseState != null ) {
                        if(IPConfiguration.getInstance().IM_AUTO_MODE){
                            IPLogs.getInstance().LOG += "[IM] ${Util.getCurrentDate()} - Calling signIn with state $responseState\n"
                            com.ipification.demoapp.im.IMHelper.signIn(responseState) { success, signInResponse ->
                                if (success) {
                                    IPLogs.getInstance().LOG += "[IM] ${Util.getCurrentDate()} - SignIn SUCCESS: $signInResponse\n"
                                    _state.update { it.copy(navigation = ProcessNavigation.ToResult(signInResponse, null, false)) }
                                } else {
                                    IPLogs.getInstance().LOG += "[IM] ${Util.getCurrentDate()} - SignIn FAILED: $signInResponse\n"
                                    handleError("IM SignIn failed: $signInResponse")
                                }
                            }
                        } else{
                            IPLogs.getInstance().LOG += "[IM] ${Util.getCurrentDate()} - Calling token exchange with code ${response.code}\n"
                            exchangeToken(response.code)
                        }

                    } else {
                        // Fallback if no state available
                        val responseData = response.code
                        _state.update { it.copy(navigation = ProcessNavigation.ToResult(responseData, null, false)) }
                    }
                    continuation.resume(Unit)
                }
            }
            
            override fun onError(error: com.ipification.mobile.sdk.ip.exception.IPificationError) {
                if (continuation.isActive) {
                    IPLogs.getInstance().LOG += "[IM] ${Util.getCurrentDate()} - IM_AUTH - ERROR: ${error.getErrorMessage()}\n"
                    handleError(error.getErrorMessage())
                    continuation.resumeWithException(IOException(error.getErrorMessage()))
                }
            }

            override fun onIMCancel() {
                super.onIMCancel()
                handleError("User canceled IM authentication")
                continuation.resumeWithException(IOException("User canceled IM authentication"))
            }
        }
        
        // Start IM authentication
        com.ipification.mobile.sdk.im.IMServices.startAuthentication(activity, authRequestBuilder.build(), callback)
    }
    
    private suspend fun performTokenExchange(code: String): String = withContext(Dispatchers.IO) {
        IPLogs.getInstance().LOG += "[ProcessViewModel] ${Util.getCurrentDate()} - TOKEN_EXCHANGE - START - code: ${code}.\n"
        val config = IPConfiguration.getInstance()
        val tokenUrl = ConfigManager.getBackendUrl() + BuildConfig.TOKEN_EXCHANGE_PATH
        
        // Use the selected server ID from the toggle
        val serverId = ConfigManager.selectedServerId ?: "stage"

        val formBody = FormBody.Builder()
            .add("client_id", config.CLIENT_ID)
            .add("redirect_uri", config.REDIRECT_URI.toString())
            .add("code", code)
            .add("server_id", serverId)
            .build()

//        val request = Request.Builder().url(tokenUrl).post(formBody).build()
        val client = OkHttpClient.Builder()
            .addInterceptor(CustomInterceptor())
            .build()
        val request = Request.Builder()
            .url(tokenUrl)
            .post(formBody)
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                IPLogs.getInstance().LOG += "[ProcessViewModel] ${Util.getCurrentDate()} - TOKEN_EXCHANGE - FAILED - error : ${responseBody}.\n"
                throw IOException("Unexpected code ${response.code}: $responseBody")
            }
            IPLogs.getInstance().LOG += "[ProcessViewModel] ${Util.getCurrentDate()} - TOKEN_EXCHANGE - SUCCESS - response: ${responseBody}.\n"
            responseBody
        }
    }

    private fun handleError(errorMessage: String) {
        IPLogs.getInstance().LOG += "$errorMessage\n"
        _state.update {
            it.copy(navigation = ProcessNavigation.ToResult(null, errorMessage, state.value.isTS43Flow))
        }
    }

    fun onNavigationHandled() {
        _state.update { it.copy(navigation = ProcessNavigation.Idle) }
    }
}
