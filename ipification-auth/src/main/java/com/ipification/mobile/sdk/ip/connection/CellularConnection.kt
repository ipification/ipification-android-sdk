package com.ipification.mobile.sdk.ip.connection

import android.content.Context
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.common.response.ApiResponse
import com.ipification.mobile.sdk.ip.exception.CellularException
import com.ipification.mobile.sdk.ip.interceptor.HandleRedirectInterceptor
import com.ipification.mobile.sdk.ip.interceptor.LoggingInterceptor
import com.ipification.mobile.sdk.ip.common.internal.CellularCallback
import com.ipification.mobile.sdk.ip.response.AuthApiResponse
import com.ipification.mobile.sdk.im.internal.response.isImResponse
import com.ipification.mobile.sdk.ip.network.NetworkDns
import com.ipification.mobile.sdk.ip.request.ApiType
import com.ipification.mobile.sdk.ip.request.AuthRequest
import com.ipification.mobile.sdk.ip.response.CoverageResponse
import com.ipification.mobile.sdk.ip.utils.DebugMetricContext
import com.ipification.mobile.sdk.ip.utils.DebugNetworkMetrics
import com.ipification.mobile.sdk.ip.utils.ErrorCode
import com.ipification.mobile.sdk.ip.utils.IPLogs
import com.ipification.mobile.sdk.ip.utils.LogUtils
import com.ipification.mobile.sdk.ip.utils.PrintingEventListener
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit

/** Executes an SDK request through an optional cellular network and parses its response. */
class CellularConnection<T>() {

    private lateinit var authRequest: AuthRequest
    private lateinit var context: Context
    private var callback: CellularCallback<T>? = null
    private var network: Network? = null

    private val configuration: IPConfiguration
        get() = IPConfiguration.getInstance()

    private val retryHandler = Handler(Looper.getMainLooper())

    constructor(
        request: AuthRequest,
        callback: CellularCallback<T>,
        network: Network?,
        context: Context
    ) : this() {
        authRequest = request
        this.callback = callback
        this.network = network
        this.context = context.applicationContext
    }

    /**
     * Executes the request and follows redirects until a final SDK response is available.
     *
     * @param requestUri URL to request.
     * @param isRedirect Whether this request follows an earlier redirect.
     * @param retryCount Number of retries already attempted.
     * @param maxRetries Maximum number of retry attempts.
     * @param retryDelayMs Delay before retrying a failed request.
     */
    fun makeConnection(
        requestUri: String,
        isRedirect: Boolean,
        retryCount: Int = 0,
        maxRetries: Int = configuration.MAX_RETRIES,
        retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS
    ) {
        log("request URL: $requestUri")
        configuration.currentUrl = requestUri

        val client = buildHttpClient(requestUri, retryCount)
        val request = buildRequest(requestUri, isRedirect)

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                handleResponse(response, requestUri)
            }

