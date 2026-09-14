package com.ipification.mobile.sdk.ip.request

import android.content.Context
import android.net.Uri
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.utils.DeviceUtils

/** Identifies how an [AuthRequest] response should be parsed. */
enum class ApiType {
    AUTH,
    COVERAGE,
    OTHER
}

/** @deprecated Use [ApiType]. */
@Deprecated("Use ApiType", ReplaceWith("ApiType"))
typealias API_TYPE = ApiType

/**
 * Request data used by IPification authentication and coverage services.
 *
 * Create partner requests with [Builder].
 */
class AuthRequest() {
    // Request routing

    /** Endpoint requested by this operation. */
    private var endpoint: Uri? = null

    /** API operation used to build and parse the request. */
    internal var apiType: ApiType? = ApiType.OTHER

    // Request values

    /** Query parameters appended to the endpoint. */
    internal var queryParameters: HashMap<String, String>? = null

    /** HTTP headers added to the request. */
    internal var headers: HashMap<String, String>? = null

    /** Custom parameters forwarded during TS43 token exchange (legacy view of [ts43Options].tokenParams). */
    internal var ts43TokenCustomParams: HashMap<String, String>? = null

    /** Custom parameters and headers applied to the IP channel. */
    var ipOptions: IPChannelOptions = IPChannelOptions.EMPTY
        internal set

    /** Custom parameters, headers and overrides applied to the TS.43 channel. */
    var ts43Options: TS43ChannelOptions = TS43ChannelOptions.EMPTY
        internal set

    /** Custom parameters and headers applied to the SMS channel. */
    var smsOptions: SMSChannelOptions = SMSChannelOptions.EMPTY
        internal set

    /** Client identifier sent with the request. */
    private var clientId: String = ""

    /** OAuth response type sent with authentication requests. */
    private var responseType: String? = null

    /** Redirect URI used to identify the final authentication response. */
    internal var redirectUri: Uri? = null

    /** OAuth scope requested by the operation. */
    internal var scope: String? = null

    /** OAuth state used to correlate the authentication response. */
    internal var state: String? = null

    // Request behavior

    /** Includes SIM MCC and MNC query parameters when available. */
    internal var includeSimOperatorParameters = true

    /** Response read timeout in milliseconds. */
    internal var readTimeout: Long = IPConfiguration.getInstance().AUTH_READ_TIMEOUT

    /** Connection timeout in milliseconds. */
    internal var connectTimeout: Long = IPConfiguration.getInstance().AUTH_CONNECT_TIMEOUT

    private constructor(
        apiType: ApiType?,
        endpoint: Uri?,
        queryParameters: HashMap<String, String>?,
        headers: HashMap<String, String>?,
        ts43TokenCustomParams: HashMap<String, String>?,
        readTimeout: Long,
        connectTimeout: Long,
        clientId: String,
        redirectUri: Uri?,
        responseType: String,
        state: String?,
        scope: String?,
        ipOptions: IPChannelOptions,
        ts43Options: TS43ChannelOptions,
        smsOptions: SMSChannelOptions
    ) : this() {
        this.apiType = apiType
        this.endpoint = endpoint
        this.queryParameters = queryParameters
        this.headers = headers
        this.ts43TokenCustomParams = ts43TokenCustomParams
        this.ipOptions = ipOptions
        this.ts43Options = ts43Options
        this.smsOptions = smsOptions
        this.readTimeout = readTimeout
        this.connectTimeout = connectTimeout
        this.clientId = clientId
        this.redirectUri = redirectUri
        this.responseType = responseType
        this.state = state
        this.scope = scope
    }

    /** Builds an [AuthRequest] with optional endpoint and OAuth parameters. */
    class Builder(private val endpoint: Uri? = null) {
        // Request values

        /** Query parameters copied into the built request. */
        internal var queryParameters: HashMap<String, String>? = null

        /** HTTP headers copied into the built request. */
        internal var headers: HashMap<String, String>? = null

        /** TS43 token parameters copied into the built request (legacy; see [ts43]). */
        internal var ts43TokenCustomParams: HashMap<String, String>? = null

        private var ipOptionsBuilder: IPChannelOptions.Builder? = null
        private var ts43OptionsBuilder: TS43ChannelOptions.Builder? = null
        private var smsOptionsBuilder: SMSChannelOptions.Builder? = null

        /** Client identifier sent with the request. */
        private var clientId: String = ""

        /** OAuth response type sent with authentication requests. */
        private var responseType: String = "code"

        /** Redirect URI used to identify the final response. */
        private var redirectUri: Uri? = null

        /** OAuth scope requested by the operation. */
        internal var scope: String? = IPConfiguration.getInstance().DEFAULT_SCOPE

        /** OAuth state used to correlate the response. */
        internal var state: String? = null

