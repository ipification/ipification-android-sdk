package com.ipification.mobile.sdk.ip.request

/**
 * Per-channel custom request data.
 *
 * Every authentication channel talks to a different backend contract, so partner-specific values are
 * declared per channel and per stage instead of being broadcast to every channel:
 *
 * - [authParams] are sent with the channel's first request (IP `/auth` query string, TS.43 `/ts43/auth`
 *   body, SMS `/sms/auth` body).
 * - [tokenParams] are sent with the channel's token request (IP `IP_TOKEN_URL` form body, TS.43
 *   `/ts43/token` body, SMS `/sms/token` body).
 * - [headers] are added to every HTTP request of that channel.
 *
 * Keys that the SDK itself sets (for example `client_id` or `login_hint`) are reserved and rejected by the
 * builders with [IllegalArgumentException] so that misconfiguration fails fast during development.
 */
abstract class ChannelOptions internal constructor(
    /** Parameters added to the channel's auth request. */
    val authParams: Map<String, String>,

    /** Parameters added to the channel's token request. */
    val tokenParams: Map<String, String>,

    /** HTTP headers added to every request of the channel. */
    val headers: Map<String, String>
) {
    /** Returns true when no custom value was configured. */
    fun isEmpty(): Boolean = authParams.isEmpty() && tokenParams.isEmpty() && headers.isEmpty()

    /** Returns true when at least one custom value was configured. */
    fun isNotEmpty(): Boolean = !isEmpty()

    /** Common builder contract shared by every channel so callers can configure several channels at once. */
    interface CommonBuilder<B : CommonBuilder<B>> {
        /** Adds or replaces a parameter sent with the channel's auth request. */
        fun addAuthParam(key: String, value: String): B

        /** Adds or replaces a parameter sent with the channel's token request. */
        fun addTokenParam(key: String, value: String): B

        /** Adds or replaces an HTTP header sent with every request of the channel. */
        fun addHeader(key: String, value: String): B
    }

    /** Shared builder implementation; only SDK channel builders can extend it. */
    abstract class BaseBuilder<B : BaseBuilder<B>> internal constructor(
        private val channelName: String,
        private val reservedAuthKeys: Set<String>,
        private val reservedTokenKeys: Set<String>
    ) : CommonBuilder<B> {
        protected val authParams: LinkedHashMap<String, String> = LinkedHashMap()
        protected val tokenParams: LinkedHashMap<String, String> = LinkedHashMap()
        protected val headers: LinkedHashMap<String, String> = LinkedHashMap()

        @Suppress("UNCHECKED_CAST")
        protected fun self(): B = this as B

        override fun addAuthParam(key: String, value: String): B {
            requireCustomKey(key, reservedAuthKeys, "auth")
            authParams[key] = value
            return self()
        }

        override fun addTokenParam(key: String, value: String): B {
            requireCustomKey(key, reservedTokenKeys, "token")
            tokenParams[key] = value
            return self()
        }

        override fun addHeader(key: String, value: String): B {
            require(key.isNotBlank()) { "$channelName header name cannot be blank" }
            headers[key] = value
            return self()
        }

        internal fun putAllAuthParams(params: Map<String, String>?): B {
            params?.forEach { (key, value) -> addAuthParam(key, value) }
            return self()
        }

        internal fun putAllTokenParams(params: Map<String, String>?): B {
            params?.forEach { (key, value) -> addTokenParam(key, value) }
            return self()
        }

        internal fun putAllHeaders(values: Map<String, String>?): B {
            values?.forEach { (key, value) -> addHeader(key, value) }
            return self()
        }

        private fun requireCustomKey(key: String, reserved: Set<String>, stage: String) {
            require(key.isNotBlank()) { "$channelName $stage parameter name cannot be blank" }
            require(key !in reserved) {
                "\"$key\" is a reserved key for the $channelName $stage request and is set by the SDK; " +
                    "configure it through IPConfiguration or AuthRequest instead."
            }
        }
    }
}

