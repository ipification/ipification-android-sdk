package com.ipification.mobile.sdk.ip.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.ipification.mobile.sdk.ip.IPConfiguration
import java.net.Inet4Address
import java.net.Inet6Address

/**
 * Provides internal network and telephony state checks used by SDK flows and diagnostics.
 */
internal object NetworkUtils {

    /**
     * Returns whether the system mobile-data setting is enabled.
     *
     * Android does not expose this setting through a public API. When the setting cannot be read,
     * the SDK returns `true` so it can still attempt cellular authentication.
     */
    @Suppress("PrivateApi")
    internal fun isMobileDataEnabled(context: Context): Boolean {
        return runCatching {
            val connectivityManager = context.connectivityManager()
            val method = connectivityManager.javaClass.getDeclaredMethod("getMobileDataEnabled")
            method.isAccessible = true
            method.invoke(connectivityManager) as? Boolean
        }.onFailure {
            LogUtils.debug("Unable to read mobile-data setting: ${it.message}")
        }.getOrNull() ?: true
    }

    /** Returns whether the active network is a VPN. */
    internal fun isVpnEnabled(context: Context): Boolean {
        return context.activeNetworkCapabilities()
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    /** Returns whether the active cellular network is roaming. */
    internal fun isRoaming(context: Context): Boolean {
        val connectivityManager = context.connectivityManager()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val capabilities = context.activeNetworkCapabilities() ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isRoaming == true
        }
    }

    /** Returns whether the active network reports internet capability. */
    internal fun hasInternet(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.activeNetworkCapabilities()
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            context.connectivityManager().activeNetworkInfo?.isConnectedOrConnecting == true
        }
    }

    /** Returns whether Wi-Fi is the active connected network. */
    internal fun isWifiEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.activeNetworkCapabilities()
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            context.connectivityManager().activeNetworkInfo?.let {
                it.type == ConnectivityManager.TYPE_WIFI && it.isConnected
            } == true
        }
    }

    /** Stores the selected network's private IPv4 and IPv6 addresses for SDK diagnostics. */
    internal fun checkPrivateIP(context: Context, network: Network?) {
        val selectedNetwork = network ?: getActiveNetwork(context)
        IPConfiguration.getInstance().CELLULAR_PRIVATE_IP = selectedNetwork
            ?.let { getIpAddress(context, it) }
            ?: if (selectedNetwork == null) "not available - network is null" else "not available"
    }

    /** Returns whether airplane mode is enabled. */
    internal fun isAirplaneModeOn(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0
    }

    /**
     * Returns whether any currently available network is connected through cellular transport.
     *
     * Checking all networks also detects cellular connectivity while Wi-Fi is the default network.
     */
    @Suppress("DEPRECATION")
    internal fun isCellularConnected(context: Context): Boolean {
        val connectivityManager = context.connectivityManager()
        val result = connectivityManager.allNetworks.any { network ->
            connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } == true
        }

        LogUtils.debug("isCellularConnected: $result")
        if (IPConfiguration.getInstance().debug) {
            IPLogs.getInstance().LOG +=
                "${LogUtils.currentTimestamp()} - isCellularConnected: $result\n"
        }
        return result
    }

    /** Returns a readable SIM state for the active subscription. */
    internal fun getSimState(context: Context): String {
        return runCatching {
            val baseTelephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val subscriptionId =
                DeviceUtils.getInstance(context).activeSimOperator().getSubscriptionId()
            val telephonyManager =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                ) {
                    baseTelephonyManager.createForSubscriptionId(subscriptionId)
                } else {
                    baseTelephonyManager
                }

            simStateName(telephonyManager.simState)
        }.getOrElse {
            LogUtils.debug("Unable to read SIM state: ${it.message}")
            "SIM_STATE_UNKNOWN"
        }
    }

    private fun getIpAddress(context: Context, network: Network): String? {
        val addresses = context.connectivityManager()
            .getLinkProperties(network)
            ?.linkAddresses
            ?.map { it.address }

        val ipv4Address = addresses?.firstOrNull { it is Inet4Address }?.hostAddress
        val ipv6Address = addresses
            ?.firstOrNull { it is Inet6Address && !it.isLinkLocalAddress }
            ?.hostAddress

        return listOfNotNull(ipv4Address, ipv6Address)
            .takeIf { it.isNotEmpty() }
            ?.joinToString("|")
    }

    @Suppress("DEPRECATION")
    private fun getActiveNetwork(context: Context): Network? {
        val connectivityManager = context.connectivityManager()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager.activeNetwork
        } else {
            connectivityManager.allNetworks.firstOrNull { network ->
                connectivityManager.getNetworkInfo(network)?.isConnected == true
            }
        }
    }

    private fun simStateName(simState: Int): String = when (simState) {
        TelephonyManager.SIM_STATE_ABSENT -> "SIM_STATE_ABSENT"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "SIM_STATE_NETWORK_LOCKED"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "SIM_STATE_PIN_REQUIRED"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "SIM_STATE_PUK_REQUIRED"
        TelephonyManager.SIM_STATE_READY -> "SIM_STATE_READY"
        TelephonyManager.SIM_STATE_NOT_READY -> "SIM_STATE_NOT_READY"
        TelephonyManager.SIM_STATE_PERM_DISABLED -> "SIM_STATE_PERM_DISABLED"
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "SIM_STATE_CARD_IO_ERROR"
        TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "SIM_STATE_CARD_RESTRICTED"
        else -> "SIM_STATE_UNKNOWN ($simState)"
    }

    private fun Context.connectivityManager(): ConnectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun Context.activeNetworkCapabilities(): NetworkCapabilities? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null
        }
        val connectivityManager = connectivityManager()
        return connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    }
}
