package com.ipification.mobile.sdk.ip.ui

/**
 * Result returned by the internal WebView authentication screen.
 *
 * @property isSuccess Whether the WebView flow reached the configured redirect URI.
 * @property url Full redirect URL returned by the auth server when successful.
 * @property errorMessage Failure details when the WebView flow fails.
 * @property isCancelled Whether the user dismissed the WebView before completion.
 */
internal data class WebViewAuthResult(
    val isSuccess: Boolean,
    val url: String? = null,
    val errorMessage: String? = null,
    val isCancelled: Boolean = false
)

/**
 * One-shot bridge between [AuthWebViewActivity] and the service that launched it.
 */
internal object WebViewAuthBridge {
    private val lock = Any()
    private var listener: ((WebViewAuthResult) -> Unit)? = null

    /** Registers the single listener that should receive the next WebView result. */
    fun setListener(listener: ((WebViewAuthResult) -> Unit)?) {
        synchronized(lock) {
            this.listener = listener
        }
    }

    /** Delivers a successful redirect result and clears the listener. */
    fun notifySuccess(redirectUrl: String) {
        notifyOnce(WebViewAuthResult(isSuccess = true, url = redirectUrl))
    }

    /** Delivers a WebView failure result and clears the listener. */
    fun notifyError(message: String?) {
        notifyOnce(
            WebViewAuthResult(
                isSuccess = false,
                errorMessage = message,
                isCancelled = false
            )
        )
    }

    /** Delivers a user-cancelled result and clears the listener. */
    fun notifyCancel() {
        notifyOnce(
            WebViewAuthResult(
                isSuccess = false,
                isCancelled = true
            )
        )
    }

    /** Clears any pending listener without sending a result. */
    fun clear() {
        synchronized(lock) {
            listener = null
        }
    }

    private fun notifyOnce(result: WebViewAuthResult) {
        val callback = synchronized(lock) {
            listener.also { listener = null }
        }
        callback?.invoke(result)
    }
}
