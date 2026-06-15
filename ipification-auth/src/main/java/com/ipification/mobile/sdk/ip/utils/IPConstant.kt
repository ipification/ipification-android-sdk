package com.ipification.mobile.sdk.ip.utils

import androidx.annotation.Keep

/** SDK-defined error codes returned by IP and IM operations. */
object ErrorCode {

    // Cellular network errors
    const val NETWORK_IS_NOT_ACTIVE = 1000
    const val EMPTY_PHONE_NUMBER = 1002
    const val EMPTY_CLIENT_ID = 1003
    const val EMPTY_REDIRECT_URI = 1004
    const val EMPTY_SCOPE = 1005
    const val UNSUPPORTED_VERSION = 1006
    const val EMPTY_COVERAGE_ENDPOINT = 1007
    const val EMPTY_LOGIN_HINT = 1008
    const val EMPTY_AUTH_ENDPOINT = 1009
    const val NETWORK_IS_UNAVAILABLE = 1010
    const val SIM_IS_UNAVAILABLE = 1011

    // IM errors
    const val EMPTY_IM_HEADER = 2001
    const val IM_FAILED = 2002
    const val IM_NO_NETWORK_ERROR = 2003
    const val EMPTY_IM_PRIORITY_APP_LIST = 2005

    // General response errors
    const val GENERAL_ERROR = 800
    const val NETWORK_RESPONSE_FAILED = 803
    const val SERVER_RESPONSE_FAILED = 804

    /** Legacy misspelling retained for source compatibility. */
    @Deprecated(
        message = "Use UNSUPPORTED_VERSION.",
        replaceWith = ReplaceWith("UNSUPPORTED_VERSION")
    )
    const val UNSUPPORT_VERSION = UNSUPPORTED_VERSION
}

/** Human-readable messages associated with SDK validation and network errors. */
object ErrorMessages {

    // Cellular network errors
    const val NETWORK_IS_UNAVAILABLE = "Your cellular network is not available"
    const val SIM_IS_NOT_ACTIVE = "Your cellular network is not available (SIM is not ready)."
    const val NETWORK_IS_NOT_ACTIVE = "Your cellular network is not active"
    const val NETWORK_IS_NOT_ACTIVE_AIRPLANE_MODE = "Your airplane mode is active."
    const val NETWORK_ERROR = "Failed to force to cellular network."
    const val NETWORK_TIMEOUT_ERROR = "Failed to force to cellular network. Timeout error."
    const val NETWORK_ERROR_REQUEST = "Failed to force to cellular network. Request error."

    // Request validation errors
    const val EMPTY_CLIENT_ID =
        "The client_id parameter is empty. Please review your initialization function."
    const val EMPTY_REDIRECT_URI =
        "The redirect_uri parameter is empty. Please review your initialization function."
    const val EMPTY_COVERAGE_ENDPOINT =
        "The Coverage endpoint is null. Please review your initialization function."
    const val EMPTY_AUTH_ENDPOINT =
        "The Auth endpoint is null. Please review your initialization function."
    const val EMPTY_PHONE_NUMBER = "The phoneNumber parameter cannot be empty."
    const val EMPTY_SCOPE = "The scope parameter cannot be empty."
    const val EMPTY_LOGIN_HINT =
        "The login_hint parameter cannot be empty when the scope is set to 'ip:phone_verify'."
    const val UNSUPPORTED_VERSION = "The SDK requires Android 5.0 (API level 21) or newer."

    // IM errors
    const val EMPTY_IM_HEADER =
        "The IM header is null. Please ensure that the necessary information is set."
    const val IM_NO_NETWORK_ERROR = "Your internet network is not active or not available."
    const val IM_ERROR_UNSUPPORTED = "UNSUPPORTED"
    const val EMPTY_IM_PRIORITY_APP_LIST =
        "IM_PRIORITY_APP_LIST cannot be empty. Please review your initialization function."

    // General response errors
    const val GENERAL_ERROR = "Something went wrong."
    const val EMPTY_RESPONSE_ERROR = "Something went wrong. Empty Response."

    /** Legacy misspelling retained for source compatibility. */
    @Deprecated(
        message = "Use UNSUPPORTED_VERSION.",
        replaceWith = ReplaceWith("UNSUPPORTED_VERSION")
    )
    const val UNSUPPORT_VERSION = UNSUPPORTED_VERSION
}

