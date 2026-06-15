package com.ipification.demoapp.activity.ui.process // Adjust package name as needed

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Utility class to check network-related states on an Android device.
 */
object NetworkUtils {

    /**
     * Checks if mobile data is enabled by using reflection to access a hidden API.
     * Note: This method is fragile and may not work on all devices or Android versions (especially API 28+)
     * due to restrictions on hidden APIs. It defaults to `true` if the state cannot be determined.
     *
     * @param context The context used to access system services.
     * @return `true` if mobile data is enabled or if the state cannot be determined, `false` otherwise.
     */
    @Suppress("PrivateApi")
    internal fun isMobileDataEnabled(context: Context): Boolean {
        return runCatching {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val method = connectivityManager.javaClass.getDeclaredMethod("getMobileDataEnabled")
            method.isAccessible = true
            method.invoke(connectivityManager) as? Boolean
        }.getOrNull() ?: true
    }

    /**
     * Checks if the device is actively connected to a Wi-Fi network.
     * This does not check if the Wi-Fi radio is enabled, only if Wi-Fi is the current active network.
     *
     * @param context The context used to access system services.
     * @return `true` if the device is connected to a Wi-Fi network, `false` otherwise.
     */
    fun isWifiEnabled(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            networkInfo?.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected
        }
    }
}
