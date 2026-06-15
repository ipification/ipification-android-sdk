package com.ipification.mobile.sdk.ip.network

import android.net.Network
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.utils.IPLogs
import com.ipification.mobile.sdk.ip.utils.LogUtils
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

/**
 * Resolves hostnames through a specific Android network.
 *
 * This keeps DNS resolution on the same cellular network used by the request socket. Resolution
 * failures are returned to OkHttp instead of falling back to a different system network.
 */
internal class NetworkDns(
    private val network: Network
) : Dns {

    /** Resolves [hostname] while preserving Android's preferred address order. */
    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.isBlank()) {
            throw UnknownHostException("Hostname is empty")
        }

        return try {
            network.getAllByName(hostname).toList().also { addresses ->
                if (addresses.isEmpty()) {
                    throw UnknownHostException("No addresses found for $hostname")
                }
                log("Resolved ${addresses.size} address(es) through the request network")
            }
        } catch (exception: UnknownHostException) {
            log("Request-network DNS could not resolve the hostname")
            throw exception
        } catch (exception: RuntimeException) {
            log("Request-network DNS failed: ${exception.message}")
            throw UnknownHostException("Request-network DNS failed for $hostname").apply {
                initCause(exception)
            }
        }
    }

    private fun log(message: String) {
        if (!IPConfiguration.getInstance().debug) return

        IPLogs.getInstance().LOG +=
            "${LogUtils.currentTimestamp()} - $LOG_TAG - $message\n"
    }

    private companion object {
        const val LOG_TAG = "NetworkDns"
    }
}
