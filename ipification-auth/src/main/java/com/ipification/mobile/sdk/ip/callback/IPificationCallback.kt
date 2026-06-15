package com.ipification.mobile.sdk.ip.callback

import com.ipification.mobile.sdk.im.callback.IMCallback

/**
 * Legacy name for [IMCallback].
 */
@Deprecated(
    message = "Use IMCallback from the IM package.",
    replaceWith = ReplaceWith(
        expression = "IMCallback",
        imports = ["com.ipification.mobile.sdk.im.callback.IMCallback"]
    )
)
interface IPificationCallback : IMCallback {

    @Deprecated(
        message = "Use IMCallback.onIMCancel().",
        replaceWith = ReplaceWith("onIMCancel()")
    )
    override fun onIMCancel() {}
}
