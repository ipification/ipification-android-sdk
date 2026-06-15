package com.ipification.mobile.sdk.ip.network

import android.net.Network
import com.ipification.mobile.sdk.ip.exception.CellularException

/** Receives the result of requesting an IP cellular network. */
internal interface IPNetworkCallback {

    /** Called when the requested cellular network is available. */
    fun onSuccess(network: Network)

    /** Called when a cellular network cannot be acquired. */
    fun onError(error: CellularException)
}
