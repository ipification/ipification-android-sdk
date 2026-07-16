package com.ipification.mobile.sdk.ip

import com.ipification.mobile.sdk.ip.IPConfiguration

import com.ipification.mobile.sdk.BuildConfig
import android.accounts.NetworkErrorException
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.Keep
import com.ipification.mobile.sdk.ip.common.internal.CellularCallback
import com.ipification.mobile.sdk.ip.network.IPNetworkCallback
import com.ipification.mobile.sdk.ip.connection.CellularConnection
import com.ipification.mobile.sdk.ip.exception.CellularException
import com.ipification.mobile.sdk.ip.network.NetworkManager
import com.ipification.mobile.sdk.ip.request.ApiType
import com.ipification.mobile.sdk.ip.request.AuthRequest
import com.ipification.mobile.sdk.ip.response.AuthApiResponse
import com.ipification.mobile.sdk.im.internal.response.isImResponse
import com.ipification.mobile.sdk.im.internal.response.toImSession
import com.ipification.mobile.sdk.ip.response.CoverageResponse
import com.ipification.mobile.sdk.ip.response.RedirectResponse
import com.ipification.mobile.sdk.ip.utils.*
import com.ipification.mobile.sdk.ip.utils.LogUtils
import com.ipification.mobile.sdk.ip.utils.NetworkUtils
import com.ipification.mobile.sdk.ip.ui.AuthWebViewActivity
import com.ipification.mobile.sdk.ip.ui.WebViewAuthBridge
import com.ipification.mobile.sdk.im.IMLocale
import com.ipification.mobile.sdk.im.IMService
import com.ipification.mobile.sdk.im.IMTheme
import com.ipification.mobile.sdk.im.VerifyCompleteListener
import com.ipification.mobile.sdk.im.model.IMSession
import androidx.core.net.toUri

/** Internal engine that routes coverage and authentication requests over cellular or IM flows. */
internal class InternalService<T>() {
        /** Locale used when the flow falls back to IM verification. */
        internal var imLocale: IMLocale? = null

        /** Theme used when the flow falls back to IM verification. */
        internal var imTheme: IMTheme? = null

        /** Coordinates Android cellular network requests for IP authentication. */
        private lateinit var networkManager : NetworkManager

        /** Application context used for network, telephony, and configuration access. */
        private lateinit var context: Context

        /** Activity used when the flow needs to open UI such as WebView or IM verification. */
        private lateinit var activity: Activity

        /** Whether this request may fall back from IP authentication to IM verification. */
        private var supportsImFallback = false

        /** Prevents retrying IM fallback more than once for the same request flow. */
        @Volatile private var hasTriedImFallback = false

        /** Whether the SDK should unregister the cellular network before delivering terminal callbacks. */
        private var autoUnregisterNetwork = IPConfiguration.getInstance().autoUnregisterNetwork

        /** Original auth request reused when IP authentication fails and IM fallback is available. */
        private var imFallbackRequest: AuthRequest? = null

        /** Snapshot of debug logging configuration for this service instance. */
        private var debugEnabled = false

        /** Callback supplied by the caller and consumed exactly once at terminal completion. */
        private var finalCallback: CellularCallback<T>? = null

