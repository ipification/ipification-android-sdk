package com.ipification.mobile.sdk.im.callback

import com.ipification.mobile.sdk.ip.callback.IPAuthCallback

/**
 * Receives authentication results and cancellation events from an IM flow.
 */
interface IMCallback : IPAuthCallback {

    /** Called when the user cancels the IM flow. */
    fun onIMCancel() {}
}
