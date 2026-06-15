package com.ipification.mobile.sdk.im.data

import android.content.Context
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.im.callback.RedirectDataCallback
import com.ipification.mobile.sdk.im.internal.callback.SessionDataCallback
import com.ipification.mobile.sdk.im.model.IMSession
import com.ipification.mobile.sdk.im.network.RedirectInterceptor
import com.ipification.mobile.sdk.im.util.SingleLiveEvent
import com.ipification.mobile.sdk.ip.exception.CellularException
import com.ipification.mobile.sdk.ip.response.AuthApiResponse
import com.ipification.mobile.sdk.ip.utils.ErrorCode
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/** Loads IM session-completion and provider-redirect data from the network. */
internal class SessionDataSource {

    /** Completes an IM session and posts its result once. */
    fun completeSession(context: Context, imInfo: IMSession): SingleLiveEvent<SessionResponse> {
        val result = SingleLiveEvent<SessionResponse>()
        callCompleteSession(imInfo, object : SessionDataCallback {
            override fun onSuccess(res: AuthApiResponse) {
                result.postValue(SessionResponse(res.authorizationCode != null, res, null))
            }

            override fun onError(error: CellularException) {
                result.postValue(SessionResponse(false, null, error))
            }
        })
        return result
    }

    /** Resolves a provider URL without automatically following its redirect. */
    fun getRedirectLink(context: Context, url: String): SingleLiveEvent<String> {
        val result = SingleLiveEvent<String>()
        callRedirectUrl(url, object : RedirectDataCallback {
            override fun onResponse(link: String) {
                result.postValue(link)
            }
        })
        return result
    }

    private fun callCompleteSession(imInfo: IMSession, callback: SessionDataCallback) {
        val url = buildCompleteSessionUrl(imInfo)
        if (url == null) {
            callback.onError(createError("IM session ID or completion URL is missing."))
            return
        }

        val clientBuilder = OkHttpClient.Builder()
        IPConfiguration.getInstance().REDIRECT_URI
            ?.toString()
            ?.takeIf(String::isNotBlank)
            ?.let { clientBuilder.addNetworkInterceptor(RedirectInterceptor(it)) }

        executeRequest(clientBuilder.build(), url,
            onResponse = { response, body ->
                handleCompleteSessionResponse(response, body, callback)
            },
            onFailure = callback::onError
        )
    }

    private fun callRedirectUrl(url: String, callback: RedirectDataCallback) {
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        executeRequest(client, url,
            onResponse = { response, body ->
                callback.onResponse(response.header(LOCATION_HEADER) ?: body)
            },
            onFailure = {
                callback.onResponse("")
            }
        )
    }

    private fun executeRequest(
        client: OkHttpClient,
        url: String,
        onResponse: (Response, String) -> Unit,
        onFailure: (CellularException) -> Unit
    ) {
        val request = runCatching { Request.Builder().url(url).build() }
            .getOrElse {
                val exception = it as? Exception
                    ?: IllegalArgumentException("Invalid request URL.", it)
                onFailure(createError("Invalid request URL.", exception))
                return
            }

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResponse(it, it.body.string())
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                onFailure(createError(e.localizedMessage, e))
            }
        })
    }

    private fun handleCompleteSessionResponse(
        response: Response,
        body: String,
        callback: SessionDataCallback
    ) {
        val sessionStatus = runCatching {
            JSONObject(body).optString(SESSION_STATUS_KEY).takeIf(String::isNotBlank)
        }.getOrNull()

        when {
            sessionStatus != null -> callback.onError(
                createError(sessionStatus, httpCode = response.code)
            )

            response.isSuccessful -> callback.onSuccess(
                AuthApiResponse(response.code, body, response.headers)
            )

            else -> callback.onError(createError(body, httpCode = response.code))
        }
    }

    private fun buildCompleteSessionUrl(imInfo: IMSession): String? {
        val baseUrl = imInfo.completeSessionUrl?.takeIf(String::isNotBlank) ?: return null
        val sessionId = imInfo.sessionId?.takeIf(String::isNotBlank) ?: return null
        return "${baseUrl.trimEnd('/')}/$sessionId"
    }

    private fun createError(
        description: String?,
        exception: Exception? = null,
        httpCode: Int? = null
    ): CellularException {
        return CellularException().apply {
            errorDescription = description
            this.exception = exception
            this.httpCode = httpCode
            sdkErrorCode = ErrorCode.SERVER_RESPONSE_FAILED
        }
    }

    private companion object {
        const val LOCATION_HEADER = "Location"
        const val SESSION_STATUS_KEY = "session_status"
    }
}
