package com.ipification.mobile.sdk.ip.interceptor

import android.util.Log
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.utils.IPLogs
import com.ipification.mobile.sdk.ip.utils.LogUtils
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Response
import java.net.InetAddress
import java.net.UnknownHostException

/** Logs system DNS results and socket connection details for network debugging. */
class LoggingInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val destinationHost = request.url.host

        try {
            val resolvedAddresses = lookupWithSystemDns(destinationHost)
            val destinationAddress = resolvedAddresses.firstOrNull()?.hostAddress ?: destinationHost
            val destinationPort = request.url.port
            val socket = chain.connection()?.socket()
            val sourceAddress = socket?.localAddress?.hostAddress
                ?: IPConfiguration.getInstance().CELLULAR_PRIVATE_IP
            val sourcePort = socket?.localPort ?: -1

            log("--> connect to $destinationAddress:$destinationPort from /$sourceAddress (port $sourcePort)")
        } catch (exception: Exception) {
            log(exception.message.orEmpty())
        }

        return chain.proceed(request)
    }

    /** Resolves the destination using the device system DNS. */
    private fun lookupWithSystemDns(hostname: String): List<InetAddress> {
        log("system DNS for $hostname")
        return try {
            Dns.SYSTEM.lookup(hostname)
        } catch (exception: UnknownHostException) {
            log("System DNS failed for $hostname")
            throw exception
        }
    }

    /** Appends a network-debug message to Logcat and the SDK debug log. */
    private fun log(message: String) {
        Log.d(LOG_TAG, message)
        IPLogs.getInstance().LOG +=
            "${LogUtils.currentTimestamp()} - $LOG_TAG - $message\n"
    }

    private companion object {
        const val LOG_TAG = "LoggingInterceptor"
    }
}
