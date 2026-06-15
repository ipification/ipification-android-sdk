package com.ipification.mobile.sdk.ip.common.response

/** Raw HTTP response shared by internal SDK operations. */
open class ApiResponse(
    /** HTTP status code returned by the server. */
    var statusCode: Int,

    /** Raw response body or redirect value returned by the server. */
    var responseData: String
)
