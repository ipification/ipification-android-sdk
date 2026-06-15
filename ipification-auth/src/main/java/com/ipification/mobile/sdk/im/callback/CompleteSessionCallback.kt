package com.ipification.mobile.sdk.im.callback

import com.ipification.mobile.sdk.ip.response.IPAuthResponse

/** Status returned when an IM session cannot be completed successfully. */
enum class CompleteStatus(val value: String) {
    /** The saved IM session does not exist or has expired. */
    NOT_FOUND("not_found"),

    /** The IM session was already completed. */
    FINISHED("finished"),

    /** The IM session is still waiting for the user to complete verification. */
    PENDING("pending"),

    /** The session could not be completed for an unrecognized reason. */
    UNKNOWN("");

    /** Legacy property name retained for compatibility. */
    @Deprecated("Use value.", ReplaceWith("value"))
    val status: String
        get() = value

    companion object {
        /** Converts a server session status into its SDK representation. */
        fun fromValue(value: String?): CompleteStatus {
            return entries.firstOrNull { it.value == value } ?: UNKNOWN
        }
    }
}

/**
 * Callback used to receive the result of completing an IM session.
 *
 * Implement this interface and pass it to `IMPublicAPIServices.completeSession`.
 * The SDK invokes either [onSuccess] with the authorization result or [onError]
 * with the current completion status.
 */
interface CompleteSessionCallback {

    /**
     * Called when the IM session completes successfully.
     *
     * Use the authorization response or send its code to your backend for token exchange.
     *
     * @param code Authorization code returned by the completed IM session.
     * @param state State returned with the authorization response, or an empty string.
     * @param response Complete IP authentication response.
     */
    fun onSuccess(code: String, state: String, response: IPAuthResponse)

    /**
     * Called when the IM session cannot be completed successfully.
     *
     * Use [status] to decide whether to retry, wait for completion, or restart the flow.
     *
     * @param status Current session completion status.
     * @param message Additional error information, when available.
     */
    fun onError(status: CompleteStatus?, message: String)
}
