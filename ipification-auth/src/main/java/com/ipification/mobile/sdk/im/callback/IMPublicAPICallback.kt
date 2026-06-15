package com.ipification.mobile.sdk.im.callback

import com.ipification.mobile.sdk.im.response.IMResponse
import com.ipification.mobile.sdk.ip.exception.IPificationError
import com.ipification.mobile.sdk.ip.response.IPAuthResponse

/** Receives responses from the manual IM public API flow. */
interface IMPublicAPICallback {

    /**
     * Called when authentication returns either an IM session or an IP authorization response.
     *
     * Exactly one of [imResponse] or [ipResponse] is expected for a successful callback.
     */
    fun onSuccess(imResponse: IMResponse?, ipResponse: IPAuthResponse?)

    /** Called when the IM public API flow fails. */
    fun onError(error: IPificationError)

    /** Called when the user cancels the IM flow. */
    fun onCancel() {}
}
