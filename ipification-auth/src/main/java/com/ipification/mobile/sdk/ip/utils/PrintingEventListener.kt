package com.ipification.mobile.sdk.ip.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.ipification.mobile.sdk.ip.IPConfiguration
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.*
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicLong

/**
 * A comprehensive OkHttp EventListener that logs the entire lifecycle of a network call.
 *
 * This listener is designed to be used with the `EventListener.Factory` pattern to ensure
 * that each call gets its own listener instance, making it safe for concurrent requests.
 *
 * It logs every event with a precise timestamp relative to the start of the call
 * and includes a unique call ID to distinguish between different network operations.
 */
class PrintingEventListener(
    private val callId: Long,
    private val callStartNanos: Long,
    private val verboseLogging: Boolean,
    private val context: Context? = null,
    private val requestNetwork: Network? = null
) : EventListener() {

    private val logBuilder = StringBuilder()
    private var requestHeadersBytes: Long = 0L
    private var requestBodyBytes: Long = 0L
    private var responseHeadersBytes: Long = 0L
    private var responseBodyBytes: Long = 0L
    private var responseCode: Int? = null
    private var metricLogged = false
    private var metricContext: DebugMetricContext? = null

    // Using a companion object for the factory is idiomatic in Kotlin.
    // This factory creates a new instance of the listener for each call.
    companion object {
        private val nextCallId = AtomicLong(1L)

        @JvmField // Expose as a static field for Java interop if needed.
        val FACTORY = Factory {
            val callId = nextCallId.getAndIncrement()
            PrintingEventListener(callId, System.nanoTime(), IPConfiguration.getInstance().debug)
        }

        /** Creates listeners that can include Android network and DNS context. */
        fun factory(context: Context, requestNetwork: Network?): Factory {
            val applicationContext = context.applicationContext
            return Factory {
                val callId = nextCallId.getAndIncrement()
                PrintingEventListener(
                    callId = callId,
                    callStartNanos = System.nanoTime(),
                    verboseLogging = IPConfiguration.getInstance().debug,
                    context = applicationContext,
                    requestNetwork = requestNetwork
                )
            }
        }
    }

    private fun printEvent(name: String, details: String? = null, category: String = "") {
        if (!verboseLogging) {
            return
        }
        val elapsedMillis = (System.nanoTime() - callStartNanos) / 1_000_000.0
        val timeStr = String.format("%7.3fms", elapsedMillis)
        
        val logLine = buildString {
            append("[Call #$callId] ")
            append(timeStr)
            if (category.isNotEmpty()) {
                append(" [$category] ")
            }
            append(" → $name")
            if (details != null) {
                append("\n                    ↳ $details")
            }
        }
        logBuilder.append(logLine).append("\n")
        IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - [Call #$callId] $timeStr [$category] $name" + 
            (if (details != null) "\n                    ↳ $details" else "") + "\n"
    }

    override fun callStart(call: Call) {
        metricContext = call.request().tag(DebugMetricContext::class.java)
        if (verboseLogging) {
            IPLogs.getInstance().LOG += "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        }
        printEvent("CALL START", "${call.request().url}", "LIFECYCLE")
    }

    override fun callEnd(call: Call) {
        emitMetricIfNeeded()
        printEvent("CALL END ✓", null, "LIFECYCLE")
        if (verboseLogging) {
            IPLogs.getInstance().LOG += "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
            println("--- Event Log for Call #$callId ---")
            println(logBuilder.toString())
            println("--- End of Log for Call #$callId ---")
        }
    }

    override fun callFailed(call: Call, ioe: IOException) {
        if (!metricLogged) {
            metricContext?.let {
                DebugNetworkMetrics.logFailureMetric(
                    context = it,
                    requestHeadersBytes = requestHeadersBytes,
                    requestBodyBytes = requestBodyBytes,
                    errorMessage = ioe.localizedMessage ?: ioe.javaClass.simpleName
                )
                metricLogged = true
            }
        }
        printEvent(
            "CALL FAILED ✗",
            "${formatException(ioe)}\n${networkSnapshot()}",
            "LIFECYCLE"
        )
        if (verboseLogging) {
            IPLogs.getInstance().LOG += "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
            println("--- Event Log for Failed Call #$callId ---")
            println(logBuilder.toString())
            println("--- End of Log for Failed Call #$callId ---")
        }
    }

    //region DNS Events
    override fun dnsStart(call: Call, domainName: String) {
        printEvent("DNS Lookup Start", "$domainName\n${networkSnapshot()}", "DNS")
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        printEvent("DNS Lookup End", "Resolved ${inetAddressList.size} address(es): ${inetAddressList.joinToString(", ")}", "DNS")
    }
    //endregion

    //region Connection Events
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        printEvent("Connect Start", "$inetSocketAddress via $proxy", "CONNECTION")
    }

    override fun secureConnectStart(call: Call) {
        printEvent("TLS Handshake Start", null, "CONNECTION")
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        val details = handshake?.let { "${it.tlsVersion} / ${it.cipherSuite}" } ?: "N/A"
        printEvent("TLS Handshake End ✓", details, "CONNECTION")
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        printEvent("Connect End ✓", "Protocol: $protocol", "CONNECTION")
    }

    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
        printEvent(
            "Connect Failed ✗",
            "$inetSocketAddress via $proxy protocol=$protocol\n${formatException(ioe)}\n${networkSnapshot()}",
            "CONNECTION"
        )
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        printEvent("Connection Acquired", "$connection", "CONNECTION")
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        printEvent("Connection Released", "$connection", "CONNECTION")
    }
    //endregion

    //region Request Events
    override fun requestHeadersStart(call: Call) {
        printEvent("Request Headers Start", null, "REQUEST")
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        requestHeadersBytes = request.headers.byteCount()
        printEvent("Request Headers End", "${request.method} - ${requestHeadersBytes} bytes", "REQUEST")
    }

    override fun requestBodyStart(call: Call) {
        printEvent("Request Body Start", null, "REQUEST")
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        requestBodyBytes = byteCount
        printEvent("Request Body End", "$byteCount bytes", "REQUEST")
    }

    override fun requestFailed(call: Call, ioe: IOException) {
        printEvent("Request Failed ✗", "$ioe", "REQUEST")
    }
    //endregion

    //region Response Events
    override fun responseHeadersStart(call: Call) {
        printEvent("Response Headers Start", null, "RESPONSE")
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        val status = if (response.isSuccessful) "✓" else if (response.isRedirect) "↪" else "✗"
        responseHeadersBytes = response.headers.byteCount()
        responseCode = response.code
        printEvent("Response Headers End $status", "HTTP ${response.code} ${response.message}", "RESPONSE")
    }

    override fun responseBodyStart(call: Call) {
        printEvent("Response Body Start", null, "RESPONSE")
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        responseBodyBytes = byteCount
        emitMetricIfNeeded()
        printEvent("Response Body End", "$byteCount bytes", "RESPONSE")
    }

    override fun responseFailed(call: Call, ioe: IOException) {
        printEvent("Response Failed ✗", "$ioe", "RESPONSE")
    }
    //endregion

    //region Other Events
    override fun canceled(call: Call) {
        printEvent("CANCELED", null, "LIFECYCLE")
    }

    private fun emitMetricIfNeeded() {
        if (metricLogged) {
            return
        }
        metricContext?.let {
            DebugNetworkMetrics.logHttpMetric(
                context = it,
                requestHeadersBytes = requestHeadersBytes,
                requestBodyBytes = requestBodyBytes,
                responseHeadersBytes = responseHeadersBytes,
                responseBodyBytes = responseBodyBytes,
                httpCode = responseCode
            )
            metricLogged = true
        }
    }

    private fun formatException(exception: Throwable): String {
        val causes = generateSequence(exception) { it.cause }
            .take(6)
            .mapIndexed { index, cause ->
                "cause[$index]=${cause.javaClass.name}: ${cause.message.orEmpty()}"
            }
            .joinToString("\n")
        val suppressed = exception.suppressed
            .take(4)
            .joinToString("\n") { "suppressed=${it.javaClass.name}: ${it.message.orEmpty()}" }
        return listOf(causes, suppressed).filter(String::isNotBlank).joinToString("\n")
    }

    private fun networkSnapshot(): String {
        val appContext = context ?: return "android_network_context=unavailable"
        return runCatching {
            val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.activeNetwork
            } else {
                null
            }
            val selectedNetwork = requestNetwork ?: activeNetwork
            val capabilities = selectedNetwork?.let(manager::getNetworkCapabilities)
            val linkProperties = selectedNetwork?.let(manager::getLinkProperties)
            val transports = buildList {
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) add("CELLULAR")
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) add("WIFI")
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) add("VPN")
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) add("ETHERNET")
            }
            val dnsServers = linkProperties?.dnsServers
                ?.joinToString(",") { it.hostAddress.orEmpty() }
                .orEmpty()
            val localAddresses = linkProperties?.linkAddresses
                ?.joinToString(",") { it.address.hostAddress.orEmpty() }
                .orEmpty()
            val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val internet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val privateDnsActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                linkProperties?.isPrivateDnsActive
            } else {
                null
            }
            val privateDnsServer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                linkProperties?.privateDnsServerName.orEmpty()
            } else {
                "unsupported"
            }

            "request_network=${requestNetwork ?: "none"} active_network=${activeNetwork ?: "none"} " +
                "selected_network=${selectedNetwork ?: "none"} transports=${transports.ifEmpty { listOf("none") }} " +
                "internet=$internet validated=$validated dns=[$dnsServers] local_addresses=[$localAddresses] " +
                "private_dns_active=$privateDnsActive private_dns_server=$privateDnsServer"
        }.getOrElse { exception ->
            "android_network_context_error=${exception.javaClass.simpleName}:${exception.message.orEmpty()}"
        }
    }
    //endregion
}
