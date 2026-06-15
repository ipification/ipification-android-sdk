package com.ipification.mobile.sdk.ip.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.SingletonHolder
import com.ipification.mobile.sdk.ip.exception.CellularException
import com.ipification.mobile.sdk.ip.utils.ErrorCode
import com.ipification.mobile.sdk.ip.utils.ErrorMessages
import com.ipification.mobile.sdk.ip.utils.IPLogs
import com.ipification.mobile.sdk.ip.utils.LogUtils
import com.ipification.mobile.sdk.ip.utils.NetworkUtils
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Acquires and retains the cellular network used by IP authentication requests. */
internal class NetworkManager private constructor(context: Context) {

    companion object : SingletonHolder<NetworkManager, Context>(::NetworkManager) {
        private const val LOG_TAG = "NetworkManager"
        private const val RETRY_DELAY_MILLIS = 1_000L
        private const val UNVALIDATED_NETWORK_DELAY_MILLIS = 2_000L
    }

    /** Application context avoids retaining the Activity used to initialize the singleton. */
    private val context = context.applicationContext

    // Request processing and Android network callbacks run away from the UI thread.
    private val workerThread = HandlerThread("NetworkManagerThread").apply { start() }
    private val workerHandler = Handler(workerThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var activeNetwork: Network? = null

    // A connect operation must deliver only one success or error result.
    private val hasDeliveredResult = AtomicBoolean(false)
    private val isDelayedSuccessScheduled = AtomicBoolean(false)
    private val hasRetriedUnavailable = AtomicBoolean(false)

    private val networkRequestTimeoutMillis = IPConfiguration.getInstance().CONNECT_NETWORK_TIMEOUT

    private var delayedSuccessRunnable: Runnable? = null
    private var legacyTimeoutRunnable: Runnable? = null

    init {
        log("NetworkManager has been initialized")
    }

    private fun sendError(
        description: String,
        code: Int,
        callback: IPNetworkCallback
    ) {
        trySendTerminalCallback {
            val error = CellularException().apply {
                errorDescription = description
                sdkErrorCode = code
            }
            mainHandler.post { callback.onError(error) }
        }
    }

    private fun sendException(exception: Exception, callback: IPNetworkCallback) {
        trySendTerminalCallback {
            val error = CellularException().apply {
                this.exception = exception
                errorDescription =
                    "${ErrorMessages.NETWORK_IS_UNAVAILABLE} with exception: ${exception.message}"
                sdkErrorCode = ErrorCode.NETWORK_IS_UNAVAILABLE
            }
            mainHandler.post { callback.onError(error) }
            activeNetwork = null
            networkCallback = null
        }
    }

    /** Runs the first terminal callback and cancels pending timeout work. */
    private fun trySendTerminalCallback(action: () -> Unit) {
        if (hasDeliveredResult.compareAndSet(false, true)) {
            log("trySendTerminalCallback -- action...")
            action()
        } else {
            log("Terminal callback already invoked or handled for this operation, ignoring duplicate.")
        }
        cancelPendingCallbacks()
    }

    /** Requests a cellular network and returns the result on the main thread. */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun connect(ipNetworkCallback: IPNetworkCallback, isRetry: Boolean = false) {
        workerHandler.post {
            connectInternal(ipNetworkCallback, isRetry)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun connectInternal(ipNetworkCallback: IPNetworkCallback, isRetry: Boolean = false) {
        log("start connecting network... $ipNetworkCallback")
        if (!isRetry) {
            hasRetriedUnavailable.set(false)
            isDelayedSuccessScheduled.set(false)
            hasDeliveredResult.set(false)
        }
        cancelPendingCallbacks()

        // Reuse a cellular network retained by a previous successful request.
        activeNetwork?.let { existing ->
            trySendTerminalCallback {
                mainHandler.post {
                    ipNetworkCallback.onSuccess(existing)
                }
            }
            return
        }

        if (networkCallback != null) {
            log("Warning: connect() called while a NetworkCallback is already active.")
        }

        log("processing cellular network ... ")
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                log("onAvailable: $network. Awaiting capabilities.")

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    startSuccess(network, ipNetworkCallback)
                }
                if (IPConfiguration.getInstance().debug) {
                    val logDetails = try {
                        val manager = connectivityManager()
                        val capabilities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            manager.getNetworkCapabilities(network)
                        } else {
                            null
                        }
                        """
                        SIM State: ${NetworkUtils.getSimState(context)}
                        ActiveNetworkInfo: $network
                        NetworkCapabilities: $capabilities
                        Transport CELLULAR: ${capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}
                        Transport WIFI: ${capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}
                        IsAirplaneModeOn: ${NetworkUtils.isAirplaneModeOn(context)}
                        SDK_INT: ${Build.VERSION.SDK_INT}
                    """.trimIndent()
                    } catch (e: Exception) {
                        "Error gathering network details: ${e.message}"
                    }
                    log("onAvailable. Details:\n$logDetails")

                }
            }

            override fun onUnavailable() {
                super.onUnavailable()
                if (hasRetriedUnavailable.compareAndSet(false, true)) {
                    log("onUnavailable triggered, retrying once...")

                    workerHandler.postDelayed({
                        log("Retrying connect() after onUnavailable")
                        connect(ipNetworkCallback, true)
                    }, RETRY_DELAY_MILLIS)

                    return
                }
                if (IPConfiguration.getInstance().debug) {
                    val logDetails = try {
                        val manager = connectivityManager()
                        val currentNetwork =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) manager.activeNetwork
                            else null
                        val capabilities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            manager.getNetworkCapabilities(currentNetwork)
                        } else {
                            null
                        }
                        """
                        SIM State: ${NetworkUtils.getSimState(context)}
                        ActiveNetwork: $currentNetwork
                        NetworkCapabilities: $capabilities
                        Transport CELLULAR: ${capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}
                        Transport WIFI: ${capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}
                        IsAirplaneModeOn: ${NetworkUtils.isAirplaneModeOn(context)}
                        SDK_INT: ${Build.VERSION.SDK_INT}
                        
                    """.trimIndent()
                    } catch (e: Exception) {
                        "Error gathering network details: ${e.message}"
                    }
                    log("onUnavailable. Details:\n$logDetails")

                }

