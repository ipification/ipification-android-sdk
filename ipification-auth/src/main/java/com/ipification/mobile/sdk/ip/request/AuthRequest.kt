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

    /** Custom parameters forwarded during TS43 token exchange. */
    internal var ts43TokenCustomParams: HashMap<String, String>? = null

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
        scope: String?
    ) : this() {
        this.apiType = apiType
        this.endpoint = endpoint
        this.queryParameters = queryParameters
        this.headers = headers
        this.ts43TokenCustomParams = ts43TokenCustomParams
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

        /** TS43 token parameters copied into the built request. */
        internal var ts43TokenCustomParams: HashMap<String, String>? = null

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

        /** Creates an immutable request snapshot from this builder. */
        fun build(): AuthRequest {
            return AuthRequest(
                apiType,
                endpoint,
                queryParameters,
                headers,
                ts43TokenCustomParams,
                readTimeout,
                connectTimeout,
                clientId,
                redirectUri,
                responseType,
                state,
                scope
            )
        }

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

        /**
         * Adds or replaces a TS43 token-exchange parameter.
         *
         */

        /** Adds or replaces a TS43 token-exchange parameter. */
        fun addTS43TokenCustomParam(key: String, value: String) {
            if (ts43TokenCustomParams == null) {
                ts43TokenCustomParams = HashMap()
            }
            ts43TokenCustomParams!![key] = value
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
                //https://github.com/bvantagelimited/ipification-android-sdk/issues/8
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
