package com.ipification.mobile.sdk.sms.callback

import com.ipification.mobile.sdk.ip.exception.IPificationError
import com.ipification.mobile.sdk.sms.SMSErrorCode
import com.ipification.mobile.sdk.sms.response.SMSAuthResponse
import com.ipification.mobile.sdk.sms.response.SMSTokenResponse

/**
 * Callback interface for SMS verification operations.
 *
 * Implement this interface to handle the results of SMS verification flow.
 *
 * ## Usage Example
 *
 * ```kotlin
 * SMSServices.startVerification(
 *     activity = this,
 *     phoneNumber = "+1234567890",
 *     callback = object : SMSCallback {
 *         override fun onAuthInitiated(response: SMSAuthResponse) {
 *             // Save auth_req_id and nonce
 *             savedAuthReqId = response.authReqId
 *             savedNonce = response.nonce
 *
 *             // Show OTP input dialog to user
 *             showOTPInputDialog()
 *         }
 *
 *         override fun onSuccess(response: SMSTokenResponse) {
 *             // Verification complete
 *             if (response.phoneNumberVerified) {
 *                 // Navigate to success screen
 *             }
 *         }
 *
 *         override fun onError(error: IPificationError) {
 *             // Handle error
 *             when (error.sdkErrorCode) {
 *                 SMSErrorCode.NETWORK_ERROR -> showNetworkError()
 *                 SMSErrorCode.UNAUTHORIZED -> showAuthError()
 *                 else -> showGenericError(error.serverDescription)
 *             }
 *         }
 *     }
 * )
 * ```
 */
interface SMSCallback {

    /**
     * Called when the SMS auth initiation is successful.
     *
     * This indicates the OTP has been sent to the user's phone.
     * You should save the auth_req_id and nonce, then prompt the user to enter the OTP.
     *
     * @param response The auth initiation response containing auth_req_id and nonce
     */
    fun onAuthInitiated(response: SMSAuthResponse)

    /**
     * Called when the SMS verification is successful.
     *
     * This is called after the user enters the correct OTP and token exchange is complete.
     *
     * @param response The token response containing user info (phone_number, phone_number_verified, etc.)
     */
    fun onSuccess(response: SMSTokenResponse)

    /**
     * Called when an error occurs during SMS verification.
     *
     * @param error The error details including error code and description
     */
    fun onError(error: IPificationError)
}
