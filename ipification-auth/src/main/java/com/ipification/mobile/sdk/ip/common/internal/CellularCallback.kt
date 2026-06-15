package com.ipification.mobile.sdk.ip.common.internal

import com.ipification.mobile.sdk.ip.exception.CellularException

/** Receives low-level transport results used by SDK service implementations. */
interface CellularCallback<T> {

    /** Called when the transport returns the expected response type. */
    fun onSuccess(response: T)

    /** Called when the transport or response parsing fails. */
    fun onError(error: CellularException)

    /** Called when the user cancels an IM verification flow. */
    fun onIMCancel() {}
}
