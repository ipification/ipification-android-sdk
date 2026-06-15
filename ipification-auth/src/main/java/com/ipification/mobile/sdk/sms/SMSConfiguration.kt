package com.ipification.mobile.sdk.sms

import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.IPEnvironment

/**
 * Configuration class for SMS verification services.
 *
 * **Note:** This class is deprecated. Please use [IPConfiguration] directly instead.
 * This class is maintained for backward compatibility and delegates all operations to [IPConfiguration].
 *
 * ## Migration Guide
 *
 * **Old way (deprecated):**
 * ```kotlin
 * val config = SMSConfiguration.getInstance()
 * config.setBackendUrlSandbox("https://stage.your-backend.com")
 * ```
 *
 * **New way (recommended):**
 * ```kotlin
 * IPConfiguration.getInstance().apply {
 *     SMS_BACKEND_URL_SANDBOX = "https://stage.your-backend.com"
 *     SMS_BACKEND_URL_PRODUCTION = "https://your-backend.com"
 *     SMS_AUTH_PATH = "/sms/auth"
 *     SMS_TOKEN_PATH = "/sms/token"
 * }
 * ```
 *
 * @deprecated Use [IPConfiguration] directly with SMS_* properties
 */
@Deprecated(
    message = "Use IPConfiguration directly with SMS_* properties",
    replaceWith = ReplaceWith("IPConfiguration.getInstance()"),
    level = DeprecationLevel.WARNING
)
class SMSConfiguration private constructor() {

    companion object {
        @Suppress("DEPRECATION")
        private val INSTANCE = SMSConfiguration()

        /** Returns the deprecated SMS configuration facade. */
        @Suppress("DEPRECATION")
        @JvmStatic
        fun getInstance(): SMSConfiguration {
            return INSTANCE
        }
    }

    private val ipConfig = IPConfiguration.getInstance()

    /** Sandbox backend base URL for SMS auth and token operations. */
    var backendUrlSandbox: String
        get() = ipConfig.SMS_BACKEND_URL_SANDBOX
        set(value) { ipConfig.SMS_BACKEND_URL_SANDBOX = value }

    /** Production backend base URL for SMS auth and token operations. */
    var backendUrlProduction: String
        get() = ipConfig.SMS_BACKEND_URL_PRODUCTION
        set(value) { ipConfig.SMS_BACKEND_URL_PRODUCTION = value }

    /** Endpoint path used to start SMS verification. */
    var authPath: String
        get() = ipConfig.SMS_AUTH_PATH
        set(value) { ipConfig.SMS_AUTH_PATH = value }

    /** Endpoint path used to verify OTP and exchange the SMS token. */
    var tokenPath: String
        get() = ipConfig.SMS_TOKEN_PATH
        set(value) { ipConfig.SMS_TOKEN_PATH = value }

    /** OAuth client ID shared with [IPConfiguration]. */
    var clientId: String
        get() = ipConfig.CLIENT_ID
        set(value) { ipConfig.CLIENT_ID = value }

    /** Default OAuth scope used for SMS phone verification. */
    var scope: String
        get() = ipConfig.SMS_SCOPE_VERIFY_PHONE
        set(value) { ipConfig.SMS_SCOPE_VERIFY_PHONE = value }

    /** Current SDK environment used to resolve the SMS backend URL. */
    var environment: IPEnvironment
        get() = ipConfig.ENV
        set(value) { ipConfig.ENV = value }

    /** Enables or disables SDK debug logging. */
    var debug: Boolean
        get() = ipConfig.debug
        set(value) { ipConfig.debug = value }

    /**
     * Get the resolved backend URL based on current environment.
     */
    fun getBackendUrl(): String {
        return ipConfig.getSMSBackendUrl()
    }

    /**
     * Get the full auth endpoint URL.
     */
    fun getAuthEndpoint(): String {
        return "${getBackendUrl()}${authPath}"
    }

    /**
     * Get the full token endpoint URL.
     */
    fun getTokenEndpoint(): String {
        return "${getBackendUrl()}${tokenPath}"
    }

    /**
     * Check if SMS is properly configured.
     */
    fun isConfigured(): Boolean {
        return SMSServices.isConfigured()
    }
}
