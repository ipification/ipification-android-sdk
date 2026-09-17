package com.ipification.mobile.sdk.ip.interceptor

import android.content.Context
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
        val requestBuilder = chain.request().newBuilder()
        SdkRequestHeaders.addSdkHeaders(requestBuilder, context)
        SdkRequestHeaders.addCarrierHeaders(requestBuilder, context)
        return chain.proceed(requestBuilder.build())
    }
}
