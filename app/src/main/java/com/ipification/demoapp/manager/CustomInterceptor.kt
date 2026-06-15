package com.ipification.demoapp.manager
//
import com.ipification.demoapp.util.Util
import com.ipification.mobile.sdk.ip.utils.IPLogs
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID
//
class CustomInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        request = request.newBuilder()
                .build()
        val t1 = System.nanoTime()
        IPLogs.getInstance().LOG += (
            java.lang.String.format(
                "--> Sending request %s on %s\n",
                request.url,
                Util.getCurrentDate()
            )
        )
        val response = chain.proceed(request)
        val t2 = System.nanoTime()
        IPLogs.getInstance().LOG += (
            String.format(
                "<-- Received response for %s in %.1fms%n%s",
                response.request.url,
                (t2 - t1) / 1e6,
                response.headers
            )
        )
        logNetworkMetricIfNeeded(request, response)
        return response
    }

    private fun logNetworkMetricIfNeeded(
        request: okhttp3.Request,
        response: Response
    ) {
        val stage = when {
            request.url.encodedPath.endsWith("/auth/mobile/login") -> "token_exchange"
            else -> null
        } ?: return

        val requestHeadersBytes = request.headers.toString().toByteArray().size.toLong()
        val requestBodyBytes = request.body?.contentLength()?.takeIf { it >= 0 } ?: 0L
        val responseHeadersBytes = response.headers.toString().toByteArray().size.toLong()
        val responseBodyBytes = response.body?.contentLength()?.takeIf { it >= 0 } ?: 0L

        IPLogs.getInstance().LOG += "${Util.getCurrentDate()} - NETWORK_METRIC " +
            "session=${UUID.randomUUID()} " +
            "stage=$stage " +
            "transport=okhttp " +
            "http_code=${response.code} " +
            "request_headers_bytes=$requestHeadersBytes " +
            "request_body_bytes=$requestBodyBytes " +
            "request_total_bytes=${requestHeadersBytes + requestBodyBytes} " +
            "response_headers_bytes=$responseHeadersBytes " +
            "response_body_bytes=$responseBodyBytes " +
            "response_total_bytes=${responseHeadersBytes + responseBodyBytes} " +
            "url=${request.url} note=\n"
    }
}
