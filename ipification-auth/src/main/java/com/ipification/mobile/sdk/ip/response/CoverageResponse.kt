package com.ipification.mobile.sdk.ip.response

import com.ipification.mobile.sdk.ip.common.response.ApiResponse
import org.json.JSONObject

/** Response returned by an IPification coverage request. */
class CoverageResponse(
    statusCode: Int,
    responseData: String
) : ApiResponse(statusCode, responseData) {

    /** Returns true when the current operator supports IPification. */
    fun isAvailable(): Boolean {
        return successfulJson()?.optBoolean(AVAILABLE_KEY, false) ?: false
    }

    /** Returns the operator code when provided by the coverage API. */
    fun getOperatorCode(): String? {
        return successfulJson()
            ?.optString(OPERATOR_CODE_KEY)
            ?.takeIf(String::isNotBlank)
    }

    /** Parses successful response data as JSON. */
    private fun successfulJson(): JSONObject? {
        if (statusCode !in 200..299) return null
        return runCatching { JSONObject(responseData) }.getOrNull()
    }

    private companion object {
        const val AVAILABLE_KEY = "available"
        const val OPERATOR_CODE_KEY = "operator_code"
    }
}
