package com.ipification.mobile.sdk.ip.utils

import android.content.Context
import android.net.TrafficStats
import android.util.Log
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.request.ApiType
import com.ipification.mobile.sdk.ip.request.AuthRequest
import java.util.UUID

data class DebugMetricContext(
    val sessionId: String,
    val stage: String,
    val transport: String,
    val url: String
)

internal object DebugNetworkMetrics {
    private const val METRIC_TAG = "NETWORK_METRIC"

    fun createContext(
        request: AuthRequest,
        requestUrl: String,
        isRedirect: Boolean,
        transport: String
    ): DebugMetricContext {
        return DebugMetricContext(
            sessionId = UUID.randomUUID().toString(),
            stage = inferStage(request, requestUrl, isRedirect),
            transport = transport,
            url = requestUrl
        )
    }

    fun logHttpMetric(
        context: DebugMetricContext,
        requestHeadersBytes: Long,
        requestBodyBytes: Long,
        responseHeadersBytes: Long,
        responseBodyBytes: Long,
        httpCode: Int?,
        note: String? = null
    ) {
        if (!shouldEmitMetrics()) {
            return
        }
        val requestTotal = requestHeadersBytes + requestBodyBytes
        val responseTotal = responseHeadersBytes + responseBodyBytes
        val parts = listOf(
            "session=${sanitize(context.sessionId)}",
            "stage=${sanitize(context.stage)}",
            "transport=${sanitize(context.transport)}",
            "http_code=${httpCode ?: -1}",
            "request_headers_bytes=$requestHeadersBytes",
            "request_body_bytes=$requestBodyBytes",
            "request_total_bytes=$requestTotal",
            "response_headers_bytes=$responseHeadersBytes",
            "response_body_bytes=$responseBodyBytes",
            "response_total_bytes=$responseTotal",
            "url=${sanitize(context.url)}",
            "note=${sanitize(note ?: "")}"
        )
        emitMetricLine(parts.joinToString(" "))
    }

    fun logFailureMetric(
        context: DebugMetricContext,
        requestHeadersBytes: Long,
        requestBodyBytes: Long,
        errorMessage: String?
    ) {
        if (!shouldEmitMetrics()) {
            return
        }
        val requestTotal = requestHeadersBytes + requestBodyBytes
        val parts = listOf(
            "session=${sanitize(context.sessionId)}",
            "stage=${sanitize(context.stage)}",
            "transport=${sanitize(context.transport)}",
            "http_code=-1",
            "request_headers_bytes=$requestHeadersBytes",
            "request_body_bytes=$requestBodyBytes",
            "request_total_bytes=$requestTotal",
            "response_headers_bytes=0",
            "response_body_bytes=0",
            "response_total_bytes=0",
            "url=${sanitize(context.url)}",
            "note=${sanitize(errorMessage ?: "request_failed")}"
        )
        emitMetricLine(parts.joinToString(" "))
    }

    fun headerFieldsByteCount(headers: Map<String, List<String>>?): Long {
        if (headers == null) {
            return 0L
        }
        var total = 0L
        for ((name, values) in headers) {
            if (values.isEmpty()) {
                total += utf8Size(name) + 2L
            } else {
                for (value in values) {
                    total += utf8Size(name) + utf8Size(value) + 4L
                }
            }
        }
        return total
    }

    fun utf8Size(value: String?): Long {
        return value?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L
    }

    private fun inferStage(request: AuthRequest, requestUrl: String, isRedirect: Boolean): String {
        val redirectUri = request.redirectUri?.toString().orEmpty()
        return when {
            request.apiType == ApiType.COVERAGE -> "coverage"
            redirectUri.isNotBlank() && requestUrl.startsWith(redirectUri) -> "callback"
            isRedirect -> "redirect"
            request.apiType == ApiType.AUTH -> "auth"
            else -> "other"
        }
    }

    private fun sanitize(value: String): String {
        return value
            .replace("\n", "_")
            .replace("\r", "_")
            .replace(" ", "_")
    }

    private fun emitMetricLine(messageBody: String) {
        val line = "$METRIC_TAG $messageBody"
        Log.d(METRIC_TAG, line)
        IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - $line\n"
    }

    private fun shouldEmitMetrics(): Boolean {
        return IPConfiguration.getInstance().debug
    }
}

internal data class WebViewTrafficSnapshot(
    val sessionId: String,
    val startUrl: String,
    val startTxBytes: Long,
    val startRxBytes: Long,
    val supported: Boolean
)

internal object WebViewDebugTrafficTracker {
    fun start(context: Context, startUrl: String): WebViewTrafficSnapshot {
        val uid = context.applicationInfo.uid
        val txBytes = TrafficStats.getUidTxBytes(uid)
        val rxBytes = TrafficStats.getUidRxBytes(uid)
        val supported = txBytes != TrafficStats.UNSUPPORTED.toLong() &&
            rxBytes != TrafficStats.UNSUPPORTED.toLong()
        return WebViewTrafficSnapshot(
            sessionId = UUID.randomUUID().toString(),
            startUrl = startUrl,
            startTxBytes = if (supported) txBytes else 0L,
            startRxBytes = if (supported) rxBytes else 0L,
            supported = supported
        )
    }

    fun finish(
        context: Context,
        snapshot: WebViewTrafficSnapshot,
        endUrl: String?,
        note: String
    ) {
        if (!IPConfiguration.getInstance().debug) {
            return
        }

        val safeEndUrl = endUrl ?: snapshot.startUrl
        if (!snapshot.supported) {
            DebugNetworkMetrics.logFailureMetric(
                context = DebugMetricContext(
                    sessionId = snapshot.sessionId,
                    stage = "auth_webview_flow",
                    transport = "webview",
                    url = safeEndUrl
                ),
                requestHeadersBytes = 0L,
                requestBodyBytes = 0L,
                errorMessage = "trafficstats_unsupported"
            )
            return
        }

        val uid = context.applicationInfo.uid
        val endTxBytes = TrafficStats.getUidTxBytes(uid)
        val endRxBytes = TrafficStats.getUidRxBytes(uid)
        val txDelta = (endTxBytes - snapshot.startTxBytes).coerceAtLeast(0L)
        val rxDelta = (endRxBytes - snapshot.startRxBytes).coerceAtLeast(0L)

        DebugNetworkMetrics.logHttpMetric(
            context = DebugMetricContext(
                sessionId = snapshot.sessionId,
                stage = "auth_webview_flow",
                transport = "webview",
                url = safeEndUrl
            ),
            requestHeadersBytes = 0L,
            requestBodyBytes = txDelta,
            responseHeadersBytes = 0L,
            responseBodyBytes = rxDelta,
            httpCode = null,
            note = note
        )
    }
}