        constructor(ctx: Context) : this() {
                this.context = ctx.applicationContext
                this.networkManager = NetworkManager.getInstance(ctx)
                this.debugEnabled = IPConfiguration.getInstance().debug
                IPConfiguration.getInstance().currentState = ""
                if(IPConfiguration.getInstance().useWebViewInsteadOfApi){
                        IPConfiguration.getInstance().bindAppToCellularNetwork = true
                }
        }
        /** Checks coverage without a phone-number hint. */
        internal fun checkCoverage(callback: CellularCallback<T>) {
                networkManager.reset()
                this.finalCallback = callback

                if(IPConfiguration.getInstance().customUrls == false){
                        IPConfiguration.getInstance().COVERAGE_URL =
                            IPConfiguration.getInstance().getCheckCoverageUrl().toUri()
                }
                if(IPConfiguration.getInstance().CLIENT_ID.isEmpty()){
                        handleException(java.lang.NullPointerException(ErrorMessages.EMPTY_CLIENT_ID), ErrorCode.EMPTY_CLIENT_ID)
                        return
                }

                if(IPConfiguration.getInstance().COVERAGE_URL == null) {
                        handleException(
                                java.lang.NullPointerException(ErrorMessages.EMPTY_COVERAGE_ENDPOINT),
                                ErrorCode.EMPTY_COVERAGE_ENDPOINT
                        )
                        return
                }

                val request: AuthRequest
                val requestBuilder = AuthRequest.Builder(IPConfiguration.getInstance().COVERAGE_URL)
                requestBuilder.apiType = ApiType.COVERAGE
                requestBuilder.connectTimeout = IPConfiguration.getInstance().COVERAGE_CONNECT_TIMEOUT
                requestBuilder.readTimeout = IPConfiguration.getInstance().COVERAGE_READ_TIMEOUT

                if(IPConfiguration.getInstance().REDIRECT_URI != null){
                        requestBuilder.setRedirectUri(IPConfiguration.getInstance().REDIRECT_URI!!)
                }
                requestBuilder.setClientId(IPConfiguration.getInstance().CLIENT_ID)

                request = requestBuilder.build()
                performRequest(request)
        }
        /** Checks coverage using a phone number as an additional hint. */
        fun checkCoverage(phoneNumber: String, callback: CellularCallback<T>) {
                networkManager.reset()
                this.finalCallback = callback

                if(IPConfiguration.getInstance().customUrls == false){
                        IPConfiguration.getInstance().COVERAGE_URL =
                            IPConfiguration.getInstance().getCheckCoverageUrl().toUri()
                }

                if(IPConfiguration.getInstance().COVERAGE_URL == null) {
                        handleException(
                                java.lang.NullPointerException(ErrorMessages.EMPTY_COVERAGE_ENDPOINT),
                                ErrorCode.EMPTY_COVERAGE_ENDPOINT
                        )
                        return
                }
                if(IPConfiguration.getInstance().CLIENT_ID.isEmpty()){
                        handleException(java.lang.NullPointerException(ErrorMessages.EMPTY_CLIENT_ID), ErrorCode.EMPTY_CLIENT_ID)
                        return
                }
                if(phoneNumber == ""){
                        handleException(Exception(ErrorMessages.EMPTY_PHONE_NUMBER), ErrorCode.EMPTY_PHONE_NUMBER)
                        return
                }
                log("start checking coverage \n")
                val coverageRequestBuilder = AuthRequest.Builder(IPConfiguration.getInstance().COVERAGE_URL)
                coverageRequestBuilder.apiType = ApiType.COVERAGE
                coverageRequestBuilder.connectTimeout = IPConfiguration.getInstance().COVERAGE_CONNECT_TIMEOUT
                coverageRequestBuilder.readTimeout = IPConfiguration.getInstance().COVERAGE_READ_TIMEOUT
                if(IPConfiguration.getInstance().REDIRECT_URI != null){
                        coverageRequestBuilder.setRedirectUri(IPConfiguration.getInstance().REDIRECT_URI!!)
                }
                coverageRequestBuilder.setClientId(IPConfiguration.getInstance().CLIENT_ID)
                coverageRequestBuilder.addQueryParam("phone", phoneNumber)
                val request: AuthRequest = coverageRequestBuilder.build()
                performRequest(request)
        }

        /**
         * perform Authorization API to get Code
         * @param callback : CellularCallback<T>
         */
        @Deprecated("performAuth is deprecated. Change to use performIPAuthentication",
                ReplaceWith("performIPAuthentication(activity, callback)")
        )
        fun performAuth(activity : Activity, callback: CellularCallback<T>){
                performAuth(activity,null, callback)
        }