/**
 * Keys used by the legacy IM activity contract and IM response headers.
 *
 * New code should access constants directly, for example `IPConstant.IM_SESSION_ID`.
 */
class IPConstant private constructor() {

    @Deprecated("Use IPConstant.ERROR_MESSAGE.", ReplaceWith("IPConstant.ERROR_MESSAGE"))
    val ERROR_MESSAGE: String get() = Companion.ERROR_MESSAGE

    @Deprecated("Use IPConstant.IM_SESSION_ERROR.", ReplaceWith("IPConstant.IM_SESSION_ERROR"))
    val IM_SESSION_ERROR: String get() = Companion.IM_SESSION_ERROR

    @Deprecated(
        "Use IPConstant.IM_SESSION_COMPLETED_ERROR.",
        ReplaceWith("IPConstant.IM_SESSION_COMPLETED_ERROR")
    )
    val IM_SESSION_COMPLETED_ERROR: String get() = Companion.IM_SESSION_COMPLETED_ERROR

    @Deprecated(
        "Use IPConstant.IM_SESSION_NOT_FOUND_OR_EXPIRED.",
        ReplaceWith("IPConstant.IM_SESSION_NOT_FOUND_OR_EXPIRED")
    )
    val IM_SESSION_NOT_FOUND_OR_EXPIRED: String
        get() = Companion.IM_SESSION_NOT_FOUND_OR_EXPIRED

    @Deprecated("Use IPConstant.IP_RESPONSE_DATA.", ReplaceWith("IPConstant.IP_RESPONSE_DATA"))
    val IP_RESPONSE_DATA: String get() = Companion.IP_RESPONSE_DATA

    @Deprecated("Use IPConstant.IM_SESSION_ID.", ReplaceWith("IPConstant.IM_SESSION_ID"))
    val IM_SESSION_ID: String get() = Companion.IM_SESSION_ID

    @Deprecated("Use IPConstant.IM_WA_LINK.", ReplaceWith("IPConstant.IM_WA_LINK"))
    val IM_WA_LINK: String get() = Companion.IM_WA_LINK

    @Deprecated("Use IPConstant.IM_TELEGRAM_LINK.", ReplaceWith("IPConstant.IM_TELEGRAM_LINK"))
    val IM_TELEGRAM_LINK: String get() = Companion.IM_TELEGRAM_LINK

    @Deprecated("Use IPConstant.IM_VIBER_LINK.", ReplaceWith("IPConstant.IM_VIBER_LINK"))
    val IM_VIBER_LINK: String get() = Companion.IM_VIBER_LINK

    @Deprecated("Use IPConstant.IMBOX_ENDPOINT.", ReplaceWith("IPConstant.IMBOX_ENDPOINT"))
    val IMBOX_ENDPOINT: String get() = Companion.IMBOX_ENDPOINT

    @Keep
    companion object {
        /** Intent extra containing an IM error message. */
        const val ERROR_MESSAGE = "ERROR_MESSAGE"

        /** Generic IM session error identifier. */
        const val IM_SESSION_ERROR = "IM_SESSION_ERROR"

        /** Server error returned when an IM session was already completed. */
        const val IM_SESSION_COMPLETED_ERROR = "session_already_completed"

        /** Server error returned when an IM session is missing or expired. */
        const val IM_SESSION_NOT_FOUND_OR_EXPIRED = "session_not_found"

        /** Intent extra containing the raw IP authorization response. */
        const val IP_RESPONSE_DATA = "IP_RESPONSE_DATA"

        /** IM session ID intent extra and response-header name. */
        const val IM_SESSION_ID = "imbox_session_id"

        /** WhatsApp verification-link response-header name. */
        const val IM_WA_LINK = "wa_link"

        /** Telegram verification-link response-header name. */
        const val IM_TELEGRAM_LINK = "telegram_link"

        /** Viber verification-link response-header name. */
        const val IM_VIBER_LINK = "viber_link"

        /** IM session-completion endpoint response-header name. */
        const val IMBOX_ENDPOINT = "imbox_endpoint"

        private val instance = IPConstant()

        /** Returns the legacy instance-based constant holder. */
        @Deprecated("Access constants directly from IPConstant.")
        @JvmStatic
        fun getInstance(): IPConstant = instance
    }
}
