package com.ipification.mobile.sdk.ip.response

/** Converts a successful raw authorization response to the public SDK response. */
internal fun AuthApiResponse.toIPAuthResponse(): IPAuthResponse? {
    val code = authorizationCode ?: return null
    return IPAuthResponse(
        code = code,
        state = state,
        fullResponse = responseData
    )
}
