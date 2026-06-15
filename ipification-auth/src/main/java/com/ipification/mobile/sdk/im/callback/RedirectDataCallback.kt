package com.ipification.mobile.sdk.im.callback

/** Receives the resolved IM provider deep link. */
interface RedirectDataCallback {

    /** Called with the resolved deep link, or the original URL when resolution fails. */
    fun onResponse(link: String)
}
