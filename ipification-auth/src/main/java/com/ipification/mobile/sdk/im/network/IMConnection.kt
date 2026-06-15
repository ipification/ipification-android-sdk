package com.ipification.mobile.sdk.im.network

import android.content.Context
import android.net.Uri
import com.ipification.mobile.sdk.ip.common.internal.CellularCallback
import com.ipification.mobile.sdk.ip.common.response.ApiResponse
import com.ipification.mobile.sdk.ip.exception.CellularException
import com.ipification.mobile.sdk.ip.request.ApiType
import com.ipification.mobile.sdk.ip.request.AuthRequest
import com.ipification.mobile.sdk.ip.response.AuthApiResponse
import com.ipification.mobile.sdk.ip.response.CoverageResponse
import com.ipification.mobile.sdk.ip.response.RedirectResponse
import com.ipification.mobile.sdk.ip.utils.ErrorCode
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Executes IM requests through the device's default network without following redirects. */
internal class IMConnection<T>(
    private val followsRedirect: Boolean,
    private val context: Context,
    private val authRequest: AuthRequest,
    private val callback: CellularCallback<T>
) {
    /**
     * Returns redirects containing an IM session ID instead of treating them as final responses.
     */
    internal var forceCheckRedirect: Boolean = false

    /** Builds and asynchronously executes the IM request. */
    fun execute() {
        val requestUrl = runCatching { authRequest.toUri(context).toString() }
            .getOrElse {
                callback.onError(createException(message = "Unable to build IM request URL.", cause = it))
                return
            }

        val client = runCatching { buildClient() }
            .getOrElse {
                callback.onError(createException(message = "Unable to configure IM request.", cause = it))
                return
            }
        val request = runCatching { buildRequest(requestUrl) }
            .getOrElse {
                callback.onError(createException(message = "Invalid IM request URL.", cause = it))
                return
            }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(createException(message = e.localizedMessage, cause = e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    handleResponse(requestUrl, it)
                }
            }
        })
    }

    private fun buildClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(authRequest.connectTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(authRequest.readTimeout, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .get()
            .header(ACCEPT_HEADER, ANY_CONTENT)
            .apply {
                if (!followsRedirect) {
                    authRequest.headers?.forEach { (name, value) -> header(name, value) }
                }
            }
            .build()
    }

    private fun handleResponse(requestUrl: String, response: Response) {
        val body = response.body.string()
        val redirectUrl = response.header(LOCATION_HEADER)
            ?.let { resolveRedirectUrl(requestUrl, it) }

        when {
            response.isRedirect && redirectUrl == null -> {
                callback.onError(
                    createException(
                        statusCode = response.code,
                        message = "Redirect response is missing a valid Location header."
                    )
                )
            }

            response.isRedirect && shouldReturnRedirect(redirectUrl.orEmpty()) -> {
                deliverSuccess(
                    RedirectResponse(response.code, redirectUrl.orEmpty(), apiType())
                )
            }

            response.isSuccessful || response.isRedirect -> {
                val responseData = redirectUrl ?: body
                deliverSuccess(createResponse(response.code, responseData, response))
            }

            else -> {
                callback.onError(
                    createException(
                        statusCode = response.code,
                        message = body.takeIf(String::isNotBlank) ?: "HTTP ${response.code}"
                    )
                )
            }
        }
    }

    private fun shouldReturnRedirect(url: String): Boolean {
        return !isConfiguredFinalRedirect(url) &&
            (!url.contains(IM_SESSION_ID_PARAMETER) || forceCheckRedirect)
    }

    private fun isConfiguredFinalRedirect(url: String): Boolean {
        val configured = authRequest.redirectUri ?: return false
        val actual = Uri.parse(url)
        return configured.scheme.equals(actual.scheme, ignoreCase = true) &&
            configured.authority.equals(actual.authority, ignoreCase = true) &&
            configured.path == actual.path
    }

    private fun resolveRedirectUrl(requestUrl: String, location: String): String? {
        return runCatching {
            requestUrl.toHttpUrl().resolve(location)?.toString()
        }.getOrNull()
    }

    private fun createResponse(statusCode: Int, body: String, response: Response): ApiResponse {
        return when (apiType()) {
            ApiType.COVERAGE -> CoverageResponse(statusCode, body)
            ApiType.AUTH -> AuthApiResponse(statusCode, body, response.headers)
            ApiType.OTHER -> ApiResponse(statusCode, body)
        }
    }

    private fun apiType(): ApiType = authRequest.apiType ?: ApiType.OTHER

    @Suppress("UNCHECKED_CAST")
    private fun deliverSuccess(response: ApiResponse) {
        callback.onSuccess(response as T)
    }

    private fun createException(
        statusCode: Int = 0,
        message: String?,
        cause: Throwable? = null
    ): CellularException {
        return CellularException().apply {
            errorDescription = message
            httpCode = statusCode
            sdkErrorCode = ErrorCode.SERVER_RESPONSE_FAILED
            exception = when (cause) {
                null -> IOException(message)
                is Exception -> cause
                else -> IOException(message, cause)
            }
        }
    }

    private companion object {
        const val ACCEPT_HEADER = "Accept"
        const val ANY_CONTENT = "*/*"
        const val IM_SESSION_ID_PARAMETER = "imbox_session_id"
        const val LOCATION_HEADER = "Location"
    }
}