        /**
         * perform IPification Authorization
         * @param callback : CellularCallback<T>
         */
        internal fun performAuthentication(activity : Activity, customRequest: AuthRequest?, callback: CellularCallback<T>){
                performAuth(activity,customRequest, callback)
        }
        /**
         * perform Authorization API to get Code
         * @param customRequest : AuthRequest
         * @param callback : CellularCallback<T>
         */
        private fun performAuth(activity: Activity, customRequest: AuthRequest?, callback: CellularCallback<T>) {
                this.activity = activity
                this.finalCallback = callback
                this.supportsImFallback = false
                this.hasTriedImFallback = false
                this.imFallbackRequest = null

                if(IPConfiguration.getInstance().customUrls == false){
                        IPConfiguration.getInstance().AUTHORIZATION_URL =
                            IPConfiguration.getInstance().getAuthorizationUrl().toUri()
                }
                if(IPConfiguration.getInstance().AUTHORIZATION_URL == null) {
                        handleException(
                                java.lang.NullPointerException(ErrorMessages.EMPTY_AUTH_ENDPOINT),
                                ErrorCode.EMPTY_AUTH_ENDPOINT
                        )
                        return
                }
                if(IPConfiguration.getInstance().CLIENT_ID.isEmpty()){
                        handleException(java.lang.NullPointerException(ErrorMessages.EMPTY_CLIENT_ID), ErrorCode.EMPTY_CLIENT_ID)
                        return
                }
                if(IPConfiguration.getInstance().REDIRECT_URI == null || IPConfiguration.getInstance().REDIRECT_URI.toString().isEmpty()){
                        handleException(java.lang.NullPointerException(ErrorMessages.EMPTY_REDIRECT_URI), ErrorCode.EMPTY_REDIRECT_URI)
                        return
                }

                val authRequestBuilder = AuthRequest.Builder(IPConfiguration.getInstance().AUTHORIZATION_URL)
                authRequestBuilder.apiType = ApiType.AUTH
                authRequestBuilder.connectTimeout = IPConfiguration.getInstance().AUTH_CONNECT_TIMEOUT
                authRequestBuilder.readTimeout = IPConfiguration.getInstance().AUTH_READ_TIMEOUT

                if(IPConfiguration.getInstance().REDIRECT_URI != null){
                        authRequestBuilder.setRedirectUri(IPConfiguration.getInstance().REDIRECT_URI!!)
                }
                authRequestBuilder.setClientId(IPConfiguration.getInstance().CLIENT_ID)

                if(IPConfiguration.getInstance().RESPONSE_TYPE_CODE != ""){
                        authRequestBuilder.setResponseType(IPConfiguration.getInstance().RESPONSE_TYPE_CODE)
                }


                var isRequestParamPresent = false
                // add request
                if(customRequest != null){
                        isRequestParamPresent = customRequest.queryParameters?.containsKey("request") ?: false
                        if(isRequestParamPresent){
                                IPConfiguration.getInstance().DEFAULT_SCOPE = ""
                                IPConfiguration.getInstance().CONSENT_ID_VALUE = ""
                        }
                        if(customRequest.scope != null){
                                authRequestBuilder.scope = customRequest.scope
                        }
                        if(customRequest.state != null && customRequest.state != ""){
                                authRequestBuilder.state = customRequest.state
                        }
                        if(customRequest.headers?.isNotEmpty() == true){
                                if(authRequestBuilder.headers == null){
                                        authRequestBuilder.headers = HashMap()
                                }
                                authRequestBuilder.headers!!.putAll(customRequest.headers!!)
                        }
                        if(customRequest.queryParameters?.isNotEmpty() == true){
                                if(authRequestBuilder.queryParameters == null){
                                        authRequestBuilder.queryParameters = HashMap()
                                }
                                authRequestBuilder.queryParameters!!.putAll(customRequest.queryParameters!!)
                        }

                }
                if(IPConfiguration.getInstance().enableParamsValidation){
                        if( isRequestParamPresent == false && IPConfiguration.getInstance().customUrls == false){
                                if(authRequestBuilder.scope.isNullOrEmpty() == true){
                                        handleException(java.lang.NullPointerException(ErrorMessages.EMPTY_SCOPE), ErrorCode.EMPTY_SCOPE)
                                        return
                                }
                                if(authRequestBuilder.scope == "openid" || authRequestBuilder.scope?.contains("ip:phone_verify") == true || authRequestBuilder.scope?.contains("ip:profile") == true){
                                        if(authRequestBuilder.queryParameters?.get("login_hint") == null || authRequestBuilder.queryParameters?.get("login_hint") == ""){
                                                handleException(java.lang.NullPointerException(ErrorMessages.EMPTY_LOGIN_HINT), ErrorCode.EMPTY_LOGIN_HINT)
                                                return
                                        }
                                }
                        }

                }

                val request: AuthRequest = authRequestBuilder.build()
                if(IPConfiguration.getInstance().IM_AUTO_MODE){
                        if(IPConfiguration.getInstance().IM_PRIORITY_APP_LIST.isEmpty()){
                                handleException(java.lang.NullPointerException(ErrorMessages.EMPTY_IM_PRIORITY_APP_LIST), ErrorCode.EMPTY_IM_PRIORITY_APP_LIST)
                                return
                        }
                        if(request.queryParameters == null){
                                request.queryParameters = HashMap()
                        }
                        request.queryParameters?.set("channel", IPConfiguration.getInstance().IM_PRIORITY_APP_LIST.joinToString(separator = " "))
                        IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - channel list" + IPConfiguration.getInstance().IM_PRIORITY_APP_LIST.size + "\n"
                        IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - set channel " + IPConfiguration.getInstance().IM_PRIORITY_APP_LIST.joinToString(separator = " ") + "\n"
                }


                val channels = request.queryParameters?.get("channel")
                supportsImFallback = channels != null

                if(supportsImFallback){
                        imFallbackRequest = request
                }
                if(channels != null && !channels.contains("ip")){
                        IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - get channel $channels ${request.queryParameters?.size}\n"
                        performIMRequest(request)
                }else{
                        performRequest(request)
                }
        }
        /**
         * Starts the WebView flow and opens the authorization request URL.
         * Assumes process is already bound to the desired network when invoked.
         * Listens for redirect completion via WebViewAuthBridge and forwards the result.
         */
        private fun startWebViewFlow(manager: ConnectivityManager, request: AuthRequest, callback: CellularCallback<T>) {
                try {
                        val url = request.toUri(context).toString()
                        val trafficSnapshot = if (IPConfiguration.getInstance().debug) {
                                WebViewDebugTrafficTracker.start(context, url)
                        } else {
                                null
                        }
                        // Register bridge listener BEFORE starting activity
                        WebViewAuthBridge.setListener { result ->
                                if (result.isSuccess && result.url != null) {
                                        trafficSnapshot?.let {
                                                WebViewDebugTrafficTracker.finish(
                                                        context = context,
                                                        snapshot = it,
                                                        endUrl = result.url,
                                                        note = "includes_auth_redirects_and_callback"
                                                )
                                        }
                                        log("WebView completed with redirect: ${result.url}")
                                        // Forward as AuthApiResponse (internalCallback will handle unregister and delay)
                                        try {
                                                callback.onSuccess(
                                                        castResponse(AuthApiResponse(200, result.url, null))
                                                )
                                        } catch (e: Exception) {
                                                handleException(e, ErrorCode.GENERAL_ERROR)
                                        }
                                } else {
                                        trafficSnapshot?.let {
                                                WebViewDebugTrafficTracker.finish(
                                                        context = context,
                                                        snapshot = it,
                                                        endUrl = result.url,
                                                        note = if (result.isCancelled) {
                                                                "cancelled_before_callback"
                                                        } else {
                                                                "webview_error_before_callback"
                                                        }
                                                )
                                        }
                                        // Error or Cancel -> propagate as error
                                        val err = CellularException()
                                        err.sdkErrorCode = ErrorCode.GENERAL_ERROR
                                        err.errorDescription = result.errorMessage ?: if (result.isCancelled) "cancelled" else "webview_error"
                                        err.exception = Exception(err.errorDescription)
                                        callback.onError(err)
                                }
                        }

                        log("startWebViewFlow -> $url")
                        val intent = Intent(activity, AuthWebViewActivity::class.java)
                        intent.putExtra(AuthWebViewActivity.EXTRA_URL, url)
                        activity.startActivity(intent)
                } catch (e: Exception) {
                        handleException(e, ErrorCode.GENERAL_ERROR)
                }
        }

