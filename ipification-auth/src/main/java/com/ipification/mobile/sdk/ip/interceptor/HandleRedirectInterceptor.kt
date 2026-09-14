package com.ipification.mobile.sdk.ip.interceptor

import android.net.Uri
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.utils.IPConstant
import com.ipification.mobile.sdk.ip.utils.IPLogs
import com.ipification.mobile.sdk.ip.utils.LogUtils
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Converts the terminal redirect of the IP authorization flow into a successful response.
 *
 * A redirect is terminal when its `Location` targets the configured redirect URI (compared by
 * scheme, authority and path so that look-alike callbacks are rejected) or when the response carries
 * an IM session header. Intermediate redirects are returned unchanged so OkHttp keeps following them
 * on the same cellular-bound client. The original headers, including `Location` and the IM session
 * headers, are preserved because later parsing depends on them.
 *
 * @param redirectUri Configured redirect URI, or null when the request has none (coverage checks).
 */
class HandleRedirectInterceptor(redirectUri: String?) : Interceptor {

    private val expectedRedirectUri: Uri? = redirectUri
        ?.takeIf { it.isNotBlank() && it != "null" }
        ?.let(Uri::parse)
        ?.takeIf { !it.scheme.isNullOrBlank() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        // OkHttp header lookup is case-insensitive, so one lookup handles Location/location.
        val location = response.header(LOCATION_HEADER)
        IPConfiguration.getInstance().currentUrl = location.orEmpty()

        if (!isTerminal(response, location)) {
            return response
        }

        log("matched terminal redirect - converting to 200")
        val terminalResponse = response.newBuilder()
            .code(200)
            .message("success")
            // The replacement body is plain text; drop transfer metadata that described the old body.
            .removeHeader("Content-Encoding")
            .removeHeader("Content-Length")
            .body(location.orEmpty().toResponseBody(TEXT_PLAIN))
            .build()

        // The original body is no longer returned and must be closed to avoid leaking a socket.
        response.close()
        return terminalResponse
    }

    /** Terminal when the redirect targets the configured redirect URI or carries an IM session. */
    private fun isTerminal(response: Response, location: String?): Boolean {
        if (response.code !in 300..399) return false
        if (response.header(IPConstant.IM_SESSION_ID) != null) return true
        return location != null && matchesRedirectUri(location)
    }

    /**
     * Compares URI components instead of using startsWith(), which would accept a different callback
     * such as "myapp://callback.attacker". Query parameters are ignored because the terminal URI adds
     * dynamic values such as `code` and `state`.
     */
    private fun matchesRedirectUri(location: String): Boolean {
        val expected = expectedRedirectUri ?: return false
        val actual = runCatching { Uri.parse(location) }.getOrNull() ?: return false

        if (!actual.scheme.equals(expected.scheme, ignoreCase = true)) return false

        if (actual.isOpaque || expected.isOpaque) {
            return actual.isOpaque == expected.isOpaque &&
                actual.schemeSpecificPart.substringBefore('?').trimEnd('/') ==
                    expected.schemeSpecificPart.substringBefore('?').trimEnd('/')
        }

        return actual.authority.orEmpty().equals(expected.authority.orEmpty(), ignoreCase = true) &&
            actual.path.orEmpty().trimEnd('/') == expected.path.orEmpty().trimEnd('/')
    }

    /** Appends an interceptor message to the SDK debug log. */
    private fun log(message: String) {
        if (IPConfiguration.getInstance().debug) {
            IPLogs.getInstance().LOG +=
                "${LogUtils.currentTimestamp()} - HandleRedirectInterceptor - $message\n"
        }
    }

    private companion object {
        const val LOCATION_HEADER = "Location"
        val TEXT_PLAIN = "text/plain; charset=utf-8".toMediaType()
    }
}
