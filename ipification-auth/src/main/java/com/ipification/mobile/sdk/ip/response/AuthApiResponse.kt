package com.ipification.mobile.sdk.ip.response

import android.net.Uri
import com.ipification.mobile.sdk.ip.common.response.ApiResponse
import okhttp3.Headers
import org.json.JSONObject

/**
 * Internal raw authorization response used to parse success, error, and header values before
 * converting a successful result to [IPAuthResponse].
 */
internal class AuthApiResponse(
    statusCode: Int,
    responseData: String,
    private val headers: Headers?
) : ApiResponse(statusCode, responseData) {

    // OAuth response values

    /** Authorization code returned in the redirect URI. */
    val authorizationCode: String?
        get() = queryParameter(CODE_KEY)

    /** OAuth state returned in the redirect URI. */
    val state: String?
        get() = queryParameter(STATE_KEY)

    // Error response values

    /** Returns the server error code from JSON or redirect parameters. */
    fun getErrorCode(): String {
        return jsonValue(ERROR_KEY)
            ?: queryParameter(ERROR_KEY)
            ?: responseData.takeUnless(String::isBlank)
            ?: ""
    }

    /** Returns the server error description from JSON or redirect parameters. */
    fun getErrorDescription(): String {
        return jsonValue(ERROR_DESCRIPTION_KEY)
            ?: queryParameter(ERROR_DESCRIPTION_KEY)
            ?: responseData.takeUnless(String::isBlank)
            ?: ""
    }

    /** Returns a readable error message from the response. */
    fun getErrorMessage(): String {
        val errorCode = jsonValue(ERROR_KEY) ?: queryParameter(ERROR_KEY)
        val description = jsonValue(ERROR_DESCRIPTION_KEY) ?: queryParameter(ERROR_DESCRIPTION_KEY)
        return when {
            !errorCode.isNullOrBlank() && !description.isNullOrBlank() -> "$errorCode $description"
            !description.isNullOrBlank() -> description
            !errorCode.isNullOrBlank() -> errorCode
            else -> responseData
        }
    }

    /** Returns a header value for internal response-specific parsers. */
    internal fun header(name: String): String? = headers?.get(name)

    /** Returns a query parameter when response data is a hierarchical URI. */
    private fun queryParameter(name: String): String? {
        return runCatching {
            Uri.parse(responseData)
                .takeIf(Uri::isHierarchical)
                ?.getQueryParameter(name)
                ?.takeIf(String::isNotBlank)
        }.getOrNull()
    }

    /** Returns a string value when response data is a JSON object. */
    private fun jsonValue(name: String): String? {
        return runCatching {
            JSONObject(responseData).optString(name).takeIf(String::isNotBlank)
        }.getOrNull()
    }

    private companion object {
        const val CODE_KEY = "code"
        const val STATE_KEY = "state"
        const val ERROR_KEY = "error"
        const val ERROR_DESCRIPTION_KEY = "error_description"
    }
}
