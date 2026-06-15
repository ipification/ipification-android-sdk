package com.ipification.mobile.sdk.im

/** Internal callback used by the SDK-hosted IM verification activity result flow. */
interface VerifyCompleteListener {

    /** Called when the IM verification screen returns an authorization response. */
    fun onSuccess(sessionId: String, responseData: String?)

    /** Called when the IM verification screen returns an error. */
    fun onFail(errorMessage: String)

    /** Called when the user cancels the IM verification screen. */
    fun onCancel()
}