        // Request behavior

        /** Response read timeout in milliseconds. */
        var readTimeout: Long = IPConfiguration.getInstance().AUTH_READ_TIMEOUT

        /** Connection timeout in milliseconds. */
        var connectTimeout: Long = IPConfiguration.getInstance().AUTH_CONNECT_TIMEOUT

        /** API operation used to build and parse the request. */
        var apiType: ApiType? = ApiType.OTHER

        /**
         * Creates an immutable request snapshot from this builder.
         *
         * IP channel options are merged into the request query parameters and headers so the IP flow
         * keeps a single source of custom values; TS.43 and SMS options are kept separate because they
         * target different backend contracts.
         */
        fun build(): AuthRequest {
            val ipOptions = ipOptionsBuilder?.build() ?: IPChannelOptions.EMPTY
            val ts43Options = ts43OptionsBuilder?.build() ?: TS43ChannelOptions.EMPTY
            val smsOptions = smsOptionsBuilder?.build() ?: SMSChannelOptions.EMPTY

            val mergedQueryParameters = queryParameters?.let { HashMap(it) }
            val mergedHeaders = headers?.let { HashMap(it) }
            val finalQueryParameters = if (ipOptions.authParams.isEmpty()) {
                mergedQueryParameters
            } else {
                (mergedQueryParameters ?: HashMap()).apply { putAll(ipOptions.authParams) }
            }
            val finalHeaders = if (ipOptions.headers.isEmpty()) {
                mergedHeaders
            } else {
                (mergedHeaders ?: HashMap()).apply { putAll(ipOptions.headers) }
            }
            val legacyTokenParams = ts43Options.tokenParams
                .takeIf { it.isNotEmpty() }
                ?.let { HashMap(it) }

            return AuthRequest(
                apiType,
                endpoint,
                finalQueryParameters,
                finalHeaders,
                legacyTokenParams,
                readTimeout,
                connectTimeout,
                clientId,
                redirectUri,
                responseType,
                state,
                scope,
                ipOptions,
                ts43Options,
                smsOptions
            )
        }

        /** Configures custom parameters and headers for the IP channel. */
        fun ip(block: IPChannelOptions.Builder.() -> Unit): Builder {
            ipBuilder().apply(block)
            return this
        }

        /** Configures custom parameters, headers and overrides for the TS.43 channel. */
        fun ts43(block: TS43ChannelOptions.Builder.() -> Unit): Builder {
            ts43Builder().apply(block)
            return this
        }

        /** Configures custom parameters and headers for the SMS channel. */
        fun sms(block: SMSChannelOptions.Builder.() -> Unit): Builder {
            smsBuilder().apply(block)
            return this
        }

        /**
         * Applies the same custom values to every channel.
         *
         * Values are still validated against each channel's reserved keys. Use this only when a value is
         * meaningful for every backend contract; prefer [ip], [ts43] and [sms] for channel-specific data.
         */
        fun forAllChannels(block: ChannelOptions.CommonBuilder<*>.() -> Unit): Builder {
            ipBuilder().block()
            ts43Builder().block()
            smsBuilder().block()
            return this
        }

        /** Applies prebuilt IP channel options (Java-friendly alternative to [ip]). */
        fun setIPOptions(options: IPChannelOptions): Builder {
            ipBuilder()
                .putAllAuthParams(options.authParams)
                .putAllTokenParams(options.tokenParams)
                .putAllHeaders(options.headers)
            return this
        }

        /** Applies prebuilt TS.43 channel options (Java-friendly alternative to [ts43]). */
        fun setTS43Options(options: TS43ChannelOptions): Builder {
            ts43Builder()
                .putAllAuthParams(options.authParams)
                .putAllTokenParams(options.tokenParams)
                .putAllHeaders(options.headers)
            options.scope?.let { ts43Builder().setScope(it) }
            options.carrierHint?.let { ts43Builder().setCarrierHint(it) }
            return this
        }

        /** Applies prebuilt SMS channel options (Java-friendly alternative to [sms]). */
        fun setSMSOptions(options: SMSChannelOptions): Builder {
            smsBuilder()
                .putAllAuthParams(options.authParams)
                .putAllTokenParams(options.tokenParams)
                .putAllHeaders(options.headers)
            return this
        }

        private fun ipBuilder(): IPChannelOptions.Builder =
            ipOptionsBuilder ?: IPChannelOptions.Builder().also { ipOptionsBuilder = it }

        private fun ts43Builder(): TS43ChannelOptions.Builder =
            ts43OptionsBuilder ?: TS43ChannelOptions.Builder().also { ts43OptionsBuilder = it }

        private fun smsBuilder(): SMSChannelOptions.Builder =
            smsOptionsBuilder ?: SMSChannelOptions.Builder().also { smsOptionsBuilder = it }

