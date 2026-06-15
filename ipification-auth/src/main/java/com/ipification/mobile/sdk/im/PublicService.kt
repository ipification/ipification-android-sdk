package com.ipification.mobile.sdk.im

import android.accounts.NetworkErrorException
import android.content.Context
import android.net.Network
import android.net.Uri
import com.ipification.mobile.sdk.BuildConfig
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.common.internal.CellularCallback
import com.ipification.mobile.sdk.im.callback.IMPublicAPICallback
import com.ipification.mobile.sdk.im.internal.response.isImResponse
import com.ipification.mobile.sdk.im.internal.response.toImSession
import com.ipification.mobile.sdk.im.response.IMResponse
import com.ipification.mobile.sdk.ip.connection.CellularConnection
import com.ipification.mobile.sdk.ip.exception.CellularException
import com.ipification.mobile.sdk.ip.network.IPNetworkCallback
import com.ipification.mobile.sdk.ip.network.NetworkManager
import com.ipification.mobile.sdk.ip.request.ApiType
import com.ipification.mobile.sdk.ip.request.AuthRequest
import com.ipification.mobile.sdk.ip.response.AuthApiResponse
import com.ipification.mobile.sdk.ip.response.toIPAuthResponse
import com.ipification.mobile.sdk.ip.utils.ErrorCode
import com.ipification.mobile.sdk.ip.utils.ErrorMessages
import com.ipification.mobile.sdk.ip.utils.LogLevel
import com.ipification.mobile.sdk.ip.utils.LogUtils
import com.ipification.mobile.sdk.ip.utils.NetworkUtils

/** Executes the legacy public IM API authentication flow. */
internal class PublicService(context: Context) {

    /** Text and locale overrides applied to the IM verification screen. */
    internal var imLocale: IMLocale? = null

    /** Appearance applied to the IM verification screen. */
    internal var imTheme: IMTheme? = null

    private val context = context.applicationContext
    private val configuration = IPConfiguration.getInstance()
    private val networkManager = NetworkManager.getInstance(context)

    private var callback: IMPublicAPICallback? = null
    private var authRequest: AuthRequest? = null
    private var supportsImFallback = false
    private var triedImFallback = false

    private val transportCallback = object : CellularCallback<AuthApiResponse> {
        override fun onSuccess(response: AuthApiResponse) {
            handleAuthResponse(response)
        }

        override fun onError(error: CellularException) {
            val request = authRequest
            if (supportsImFallback && !triedImFallback && request != null) {
                triedImFallback = true
                performImRequest(request)
            } else {
                callback?.onError(error.toIPificationError())
            }
        }

        override fun onIMCancel() {
            callback?.onCancel()
        }
    }

    /** Builds and starts authentication for [IMPublicAPIServices]. */
    internal fun performAuthentication(
        customRequest: AuthRequest?,
        callback: IMPublicAPICallback
    ) {
        this.callback = callback
        triedImFallback = false

        val request = buildAuthRequest(customRequest) ?: return
        authRequest = request

        val channels = request.queryParameters?.get(CHANNEL_PARAMETER)
        supportsImFallback = channels != null
        if (channels != null && !channels.contains(IP_CHANNEL)) {
            performImRequest(request)
        } else {
            performIpRequest(request)
        }
    }

    /** Creates and validates the authorization request used by the IM public API. */
    private fun buildAuthRequest(customRequest: AuthRequest?): AuthRequest? {
        if (!configuration.customUrls) {
            configuration.AUTHORIZATION_URL = Uri.parse(configuration.getAuthorizationUrl())
        }

        val endpoint = configuration.AUTHORIZATION_URL
            ?: return failValidation(ErrorMessages.EMPTY_AUTH_ENDPOINT, ErrorCode.EMPTY_AUTH_ENDPOINT)
        if (configuration.CLIENT_ID.isBlank()) {
            return failValidation(ErrorMessages.EMPTY_CLIENT_ID, ErrorCode.EMPTY_CLIENT_ID)
        }
        val redirectUri = configuration.REDIRECT_URI
            ?: return failValidation(ErrorMessages.EMPTY_REDIRECT_URI, ErrorCode.EMPTY_REDIRECT_URI)

        val builder = AuthRequest.Builder(endpoint).apply {
            apiType = ApiType.AUTH
            connectTimeout = configuration.AUTH_CONNECT_TIMEOUT
            readTimeout = configuration.AUTH_READ_TIMEOUT
            setRedirectUri(redirectUri)
            setClientId(configuration.CLIENT_ID)
            setResponseType(AUTH_CODE_RESPONSE_TYPE)
            customRequest?.scope?.takeIf(String::isNotBlank)?.let { scope = it }
            customRequest?.state?.takeIf(String::isNotBlank)?.let { state = it }
            customRequest?.headers?.let { headers = HashMap(it) }
            customRequest?.queryParameters?.let { queryParameters = HashMap(it) }
        }

        if (!configuration.customUrls && builder.scope == null) {
            return failValidation(ErrorMessages.EMPTY_SCOPE, ErrorCode.EMPTY_SCOPE)
        }
        if (requiresLoginHint(builder.scope) &&
            builder.queryParameters?.get(LOGIN_HINT_PARAMETER).isNullOrBlank()
        ) {
            return failValidation(ErrorMessages.EMPTY_LOGIN_HINT, ErrorCode.EMPTY_LOGIN_HINT)
        }
        return builder.build()
    }