            override fun onFailure(call: Call, e: IOException) {
                handleFailure(
                    exception = e,
                    requestUri = requestUri,
                    isRedirect = isRedirect,
                    retryCount = retryCount,
                    maxRetries = maxRetries,
                    retryDelayMs = retryDelayMs
                )
            }
        })
    }

    /** Creates an OkHttp client configured for the request network and SDK options. */
    private fun buildHttpClient(requestUri: String, retryCount: Int): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addNetworkInterceptor(
                HandleRedirectInterceptor(
                    context,
                    requestUri,
                    authRequest.redirectUri.toString(),
                    authRequest.apiType != ApiType.OTHER
                )
            )

        if (network != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.socketFactory(network!!.socketFactory)
        }

        if (configuration.debug || configuration.dnsDebug) {
            builder.eventListenerFactory(PrintingEventListener.factory(context, network))
        }

        if (configuration.dnsDebug) {
            builder.addNetworkInterceptor(LoggingInterceptor())
        }

        if (configuration.enabledHandleCookie) {
            builder.cookieJar(cookieJar)
        }

        configureDns(builder, requestUri)
        configureTimeouts(builder, retryCount)
        builder.retryOnConnectionFailure(configuration.retryOnConnectionFailure)

        return builder.build()
    }

    /** Uses network-specific DNS when configured for this request. */
    private fun configureDns(builder: OkHttpClient.Builder, requestUri: String) {
        val requestNetwork = network
        val useNetworkDns = requestNetwork != null &&
            (!configuration.forceDNSForOnlyTelco || !isIpificationEndpoint(requestUri))

        if (useNetworkDns) {
            log("enable network specific DNS lookup")
            builder.dns(NetworkDns(requireNotNull(requestNetwork)))
        } else {
            log("use default DNS lookup")
        }
    }

    /** Applies configured timeouts, with shorter values after the first failure. */
    private fun configureTimeouts(builder: OkHttpClient.Builder, retryCount: Int) {
        val connectTimeout = if (retryCount >= 1) RETRY_TIMEOUT_MS else authRequest.connectTimeout
        val readTimeout = if (retryCount >= 1) RETRY_TIMEOUT_MS else authRequest.readTimeout

        log("connectTimeout $connectTimeout")
        log("readTimeout $readTimeout")
        builder.connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
        builder.readTimeout(readTimeout, TimeUnit.MILLISECONDS)
    }

    /** Builds an OkHttp request and includes custom headers only on the initial request. */
    private fun buildRequest(requestUri: String, isRedirect: Boolean): Request {
        val transport = when {
            network != null -> "cellular_okhttp"
            configuration.bindAppToCellularNetwork -> "bound_okhttp"
            else -> "okhttp"
        }
        val metricContext = DebugNetworkMetrics.createContext(
            request = authRequest,
            requestUrl = requestUri,
            isRedirect = isRedirect,
            transport = transport
        )
        val builder = Request.Builder()
            .url(requestUri)
            .tag(DebugMetricContext::class.java, metricContext)

        if (!isRedirect) {
            authRequest.headers?.forEach { (name, value) -> builder.header(name, value) }
        }

        return builder.build()
    }

    /** Follows non-final redirects or parses the final response. */
    private fun handleResponse(response: Response, requestUri: String) {
        val redirectLocation = resolveRedirectLocation(response)
        if (response.isRedirect &&
            redirectLocation != null &&
            !redirectLocation.startsWith(authRequest.redirectUri.toString())
        ) {
            log("redirect $redirectLocation")
            response.close()
            makeConnection(redirectLocation, true)
            return
        }

        try {
            parseResponse(response)
        } catch (exception: ClassCastException) {
            deliverError(
                CellularException().apply {
                    sdkErrorCode = ErrorCode.SERVER_RESPONSE_FAILED
                    httpCode = response.code
                    errorDescription = requestUri
                    this.exception =
                        Exception("Invalid callback type. (${exception.localizedMessage})")
                }
            )
        }
    }

    /** Resolves a redirect header against the response request URL. */
    private fun resolveRedirectLocation(response: Response): String? {
        val location = response.header(LOCATION_HEADER) ?: return null
        return response.request.url.resolve(location)?.toString() ?: location
    }

    /** Retries recoverable failures or delivers the final network error. */
    private fun handleFailure(
        exception: IOException,
        requestUri: String,
        isRedirect: Boolean,
        retryCount: Int,
        maxRetries: Int,
        retryDelayMs: Long
    ) {
        log("onFailure: ${exception.message}")

        if (exception is UnknownServiceException &&
            exception.message?.contains(CLEARTEXT_ERROR) == true
        ) {
            log("Not retrying due to cleartext policy violation")
            deliverError(
                CellularException().apply {
                    errorDescription = "Cleartext HTTP request not permitted: ${exception.message}"
                    sdkErrorCode = ErrorCode.NETWORK_RESPONSE_FAILED
                    this.exception = exception
                }
            )
            return
        }

        if (retryCount < maxRetries) {
            log("Retrying... (${retryCount + 1}/$maxRetries)")
            retryHandler.postDelayed({
                makeConnection(
                    requestUri = requestUri,
                    isRedirect = isRedirect,
                    retryCount = retryCount + 1,
                    maxRetries = maxRetries,
                    retryDelayMs = retryDelayMs
                )
            }, retryDelayMs)
            return
        }

        deliverError(
            CellularException().apply {
                errorDescription =
                    "onFailure - ${exception::class.java.simpleName} retriedCount $retryCount - " +
                        "${configuration.currentUrl} - ${exception.message}"
                sdkErrorCode = ErrorCode.NETWORK_RESPONSE_FAILED
                this.exception = exception
            }
        )
    }

    /** Parses a response according to the request API type. */
    private fun parseResponse(response: Response) {
        val responseBody = response.body.string()
        log("parseResponse with response.code: ${response.code} and responseBody: $responseBody")

        when {
            response.isSuccessful -> parseSuccessfulResponse(response, responseBody)
            response.code in REDIRECT_RESPONSE_CODES -> parseRedirectResponse(response)
            else -> deliverError(
                CellularException().apply {
                    exception = Exception(responseBody)
                    sdkErrorCode = ErrorCode.SERVER_RESPONSE_FAILED
                    httpCode = response.code
                }
            )
        }
    }

    /** Parses a successful response into the expected SDK response model. */
    private fun parseSuccessfulResponse(response: Response, responseBody: String) {
        when (authRequest.apiType) {
            ApiType.COVERAGE -> deliverResponse(CoverageResponse(response.code, responseBody))
            ApiType.AUTH -> parseSuccessfulAuthResponse(response, responseBody)
            else -> deliverResponse(ApiResponse(response.code, responseBody))
        }
    }

    /** Validates a successful authentication response before delivery. */
    private fun parseSuccessfulAuthResponse(response: Response, responseBody: String) {
        val authResponse = AuthApiResponse(response.code, responseBody, response.headers)
        if (authResponse.authorizationCode != null || authResponse.isImResponse) {
            deliverResponse(authResponse)
        } else {
            deliverError(
                CellularException().apply {
                    httpCode = response.code
                    sdkErrorCode = ErrorCode.SERVER_RESPONSE_FAILED
                    exception = Exception(authResponse.getErrorMessage())
                }
            )
        }
    }

    /** Parses a final redirect response as an authentication result. */
    private fun parseRedirectResponse(response: Response) {
        val authResponse = AuthApiResponse(
            response.code,
            response.header(LOCATION_HEADER) ?: "error=empty_location",
            response.headers
        )

        if (authResponse.authorizationCode != null) {
            deliverResponse(authResponse)
        } else {
            deliverError(
                CellularException().apply {
                    sdkErrorCode = ErrorCode.SERVER_RESPONSE_FAILED
                    httpCode = response.code
                    errorCode = authResponse.getErrorCode()
                    errorDescription = authResponse.getErrorDescription()
                    exception = Exception(authResponse.getErrorMessage())
                }
            )
        }
    }

    /** Delivers a response once and releases the callback reference before invocation. */
    @Suppress("UNCHECKED_CAST")
    private fun deliverResponse(response: Any) {
        val currentCallback = callback ?: return
        callback = null
        log("callbackSuccess: $response")
        try {
            currentCallback.onSuccess(response as T)
        } catch (exception: ClassCastException) {
            callback = currentCallback
            throw exception
        }
    }

    /** Delivers an error once and releases the callback reference before invocation. */
    private fun deliverError(error: CellularException) {
        val currentCallback = callback ?: return
        callback = null
        log("callbackFailed: ${error.getErrorMessage()}")
        currentCallback.onError(error)
    }

    /** Stores response cookies and supplies matching cookies to later redirects. */
    private val cookieJar: CookieJar = object : CookieJar {
        private val cookieStore = ArrayList<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieStore) {
                log("saveFromResponse --- start --- ${cookieStore.size}")
                for (cookie in cookies) {
                    if (configuration.extraDebug) {
                        log("cookie info: ${cookie.name} ${cookie.path} ${cookie.value} ${cookie.domain} -- ${url.host}")
                    }
                    cookieStore.removeAll {
                        it.name == cookie.name &&
                            it.domain == cookie.domain &&
                            it.path == cookie.path
                    }
                    cookieStore.add(cookie)
                }
                logCookies("saved cookie info", cookieStore)
                log("saveFromResponse --- end --- ${cookieStore.size}")
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return synchronized(cookieStore) {
                log("loadCookieForRequest --- start ---")
                logCookies("load cookie info", cookieStore)
                val matchingCookies = cookieStore.filter { it.matches(url) }
                logCookies("loadCookieForRequest - cookie", matchingCookies)
                log("loadCookieForRequest --- end --- ${matchingCookies.size}")
                matchingCookies
            }
        }
    }

    /** Logs cookie details only when extra debug logging is enabled. */
    private fun logCookies(prefix: String, cookies: List<Cookie>) {
        if (!configuration.extraDebug) return
        for (cookie in cookies) {
            log("$prefix: ${cookie.name} ${cookie.value} ${cookie.domain} ${cookie.path}")
        }
    }

    /** Returns whether the request targets IPification's production domain. */
    private fun isIpificationEndpoint(requestUri: String): Boolean {
        val host = runCatching { URI(requestUri).host?.lowercase() }.getOrNull() ?: return false
        val result = host == IPIFICATION_DOMAIN || host.endsWith(".$IPIFICATION_DOMAIN")
        log("isIpificationEndpoint - $result")
        return result
    }

    /** Appends a connection message to Logcat and the SDK debug log. */
    private fun log(message: String) {
        Log.d(LOG_TAG, message)
        if (configuration.debug) {
            IPLogs.getInstance().LOG += "$LOG_TAG: ${LogUtils.currentTimestamp()} - $message\n"
        }
    }

    private companion object {
        const val LOG_TAG = "CellularConnection"
        const val LOCATION_HEADER = "Location"
        const val CLEARTEXT_ERROR = "CLEARTEXT"
        const val IPIFICATION_DOMAIN = "ipification.com"
        const val DEFAULT_RETRY_DELAY_MS = 1_000L
        const val RETRY_TIMEOUT_MS = 5_000L
        val REDIRECT_RESPONSE_CODES = 300..310
    }
}
