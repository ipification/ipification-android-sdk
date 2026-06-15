package com.ipification.mobile.sdk.im.network

import android.net.Uri
import com.ipification.mobile.sdk.ip.utils.debug
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Converts a matching IM completion redirect into a successful response containing its location.
 */
internal class RedirectInterceptor(
    private val redirectUri: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val location = response.header(LOCATION_HEADER)

        if (!response.isRedirect || location == null || !isConfiguredRedirect(location)) {
            return response
        }

        debug("Matched IM completion redirect")
        val contentType = response.body.contentType()
        response.body.close()

        return response.newBuilder()
            .code(SUCCESS_CODE)
            .message(SUCCESS_MESSAGE)
            .removeHeader(CONTENT_LENGTH_HEADER)
            .body(location.toResponseBody(contentType))
            .build()
    }

    /** Matches the configured redirect destination while allowing returned query parameters. */
    private fun isConfiguredRedirect(location: String): Boolean {
        val configured = Uri.parse(redirectUri)
        val actual = Uri.parse(location)
        return configured.scheme.equals(actual.scheme, ignoreCase = true) &&
            configured.authority.equals(actual.authority, ignoreCase = true) &&
            configured.path == actual.path
    }

    private companion object {
        const val CONTENT_LENGTH_HEADER = "Content-Length"
        const val LOCATION_HEADER = "Location"
        const val SUCCESS_CODE = 200
        const val SUCCESS_MESSAGE = "success"
    }
}