        /**
         * perform normal request
         * @param request : AuthRequest
         */
        private fun performRequest(request: AuthRequest) {
                request.includeSimOperatorParameters = true

                log("performRequest with type ${request.apiType?.name}")

                if (BuildConfig.DEBUG) {
                        LogUtils.addLevel(
                                LogLevel.ALL
                        )
                } else {
                        LogUtils.addLevel(
                                LogLevel.ERROR
                        )
                }
                if (NetworkUtils.isAirplaneModeOn(context)){
                        //check and do the IM flow
                        if (NetworkUtils.isWifiEnabled(context) && supportsImFallback) {
                                performIMRequest(request)
                                return
                        }
                        handleAirPlaneCase()
                        return
                }
                if (!NetworkUtils.isMobileDataEnabled(context)) {
                        log("isMobileDataEnabled return false")
                        //check and do the IM flow
                        if (NetworkUtils.isWifiEnabled(context) && supportsImFallback) {
                                performIMRequest(request)
                                return
                        }
                        handleUnAvailableCase()
                        return
                }
                if (!NetworkUtils.isWifiEnabled(context)) {
                        IPConfiguration.getInstance().TIMEOUT_RELEASE_NETWORK = 100L
                        log("WIFI IS NOT ENABLED")
                        NetworkUtils.checkPrivateIP(context, null)
                        if (!NetworkUtils.hasInternet(context)){
                                log("no Internet")
                                Log.d("InternalService", "isCellularConnected: " + NetworkUtils.isCellularConnected(context) )
                                Log.d("InternalService", "getOperatorName: " + DeviceUtils.getInstance(context).activeSimOperator().getOperatorName())
                                if(!NetworkUtils.isCellularConnected(context)){
                                        log("NO SIM was active")
                                        handleNoSIMCase()
                                        return
                                }else{
                                        log("Cellular connected")
                                }
                                Log.e("InternalService", "cellular is on but no Internet, forcing cellular and wait for internet is ready")
                                forceCellularConnection(request, false, "cellular is on but no Internet, forcing cellular and wait for internet is ready")
                                return
                        }else{
                                log("Internet is active")
                        }
                        forceCellularConnection(request, false, "wifi disabled")
                        return
                }else{
                        log("WIFI IS ENABLED")
                        IPConfiguration.getInstance().TIMEOUT_RELEASE_NETWORK = IPConfiguration.getInstance().DEFAULT_TIMEOUT_RELEASE_NETWORK
                }
                log("isMobileDataEnabled return true")
                forceCellularConnection(request, true, "wifi enabled")
        }
        /**
         * Continues the request using the selected network and configured transport mode.
         *
         * The method binds the process when requested, starts the WebView flow when configured,
         * or passes the selected network to the OkHttp request path.
         *
         * @param isWifiEnabled Whether Wi-Fi was active when this connection path was selected.
         * @param network Cellular network selected for this request, or null when no network is available.
         * @param request API request to execute.
         * @param bindAppToCellularNetwork Whether the process should be bound to the selected network.
         * @param useWebViewInsteadOfApi Whether the request should continue through the WebView flow.
         * @param callback Callback that receives the request result.
         */
        private fun handleConnection(
                isWifiEnabled: Boolean,
                network: Network?,
                request: AuthRequest,
                bindAppToCellularNetwork: Boolean,
                useWebViewInsteadOfApi: Boolean,
                callback: CellularCallback<T>
        ) {
                val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                // Pre-M: no bind API — just connect using the provided network (if any)
                log("handleConnection : bindAppToCellularNetwork $bindAppToCellularNetwork - network $network")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !bindAppToCellularNetwork) {
                        connect(request, network, callback)
                        return
                }

