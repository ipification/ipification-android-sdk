package com.ipification.mobile.sdk.ip.callback

import com.ipification.mobile.sdk.ip.exception.IPificationError
import com.ipification.mobile.sdk.ip.response.IPAuthResponse

/**
 * Callback used to receive the result of an IP-based authentication request.
 *
 * Implement this interface and pass it to the authentication API. The SDK invokes
 * either [onSuccess] when authentication completes or [onError] when it fails.
 */
interface IPAuthCallback {

    /**
     * Called after the authentication API returns a valid authorization response.
     *
     * Use this callback to send the authorization code to your backend for token exchange.
     *
     * @param response Authentication response containing the authorization result.
     */
    fun onSuccess(response: IPAuthResponse)

    /**
     * Called when authentication cannot return a valid authorization response.
     *
     * Use this callback to inspect the error, show an appropriate message, or continue
     * with another authentication method.
     *
     * @param error Error details from the SDK or authentication API.
     */
    fun onError(error: IPificationError)
}
