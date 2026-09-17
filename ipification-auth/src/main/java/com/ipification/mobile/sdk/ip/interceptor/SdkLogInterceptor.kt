package com.ipification.mobile.sdk.ip.interceptor

import android.content.Context
import com.ipification.mobile.sdk.ip.IPConfiguration
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds client, SDK, host app, device, and optional carrier headers to error-report requests.
 *
 * The class name is retained for compatibility with existing integrations.
 */
class SdkLogInterceptor(context: Context) : Interceptor {

    private val context = context.applicationContext

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()

        SdkRequestHeaders.addSdkHeaders(requestBuilder, context)
        if (IPConfiguration.getInstance().errorReportEnableCarrierHeaders) {
            SdkRequestHeaders.addCarrierHeaders(requestBuilder, context)
        }

        return chain.proceed(requestBuilder.build())
    }
}
