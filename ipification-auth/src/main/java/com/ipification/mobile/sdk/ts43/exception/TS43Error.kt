package com.ipification.mobile.sdk.ts43.exception

/** SDK error codes produced by TS.43 authentication operations. */
object TS43ErrorCode {

    // Configuration and device-support errors
    const val CONFIGURATION_ERROR = 4301
    const val MISSING_CLIENT_ID = 4302
    const val MISSING_PHONE_NUMBER = 4303
    const val MISSING_TS43_ENDPOINT = 4304
    const val MISSING_CREDENTIAL_MANAGER = 4305
    const val TS43_NOT_SUPPORTED_IN_MULTI_CHANNEL = 4306
    const val ANDROID_GO_NOT_SUPPORTED = 4307

    // Authorization request errors
    const val AUTH_REQUEST_FAILED = 4310

    // Credential Manager errors
    const val CREDENTIAL_MANAGER_ERROR = 4320
    const val CREDENTIAL_MANAGER_FAILED = 4321
    const val CREDENTIAL_CANCELLED = 4322
    const val CREDENTIAL_INTERRUPTED = 4323
    const val NO_CREDENTIAL_AVAILABLE = 4324
    const val VP_TOKEN_EXTRACTION_FAILED = 4325
    const val CREDENTIAL_RESPONSE_INVALID = 4326
    const val CREDENTIAL_TYPE_MISMATCH = 4327

    // Token exchange errors
    const val TOKEN_EXCHANGE_FAILED = 4330

    // Unclassified TS.43 errors
    const val UNKNOWN_ERROR = 4399
}

/** Standard descriptions associated with [TS43ErrorCode] values. */
object TS43ErrorMessage {

    // Configuration and device-support errors
    const val CONFIGURATION_ERROR = "TS43 configuration is invalid"
    const val MISSING_CLIENT_ID = "TS43: missing CLIENT_ID"
    const val MISSING_PHONE_NUMBER = "TS43: missing phone number (login_hint)"
    const val MISSING_ENDPOINT = "TS43: endpoint not configured"
    const val MISSING_CREDENTIAL_MANAGER = "TS43: Credential Manager not available"
    const val NOT_SUPPORTED_IN_MULTI_CHANNEL = "TS43: multi-channel authentication is not supported"
    const val ANDROID_GO_NOT_SUPPORTED = "TS43: Android Go devices are not supported"

    // Authorization request errors
    const val AUTH_REQUEST_FAILED = "TS43: auth request failed (/ts43/auth)"

    // Credential Manager errors
    const val CREDENTIAL_MANAGER_ERROR = "TS43: Credential Manager error"
    const val CREDENTIAL_MANAGER_FAILED = "TS43: Credential Manager request failed"
    const val CREDENTIAL_CANCELLED = "TS43: user cancelled"
    const val CREDENTIAL_INTERRUPTED = "TS43: credential flow interrupted"
    const val NO_CREDENTIAL_AVAILABLE = "TS43: no credential available"
    const val VP_TOKEN_EXTRACTION_FAILED = "TS43: failed to extract vp_token"
    const val CREDENTIAL_RESPONSE_INVALID = "TS43: credential response is invalid"
    const val CREDENTIAL_TYPE_MISMATCH = "TS43: credential type does not match"

    // Token exchange errors
    const val TOKEN_EXCHANGE_FAILED = "TS43: token exchange failed (/ts43/token)"

    // Unclassified TS.43 errors
    const val UNKNOWN_ERROR = "TS43: unknown error"

    /** Returns the standard description for [errorCode]. */
    fun fromCode(errorCode: Int): String {
        return when (errorCode) {
            TS43ErrorCode.CONFIGURATION_ERROR -> CONFIGURATION_ERROR
            TS43ErrorCode.MISSING_CLIENT_ID -> MISSING_CLIENT_ID
            TS43ErrorCode.MISSING_PHONE_NUMBER -> MISSING_PHONE_NUMBER
            TS43ErrorCode.MISSING_TS43_ENDPOINT -> MISSING_ENDPOINT
            TS43ErrorCode.MISSING_CREDENTIAL_MANAGER -> MISSING_CREDENTIAL_MANAGER
            TS43ErrorCode.TS43_NOT_SUPPORTED_IN_MULTI_CHANNEL -> NOT_SUPPORTED_IN_MULTI_CHANNEL
            TS43ErrorCode.ANDROID_GO_NOT_SUPPORTED -> ANDROID_GO_NOT_SUPPORTED
            TS43ErrorCode.AUTH_REQUEST_FAILED -> AUTH_REQUEST_FAILED
            TS43ErrorCode.CREDENTIAL_MANAGER_ERROR -> CREDENTIAL_MANAGER_ERROR
            TS43ErrorCode.CREDENTIAL_MANAGER_FAILED -> CREDENTIAL_MANAGER_FAILED
            TS43ErrorCode.CREDENTIAL_CANCELLED -> CREDENTIAL_CANCELLED
            TS43ErrorCode.CREDENTIAL_INTERRUPTED -> CREDENTIAL_INTERRUPTED
            TS43ErrorCode.NO_CREDENTIAL_AVAILABLE -> NO_CREDENTIAL_AVAILABLE
            TS43ErrorCode.VP_TOKEN_EXTRACTION_FAILED -> VP_TOKEN_EXTRACTION_FAILED
            TS43ErrorCode.CREDENTIAL_RESPONSE_INVALID -> CREDENTIAL_RESPONSE_INVALID
            TS43ErrorCode.CREDENTIAL_TYPE_MISMATCH -> CREDENTIAL_TYPE_MISMATCH
            TS43ErrorCode.TOKEN_EXCHANGE_FAILED -> TOKEN_EXCHANGE_FAILED
            else -> UNKNOWN_ERROR
        }
    }
}
