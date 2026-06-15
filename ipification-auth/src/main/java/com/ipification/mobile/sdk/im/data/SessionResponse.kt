package com.ipification.mobile.sdk.im.data

import com.ipification.mobile.sdk.ip.exception.CellularException
import com.ipification.mobile.sdk.ip.response.AuthApiResponse

/** Result returned after attempting to complete an IM session. */
internal data class SessionResponse(
    /** True when the session returned an authorization response. */
    val isSuccess: Boolean = false,

    /** Raw authorization response returned by the completion endpoint. */
    val response: AuthApiResponse?,

    /** Error returned when completion failed or is still pending. */
    val exception: CellularException?
)
