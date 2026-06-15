package com.ipification.mobile.sdk.ts43.callback

import com.ipification.mobile.sdk.ip.exception.IPificationError
import com.ipification.mobile.sdk.ts43.response.TS43TokenResponse

/**
 * Callback interface for TS43 authentication flow.
 * 
 * Implement this interface to receive the result of TS43 phone verification.
 */
interface TS43Callback {
    /**
     * Called when TS43 authentication completes successfully.
     * 
     * @param response The token response containing verification result and phone number.
     */
    fun onSuccess(response: TS43TokenResponse)

    /**
     * Called when TS43 authentication fails.
     * 
     * @param error The error details including error code and description.
     */
    fun onError(error: IPificationError)

    /**
     * Called when IPification fallback returns an authorization code.
     * Note: This method is deprecated as the SDK now automatically exchanges the code for a token.
     *
     * @param code The authorization code from IPification flow.
     */
    @Deprecated("SDK now automatically exchanges code for token. This callback is no longer called in standard flow.")
    fun onIPCodeReceived(code: String) {
        // Default empty implementation for backward compatibility
    }
}
