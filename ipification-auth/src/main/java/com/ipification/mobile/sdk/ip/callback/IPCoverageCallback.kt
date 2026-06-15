package com.ipification.mobile.sdk.ip.callback

import com.ipification.mobile.sdk.ip.exception.IPificationError
import com.ipification.mobile.sdk.ip.response.CoverageResponse

/**
 * Callback used to receive the result of an IP coverage request.
 *
 * Implement this interface and pass it to the coverage API. The SDK invokes either
 * [onSuccess] when the request completes or [onError] when the request fails.
 */
interface IPCoverageCallback {

    /**
     * Called after the coverage API returns a valid response.
     *
     * Use this callback to check whether IP authentication is available and read
     * the detected operator information.
     *
     * @param response Coverage response containing availability and operator information.
     */
    fun onSuccess(response: CoverageResponse)

    /**
     * Called when the coverage request cannot return a valid response.
     *
     * Use this callback to inspect the error and continue with another authentication
     * method or show an appropriate message.
     *
     * @param error Error details from the SDK or coverage API.
     */
    fun onError(error: IPificationError)
}
