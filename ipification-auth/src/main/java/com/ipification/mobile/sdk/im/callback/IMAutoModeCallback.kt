package com.ipification.mobile.sdk.im.callback

import com.ipification.mobile.sdk.ip.exception.IPificationError

/** Receives the result of IM auto-mode authentication. */
interface IMAutoModeCallback {

    /** Called when IM auto-mode completes successfully. */
    fun onSuccess()

    /** Called when IM auto-mode fails. */
    fun onError(error: IPificationError)

    /** Called when the user cancels the IM flow. */
    fun onCancel() {}
}
