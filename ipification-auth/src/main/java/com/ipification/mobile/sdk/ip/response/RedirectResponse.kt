package com.ipification.mobile.sdk.ip.response

import com.ipification.mobile.sdk.ip.request.ApiType
import com.ipification.mobile.sdk.ip.common.response.ApiResponse

/** Internal redirect response that retains the originating API type. */
class RedirectResponse(statusCode: Int, responseData: String, var apiType: ApiType) :
    ApiResponse(statusCode, responseData) {
    /** Returns the redirect URL. */
    fun getUrl(): String {
        return responseData
    }
}