    /** Attempts authentication through a requested cellular network. */
    private fun performIpRequest(request: AuthRequest) {
        request.includeSimOperatorParameters = true
        configureLogging()

        if (!NetworkUtils.isMobileDataEnabled(context)) {
            if (NetworkUtils.isWifiEnabled(context) && supportsImFallback) {
                performImRequest(request)
            } else {
                deliverTransportError(
                    ErrorMessages.NETWORK_IS_NOT_ACTIVE,
                    ErrorCode.NETWORK_IS_NOT_ACTIVE
                )
            }
            return
        }

        if (!NetworkUtils.isWifiEnabled(context)) {
            connect(request, null)
            return
        }

        networkManager.connect(object : IPNetworkCallback {
            override fun onSuccess(network: Network) {
                connect(request, network)
            }

            override fun onError(error: CellularException) {
                transportCallback.onError(error)
            }
        })
    }

    /** Attempts authentication through the device's default internet network. */
    private fun performImRequest(request: AuthRequest) {
        request.includeSimOperatorParameters = false
        configureLogging()

        if (!NetworkUtils.isMobileDataEnabled(context) && !NetworkUtils.isWifiEnabled(context)) {
            deliverTransportError(ErrorMessages.IM_NO_NETWORK_ERROR, ErrorCode.IM_NO_NETWORK_ERROR)
            return
        }
        connect(request, null)
    }

    /** Executes the request through an optional cellular network. */
    private fun connect(request: AuthRequest, network: Network?) {
        runCatching {
            CellularConnection(request, transportCallback, network, context)
                .makeConnection(request.toUri(context).toString(), false)
        }.onFailure {
            transportCallback.onError(
                CellularException().apply {
                    sdkErrorCode = ErrorCode.NETWORK_RESPONSE_FAILED
                    errorDescription = it.localizedMessage
                    exception = it as? Exception ?: Exception(it)
                }
            )
        }
    }

    /** Delivers an IM session or completed IP authentication response. */
    private fun handleAuthResponse(response: AuthApiResponse) {
        if (response.isImResponse) {
            val session = response.toImSession()
            if (session == null) {
                deliverPublicError(ErrorMessages.EMPTY_IM_HEADER, ErrorCode.EMPTY_IM_HEADER)
            } else {
                callback?.onSuccess(IMResponse(session), null)
            }
            return
        }

        val ipResponse = response.toIPAuthResponse()
        if (ipResponse == null) {
            deliverPublicError(response.getErrorMessage(), ErrorCode.SERVER_RESPONSE_FAILED)
        } else {
            callback?.onSuccess(null, ipResponse)
        }
    }

    private fun requiresLoginHint(scope: String?): Boolean {
        return scope == OPENID_SCOPE ||
            scope?.contains(PHONE_VERIFY_SCOPE) == true ||
            scope?.contains(PROFILE_SCOPE) == true
    }

    private fun configureLogging() {
        LogUtils.addLevel(if (BuildConfig.DEBUG) LogLevel.ALL else LogLevel.ERROR)
    }

    private fun deliverTransportError(message: String, code: Int) {
        transportCallback.onError(
            CellularException().apply {
                sdkErrorCode = code
                errorDescription = message
                exception = NetworkErrorException(message)
            }
        )
    }

    private fun deliverPublicError(message: String, code: Int) {
        callback?.onError(
            CellularException().apply {
                sdkErrorCode = code
                errorDescription = message
            }.toIPificationError()
        )
    }

    private fun failValidation(message: String, code: Int): AuthRequest? {
        deliverPublicError(message, code)
        return null
    }

    private companion object {
        const val AUTH_CODE_RESPONSE_TYPE = "code"
        const val CHANNEL_PARAMETER = "channel"
        const val IP_CHANNEL = "ip"
        const val LOGIN_HINT_PARAMETER = "login_hint"
        const val OPENID_SCOPE = "openid"
        const val PHONE_VERIFY_SCOPE = "ip:phone_verify"
        const val PROFILE_SCOPE = "ip:profile"
    }
}
