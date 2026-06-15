package com.ipification.mobile.sdk.ip.response

import com.ipification.mobile.sdk.ts43.response.TS43TokenResponse

/** Successful response returned by an authentication channel. */
data class IPAuthResponse(
    /** Authorization code returned by IP authentication. */
    var code: String,

    /** OAuth state returned with the authentication response. */
    var state: String?,

    /** Raw authorization or token response returned by the channel. */
    var fullResponse: String,

    /** Parsed TS43 token response when TS43 completed authentication. */
    var ts43TokenResponse: TS43TokenResponse? = null
)