                sendError(
                    "${ErrorMessages.NETWORK_IS_UNAVAILABLE} (already retried)",
                    ErrorCode.NETWORK_IS_UNAVAILABLE,
                    ipNetworkCallback
                )
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                log("onLost: Network lost ($network). Clearing activeNetwork and networkCallback.")
                if (activeNetwork == network) {
                    activeNetwork = null
                }
                networkCallback = null
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                if (hasDeliveredResult.get()) {
                    return
                }
                log("onCapabilitiesChanged: Cellular network capabilities changed: $networkCapabilities for $network ")
                val hasInternet =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    } else {
                        hasInternet
                    }

                log("onCapabilitiesChanged: hasInternet $hasInternet isValidated $isValidated ")
                if (hasInternet) {
                    if (isValidated) {
                        startSuccess(network, ipNetworkCallback)
                        return
                    }
                    if (isDelayedSuccessScheduled.compareAndSet(false, true)) {
                        log("create delay checking...")
                        delayedSuccessRunnable = Runnable {
                            log("call delay checking activate...")
                            trySendTerminalCallback {
                                IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - onCapabilitiesChanged: Network available after validation delay.\n"
                                activeNetwork = network
                                try {
                                    NetworkUtils.checkPrivateIP(context, network)
                                } catch (e: Exception) {
                                    log("Error checking private IP: ${e.message}")
                                }
                                log("ipNetworkCallback.onSuccess($network)")
                                mainHandler.post { ipNetworkCallback.onSuccess(network) }
                            }
                            isDelayedSuccessScheduled.set(false)
                        }
                        workerHandler.postDelayed(
                            requireNotNull(delayedSuccessRunnable),
                            UNVALIDATED_NETWORK_DELAY_MILLIS
                        )
                    } else {
                        log("already delay")
                    }
                } else {
                    sendError(
                        "${ErrorMessages.NETWORK_IS_NOT_ACTIVE} (no INTERNET capability)",
                        ErrorCode.NETWORK_IS_UNAVAILABLE,
                        ipNetworkCallback
                    )
                }
            }
        }

        val manager = connectivityManager()
        val builder = NetworkRequest.Builder().apply {
            addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requestNetworkV26(manager, builder)
            } else {
                requestNetworkV21(manager, builder, ipNetworkCallback)
            }
        } catch (e: Exception) {
            sendException(e, ipNetworkCallback)
        }
    }

    private fun startSuccess(network: Network, ipNetworkCallback: IPNetworkCallback) {
        trySendTerminalCallback {
            IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - startSuccess ...\n"
            activeNetwork = network
            try {
                NetworkUtils.checkPrivateIP(context, network)
            } catch (e: Exception) {
                log("Error checking private IP: ${e.message}")
            }
            log("ipNetworkCallback.onSuccess($network)")
            mainHandler.post { ipNetworkCallback.onSuccess(network) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun requestNetworkV21(
        manager: ConnectivityManager,
        builder: NetworkRequest.Builder,
        ipNetworkCallback: IPNetworkCallback
    ) {
        val request = builder.build()
        try {
            manager.requestNetwork(request, requireNotNull(networkCallback))
        } catch (e: Exception) {
            log("requestNetwork failed on API 21-25, registering callback instead: ${e.message}")
            manager.registerNetworkCallback(request, requireNotNull(networkCallback))
        }

        legacyTimeoutRunnable = Runnable {
            log("V21 timeout check: hasDeliveredResult=${hasDeliveredResult.get()}")
            sendError(
                ErrorMessages.NETWORK_IS_UNAVAILABLE + " (v21)",
                ErrorCode.NETWORK_IS_UNAVAILABLE,
                ipNetworkCallback
            )
        }
        workerHandler.postDelayed(requireNotNull(legacyTimeoutRunnable), networkRequestTimeoutMillis)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestNetworkV26(
        manager: ConnectivityManager,
        builder: NetworkRequest.Builder
    ) {
        manager.requestNetwork(
            builder.build(),
            requireNotNull(networkCallback),
            networkRequestTimeoutMillis.toInt()
        )
    }

    fun unregister(): Boolean {
        hasRetriedUnavailable.set(false)
        isDelayedSuccessScheduled.set(false)
        hasDeliveredResult.set(false)
        cancelPendingCallbacks()

        val callback = networkCallback ?: run {
            log("unregister: no active callback")
            activeNetwork = null
            return false
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            log("unregister: Pre-Lollipop, no unregister API. Clearing local refs.")
            activeNetwork = null
            networkCallback = null
            return false
        }

        val manager = connectivityManager()
        var unregistrationCallSuccess = false

        // Some partner integrations only unregister callbacks on affected device brands.
        val onlyAffectedBrands = IPConfiguration.getInstance().onlyAffectedBrands
        val manufacturer = Build.MANUFACTURER?.lowercase(Locale.getDefault()) ?: ""
        val isSamsungOrHuawei = manufacturer.contains("samsung") || manufacturer.contains("huawei")
        val shouldAttemptSystemUnregister = !onlyAffectedBrands || isSamsungOrHuawei

        if (shouldAttemptSystemUnregister) {
            try {
                manager.unregisterNetworkCallback(callback)
                Log.i(LOG_TAG, "Successfully called system unregisterNetworkCallback${if (onlyAffectedBrands) " (for specific brand)" else ""}")
                log("System unregisterNetworkCallback called successfully.")
                unregistrationCallSuccess = true
            } catch (e: IllegalArgumentException) {
                Log.w(LOG_TAG, "Failed to call system unregisterNetworkCallback (IllegalArgumentException): ${e.message}")
                log("System unregisterNetworkCallback failed (IllegalArgumentException): ${e.message}")
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to call system unregisterNetworkCallback (Exception)", e)
                log("System unregisterNetworkCallback failed (Exception): ${e.message}")
            }
        } else {
            Log.i(LOG_TAG, "Skipped system unregisterNetworkCallback due to brand filtering. Manufacturer: $manufacturer")
            log("Skipped system unregisterNetworkCallback due to brand filtering. Manufacturer: $manufacturer")
        }

        activeNetwork = null
        networkCallback = null
        hasDeliveredResult.set(false)
        log("unregister complete. System call success: $unregistrationCallSuccess")
        return unregistrationCallSuccess
    }

    private fun log(message: String) {
        Log.d(LOG_TAG, message)
        if (IPConfiguration.getInstance().debug) {
            IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - NetworkManager - $message\n"
        }
    }

    /** Releases any active callback and clears retained network state. */
    fun reset() {
        unregister()
    }

    private fun cancelPendingCallbacks() {
        delayedSuccessRunnable?.let(workerHandler::removeCallbacks)
        delayedSuccessRunnable = null
        legacyTimeoutRunnable?.let(workerHandler::removeCallbacks)
        legacyTimeoutRunnable = null
    }

    private fun connectivityManager(): ConnectivityManager {
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
}