/** Custom data for the IPification IP channel. */
class IPChannelOptions private constructor(
    authParams: Map<String, String>,
    tokenParams: Map<String, String>,
    headers: Map<String, String>
) : ChannelOptions(authParams, tokenParams, headers) {

    /** Builds [IPChannelOptions]. */
    class Builder : ChannelOptions.BaseBuilder<Builder>("IP", RESERVED_AUTH_KEYS, RESERVED_TOKEN_KEYS) {
        /** Creates an immutable snapshot of the configured values. */
        fun build(): IPChannelOptions = IPChannelOptions(
            authParams.toMap(),
            tokenParams.toMap(),
            headers.toMap()
        )
    }

    companion object {
        /** Keys the SDK sets on the IP authorization request. */
        @JvmField
        val RESERVED_AUTH_KEYS: Set<String> = setOf(
            "client_id", "redirect_uri", "response_type", "scope", "state", "login_hint", "mcc", "mnc"
        )

        /** Keys the SDK sets on the IP token exchange request. */
        @JvmField
        val RESERVED_TOKEN_KEYS: Set<String> = setOf("grant_type", "client_id", "redirect_uri", "code")

        /** Options without custom values. Declared after the reserved-key sets it depends on. */
        @JvmField
        val EMPTY: IPChannelOptions = Builder().build()
    }
}

/** Custom data for the TS.43 channel. */
class TS43ChannelOptions private constructor(
    authParams: Map<String, String>,
    tokenParams: Map<String, String>,
    headers: Map<String, String>,

    /** Optional scope override for TS.43 requests; null uses the operation default from [com.ipification.mobile.sdk.ip.IPConfiguration]. */
    val scope: String?,

    /** Optional carrier hint (MCC+MNC) override; null uses the SDK-detected or configured value. */
    val carrierHint: String?
) : ChannelOptions(authParams, tokenParams, headers) {

    /** Builds [TS43ChannelOptions]. */
    class Builder : ChannelOptions.BaseBuilder<Builder>("TS43", RESERVED_AUTH_KEYS, RESERVED_TOKEN_KEYS) {
        private var scope: String? = null
        private var carrierHint: String? = null

        /** Overrides the scope used by TS.43 requests. */
        fun setScope(scope: String?): Builder {
            this.scope = scope?.trim()?.takeIf(String::isNotEmpty)
            return this
        }

        /** Overrides the carrier hint (MCC+MNC) used by TS.43 requests. */
        fun setCarrierHint(mccMnc: String?): Builder {
            this.carrierHint = mccMnc?.trim()?.takeIf(String::isNotEmpty)
            return this
        }

        /** Creates an immutable snapshot of the configured values. */
        fun build(): TS43ChannelOptions = TS43ChannelOptions(
            authParams.toMap(),
            tokenParams.toMap(),
            headers.toMap(),
            scope,
            carrierHint
        )
    }

    companion object {
        /** Keys the SDK sets on the `/ts43/auth` request body. */
        @JvmField
        val RESERVED_AUTH_KEYS: Set<String> = setOf(
            "login_hint", "carrier_hint", "client_id", "scope", "operation"
        )

        /** Keys the SDK sets on the `/ts43/token` request body. */
        @JvmField
        val RESERVED_TOKEN_KEYS: Set<String> = setOf("vp_token", "auth_req_id", "client_id")

        /** Options without custom values. Declared after the reserved-key sets it depends on. */
        @JvmField
        val EMPTY: TS43ChannelOptions = Builder().build()
    }
}

/** Custom data for the SMS OTP channel. */
class SMSChannelOptions private constructor(
    authParams: Map<String, String>,
    tokenParams: Map<String, String>,
    headers: Map<String, String>
) : ChannelOptions(authParams, tokenParams, headers) {

    /** Builds [SMSChannelOptions]. */
    class Builder : ChannelOptions.BaseBuilder<Builder>("SMS", RESERVED_AUTH_KEYS, RESERVED_TOKEN_KEYS) {
        /** Creates an immutable snapshot of the configured values. */
        fun build(): SMSChannelOptions = SMSChannelOptions(
            authParams.toMap(),
            tokenParams.toMap(),
            headers.toMap()
        )
    }

    companion object {
        /** Keys the SDK sets on the `/sms/auth` request body. */
        @JvmField
        val RESERVED_AUTH_KEYS: Set<String> = setOf("client_id", "login_hint", "scope", "channel")

        /** Keys the SDK sets on the `/sms/token` request body. */
        @JvmField
        val RESERVED_TOKEN_KEYS: Set<String> = setOf("code", "auth_req_id", "client_id", "nonce")

        /** Options without custom values. Declared after the reserved-key sets it depends on. */
        @JvmField
        val EMPTY: SMSChannelOptions = Builder().build()
    }
}