                // Try to bind the whole process to `network` when bindAppToCellularNetwork is TRUE
                val bound = if(network != null) {
                        log("bindProcessToNetwork $network")
                        manager.bindProcessToNetwork(network)
                } else false
                log("network $network - bound : $bound - useWebViewInsteadOfApi: $useWebViewInsteadOfApi")
                if (useWebViewInsteadOfApi && (isWifiEnabled == false || bound)) {
                        // If your WebView flow will use the process-wide default network,
                        // start it now and (optionally) unbind later when done.
                        log("startWebViewFlow ... ")
                        startWebViewFlow(manager, request, callback)

                        return
                }

                // Keep passing the selected network so OkHttp can use network-scoped sockets,
                // DNS, and diagnostics even when the process bind succeeds.
                val targetForConnect: Network? = network
                log("connect with targetForConnect $targetForConnect")
                connect(request, targetForConnect, callback)
        }

        /**
         * Requests a cellular network, then continues the request on that selected network.
         *
         * @param request API request to execute after cellular network selection succeeds.
         * @param isWifiEnabled Whether Wi-Fi was active when this request path was selected.
         * @param reason Short log label describing why cellular selection is being forced.
         */
        private fun forceCellularConnection(
                request: AuthRequest,
                isWifiEnabled: Boolean,
                reason: String
        ) {
                log("force cellular network - $reason")
                networkManager.connect(object : IPNetworkCallback {
                        override fun onSuccess(network: Network) {
                                log("networkManager.connect - onSuccess: ${request.apiType?.name} - $reason")
                                handleConnection(isWifiEnabled, network, request, IPConfiguration.getInstance().bindAppToCellularNetwork,
                                        IPConfiguration.getInstance().useWebViewInsteadOfApi, internalCallback)
                        }

                        override fun onError(error: CellularException) {
                                Log.d("InternalService", "connect error " + error.sdkErrorCode)
                                log("networkManager.connect - error: ${error.sdkErrorCode} - $reason")

                                val timeout = IPConfiguration.getInstance().TIMEOUT_RELEASE_NETWORK
                                finishWithUnregisterDelay(timeout) { it.onError(error) }
                        }
                })
        }

        /** Handles terminal responses from IP/API requests and routes them to the final callback. */
        private val internalCallback = object: CellularCallback<T>{
                override fun onSuccess(response: T) {
                        log("internal callback success: $response")
                        val timeout = IPConfiguration.getInstance().TIMEOUT_RELEASE_NETWORK
                        when (response) {
                                is RedirectResponse -> {
                                        // Handle redirect response...
                                        handleRedirect(response, this)
                                }

                                is CoverageResponse -> {
                                        log("internal callback received CoverageResponse")
                                        if (!response.isAvailable()) {
                                                finishWithUnregisterDelay(timeout) {
                                                        it.onSuccess(response)
                                                }
                                        } else {
                                                finishSuccess(response)
                                        }
                                }

                                is AuthApiResponse -> {
                                        log("internal callback received AuthApiResponse")
                                        if (response.isImResponse) {
                                                val session = response.toImSession()
                                                if (session != null) {
                                                        takeFinalCallback()?.let {
                                                                handleIMResponse(session, it)
                                                        }
                                                } else{
                                                        val cellularException = CellularException()
                                                        cellularException.errorDescription = ErrorMessages.EMPTY_IM_HEADER
                                                        cellularException.httpCode = response.statusCode
                                                        cellularException.sdkErrorCode = ErrorCode.EMPTY_IM_HEADER
                                                        cellularException.exception =
                                                                IllegalStateException(ErrorMessages.EMPTY_IM_HEADER)
                                                        finishError(cellularException)
                                                }

                                        }

                                        else {
                                                finishWithUnregisterDelay(timeout) {
                                                        it.onSuccess(response)
                                                }
                                        }
                                }
                                else -> {
                                        log("internal callback received ${response?.let { it::class.java.simpleName }}")
                                        finishSuccess(response)
                                }
                        }
                }

                override fun onError(error: CellularException) {
                        log("internal callback error: ${error.getErrorMessage()}")
                        // Try IM flow if applicable
                        if (supportsImFallback && !hasTriedImFallback && imFallbackRequest != null) {
                                hasTriedImFallback = true
                                releaseProcessNetworkBinding()
                                performIMRequest(requireNotNull(imFallbackRequest))
                                return
                        }
                        // unregister + delayed callback
                        val timeout = IPConfiguration.getInstance().TIMEOUT_RELEASE_NETWORK
                        finishWithUnregisterDelay(timeout) { it.onError(error) }

                }

                override fun onIMCancel() {
                        finishCancel()
                }
        }

        /**
         * Follows a redirect returned by the IP authorization endpoint.
         *
         * @param response Redirect response containing the next URL and API type.
         * @param cellularCallback Callback that receives the redirected request result.
         */
        private fun handleRedirect(response: RedirectResponse, cellularCallback: CellularCallback<T>) {
                val url =  response.getUrl()
                log("redirect response with url: $url")
                val requestBuilder = AuthRequest.Builder(url.toUri())
                requestBuilder.apiType = response.apiType
                if(IPConfiguration.getInstance().REDIRECT_URI != null){
                        requestBuilder.setRedirectUri(IPConfiguration.getInstance().REDIRECT_URI!!)
                }
                val request = requestBuilder.build()
                CellularConnection(
                        request,
                        cellularCallback,
                        null,
                        context
                ).makeConnection(url, true)
        }

        /** Opens IM verification and forwards its terminal result. */
        private fun handleIMResponse(
                session: IMSession,
                callback: CellularCallback<T>
        ) {
                IMService.startVerification(activity, imTheme, imLocale, session, object : VerifyCompleteListener{
                        override fun onSuccess(sessionId: String, responseData: String?) {
                                IMService.clearIMSession(activity.applicationContext)
                                callback.onSuccess(
                                        castResponse(AuthApiResponse(200, responseData.orEmpty(), null))
                                )
                        }

                        override fun onFail(errorMessage: String) {
                                IMService.clearIMSession(activity.applicationContext)
                                val cellularException =
                                        CellularException()
                                cellularException.errorDescription = errorMessage
                                cellularException.sdkErrorCode = ErrorCode.IM_FAILED
                                cellularException.exception = IllegalStateException(errorMessage)
                                callback.onError(cellularException)
                        }

                        override fun onCancel() {
                                callback.onIMCancel()
                        }

                })
        }

        /**
         * perform IM request
         * @param authRequest : AuthRequest
         */
        private fun performIMRequest(authRequest: AuthRequest) {
                // IM requests use normal internet and do not send cellular SIM query parameters.
                authRequest.includeSimOperatorParameters = false
                
                if (BuildConfig.DEBUG) {
                        LogUtils.addLevel(
                                LogLevel.ALL)
                } else {
                        LogUtils.addLevel(
                                LogLevel.ERROR)
                }
                if (!NetworkUtils.isMobileDataEnabled(context) && !NetworkUtils.isWifiEnabled(context)) {
                        handleNoNetworkError()
                        return
                }
                connect(authRequest, null, internalCallback)

        }
        /*
        * perform request
        * @param cellularRequest : AuthRequest
        * @param network : Network?
        * @param callback : CellularCallback<T>
         */
        private fun connect(cellularRequest: AuthRequest, network: Network?, callback: CellularCallback<T>){
                log("connect with the API - ${cellularRequest.apiType}")
                val okHttpCellularConnection =
                        CellularConnection(
                                cellularRequest,
                                callback,
                                network,
                                context
                        )
                log("connect with the API url - ${cellularRequest.toUri(context)}")
                okHttpCellularConnection.makeConnection(cellularRequest.toUri(context).toString(), false)

        }

        /** Converts an internally selected response type to this service's expected generic type. */
        @Suppress("UNCHECKED_CAST")
        private fun castResponse(response: Any): T = response as T

        /** Captures and clears the active callback before delivering a terminal result. */
        private fun takeFinalCallback(): CellularCallback<T>? {
                val callback = finalCallback
                finalCallback = null
                return callback
        }

        private fun finishSuccess(response: T) {
                releaseProcessNetworkBinding()
                takeFinalCallback()?.onSuccess(response)
        }

        private fun finishError(error: CellularException) {
                releaseProcessNetworkBinding()
                takeFinalCallback()?.onError(error)
        }

        private fun finishCancel() {
                releaseProcessNetworkBinding()
                takeFinalCallback()?.onIMCancel()
        }

        private fun finishWithUnregisterDelay(
                timeoutMs: Long,
                action: (CellularCallback<T>) -> Unit
        ) {
                val callback = takeFinalCallback() ?: return
                releaseProcessNetworkBinding()
                runWithUnregisterDelay(context, autoUnregisterNetwork, timeoutMs) {
                        action(callback)
                }
        }

        /*
        * perform request
        * @param context : Context
        * @param autoUnregister : Boolean
        * @param timeoutMs : Long
        * @param action : () -> Unit
         */
        private fun runWithUnregisterDelay(
                context: Context,
                autoUnregister: Boolean,
                timeoutMs: Long,
                action: () -> Unit
        ) {
                // Always use the applicationContext to avoid holding onto an Activity
                val appCtx = context.applicationContext
                if (autoUnregister) {
                        // 1) Safely unregister (won’t throw)
                        runCatching {
                                log("unregisterNetwork")
                                unregisterNetwork(appCtx)
                        }.onFailure {
                                log("unregisterNetwork failed ${it.message}")
                                Log.e(TAG, "unregisterNetwork failed", it)
                        }

                        // 2) Schedule your callback, but catch any exception inside it
                        mainHandler.postDelayed({
                                runCatching {
                                        action()
                                }.onFailure {
                                        Log.e(TAG, "Delayed action failed", it)
                                }
                        }, timeoutMs)
                } else {
                        // Immediate, but still safe
                        runCatching {
                                action()
                        }.onFailure {
                                Log.e(TAG, "Immediate action failed", it)
                        }
                }
        }

        /** Clears process-wide cellular binding before leaving cellular-only request paths. */
        private fun releaseProcessNetworkBinding() {
                if (!IPConfiguration.getInstance().bindAppToCellularNetwork ||
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        return
                }

                runCatching {
                        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                        log("unbindProcessToNetwork")
                        manager.bindProcessToNetwork(null)
                }.onFailure {
                        Log.e(TAG, "unbindProcessToNetwork failed", it)
                        log("unbindProcessToNetwork failed ${it.message}")
                }
        }

        /*
        * handle UnAvailable cases
        *
        */
        private fun handleUnAvailableCase() {
                LogUtils.error("onUnavailable: Your cellular network is not active or not available")
                log("handleUnAvailableCase - onUnavailable: Your cellular network is not active or not available")
                val cellularException =
                        CellularException()
                cellularException.sdkErrorCode = ErrorCode.NETWORK_IS_NOT_ACTIVE
                cellularException.exception =
                        NetworkErrorException(ErrorMessages.NETWORK_IS_NOT_ACTIVE)
                finishError(cellularException)
        }
        /**
         * handle no SIM case
         */
        private fun handleNoSIMCase() {
                LogUtils.error("onNoSIMCaseActive: Your cellular network is not active or not available. (No active SIM)")
                log("onNoSIMCaseActive - onUnavailable: Your cellular network is not active or not available. (No active SIM)")
                val cellularException =
                        CellularException()
                cellularException.sdkErrorCode = ErrorCode.SIM_IS_UNAVAILABLE
                cellularException.exception =
                        NetworkErrorException(ErrorMessages.SIM_IS_NOT_ACTIVE)
                finishError(cellularException)
        }
        /**
         * handle airplane case
         */
        private fun handleAirPlaneCase() {
                LogUtils.error("onUnavailable: Your cellular network is not active or not available (airplane mode)")
                log("handleUnAvailableCase - onUnavailable: Your cellular network is not active or not available")
                val cellularException =
                        CellularException()
                cellularException.sdkErrorCode = ErrorCode.NETWORK_IS_NOT_ACTIVE
                cellularException.exception =
                        Exception(ErrorMessages.NETWORK_IS_NOT_ACTIVE_AIRPLANE_MODE)
                finishError(cellularException)
        }
        /**
         * handle network error
         */
        private fun handleNoNetworkError() {
                log("handleNoNetworkError")
                LogUtils.error("No Internet connection")
                val cellularException =
                        CellularException()
                cellularException.sdkErrorCode = ErrorCode.IM_NO_NETWORK_ERROR
                cellularException.exception =
                        NetworkErrorException(ErrorMessages.IM_NO_NETWORK_ERROR)
                finishError(cellularException)
        }
        /** Converts an exception into the low-level callback error model. */
        private fun handleException(e: Exception, code: Int){
                if(finalCallback != null){
                        log("handleException - cellularCallback is not null ${e.localizedMessage}")
                        val cellularException =
                                CellularException()
                        cellularException.sdkErrorCode = code
                        cellularException.exception = e
                        callbackFailed(cellularException)
                }else{
                        log("handleException - finalCallback is null - ${e.localizedMessage}")
                        Log.e("InternalService", "finalCallback is null - "+ e.localizedMessage)
                }
        }
        /** Delivers an exception through the active callback. */
        private fun callbackFailed(exception: CellularException) {
                log("callbackFailed - ${exception.errorDescription}")
                finishError(exception)
        }
        /** Appends an internal service message when debug logging is enabled. */
        private fun log(message: String){
                if(debugEnabled){
                        Log.d("InternalService", message)
                        IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - InternalService - ${message}\n"
                }
        }
        
        @Keep companion object {
                private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
                private const val TAG = "InternalService"
                /** Releases the cellular network retained by the SDK. */
                fun unregisterNetwork(context: Context): Boolean{
                        return NetworkManager.getInstance(context).unregister()
                }
        }

}