        /** Sets the OAuth response type. */
        fun setResponseType(responseType: String): Builder {
            this.responseType = responseType
            return this
        }

        /** Sets the redirect URI for the authentication response. */
        fun setRedirectUri(redirectUri: Uri): Builder {
            this.redirectUri = redirectUri
            return this
        }

        /** Sets the OAuth state value. */
        fun setState(state: String?): Builder {
            this.state = state
            return this
        }

        /** Sets and normalizes the whitespace-separated OAuth scopes. */
        fun setScope(scope: String?): Builder {
            if (scope.isNullOrBlank()) {
                this.scope = ""
            } else {
                this.scope = scope.trim().split(Regex("\\s+")).joinToString(" ")
            }
            return this
        }

        /** Adds or replaces an HTTP header. */
        fun addHeader(key: String, value: String) {
            if (headers == null) {
                headers = HashMap()
            }
            headers!![key] = value
        }

        /** Adds or replaces a query parameter. */
        fun addQueryParam(key: String, value: String) {
            if (queryParameters == null) {
                queryParameters = HashMap()
            }
            queryParameters!![key] = value
        }

        /** Adds or replaces a TS43 token-exchange parameter. */
        @Deprecated(
            message = "Use ts43 { addTokenParam(key, value) } or setTS43Options(...).",
            replaceWith = ReplaceWith("ts43 { addTokenParam(key, value) }")
        )
        fun addTS43TokenCustomParam(key: String, value: String) {
            ts43Builder().addTokenParam(key, value)
        }

        /** Sets the client identifier. */
        fun setClientId(clientId: String): Builder {
            this.clientId = clientId
            return this
        }
    }

    /** Builds the final request URI using configured and custom parameters. */
    fun toUri(context: Context): Uri {
        val uriBuilder: Uri.Builder = endpoint!!.buildUpon()
        if(apiType == ApiType.AUTH && IPConfiguration.getInstance().customUrls == false){
            uriBuilder.appendQueryParameter("redirect_uri", redirectUri.toString())
            if(responseType != null && responseType != ""){
                uriBuilder.appendQueryParameter("response_type", responseType)
            }
            if(scope != null && scope != ""){
                uriBuilder.appendQueryParameter("scope", scope)
            }
            if(IPConfiguration.getInstance().CONSENT_ID_VALUE != ""){
                // #22
                if(queryParameters == null || queryParameters?.containsKey("consent_id") == false){
                    uriBuilder.appendQueryParameter("consent_id", IPConfiguration.getInstance().CONSENT_ID_VALUE)
                }
                if(queryParameters == null || queryParameters?.containsKey("consent_timestamp") == false){
                    val unixTime = System.currentTimeMillis() / 1000L
                    uriBuilder.appendQueryParameter("consent_timestamp", "$unixTime")
                }
            }


            var requestState = ""
            if(state != null && state != ""){
                requestState = state!!
                IPConfiguration.getInstance().currentState = requestState
                uriBuilder.appendQueryParameter("state", requestState)
            }
            else if(IPConfiguration.getInstance().automaticStateGenerationEnabled){
                requestState = IPConfiguration.getInstance().generateState()
                IPConfiguration.getInstance().currentState = requestState
                uriBuilder.appendQueryParameter("state", requestState)
            }

            if(queryParameters?.containsKey("state") == true){
                IPConfiguration.getInstance().currentState = queryParameters?.get("state") ?: ""
            }
        }
        if(apiType != ApiType.OTHER && IPConfiguration.getInstance().customUrls == false){
            uriBuilder.appendQueryParameter("client_id", clientId)
            val deviceUtil = DeviceUtils.getInstance(context)
            val activeSIMOperator = deviceUtil.activeSimOperator()
            if(activeSIMOperator.getMCC() != "" && activeSIMOperator.getMNC() != "" && includeSimOperatorParameters){
                uriBuilder.appendQueryParameter("mcc", activeSIMOperator.getMCC())
                uriBuilder.appendQueryParameter("mnc", activeSIMOperator.getMNC())
            }
        }

        if (queryParameters != null) {
            for ((key, value) in queryParameters!!) {
                //https://github.com/ipification/ipification-android-sdk/issues/8
                if(key == "login_hint" || key == "phone"){
                    val updatedLoginHint = value.replace("+","").removeWhitespaces()
                    IPConfiguration.getInstance().LOGIN_HINT = updatedLoginHint
                    uriBuilder.appendQueryParameter(key, updatedLoginHint)
                }else{
                    uriBuilder.appendQueryParameter(key, value)
                }

            }
        }
        return uriBuilder.build()
    }
}

/** Removes whitespace characters from a request parameter value. */
fun String.removeWhitespaces() = filterNot(Char::isWhitespace)
