package com.ipification.mobile.sdk.ip.interceptor

import android.content.Context
import com.ipification.mobile.sdk.ip.utils.IPLogs
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the SDK, host app, device and carrier headers to TS.43 and SMS backend requests, so those
 * channels report the same device information as the IP channel and error reports.
 *
 * Partner headers set on the request are kept: they are applied before this interceptor runs and
 * OkHttp keeps both values if a name collides.
 */
internal class DeviceHeadersInterceptor(context: Context) : Interceptor {

    private val context = context.applicationContext

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = runCatching {
            val requestBuilder = chain.request().newBuilder()
            SdkRequestHeaders.addSdkHeaders(requestBuilder, context)
            SdkRequestHeaders.addCarrierHeaders(requestBuilder, context)
            requestBuilder.build()
        }.getOrElse { e ->
            // Header collection must never break the TS.43 / SMS request itself: on any failure
            // (telephony restrictions, an invalid header value, ...) send the original request.
            IPLogs.getInstance().LOG +=
                "DeviceHeadersInterceptor - skipped device headers: ${e.message}\n"
            chain.request()
        }
        return chain.proceed(request)
    }
}
