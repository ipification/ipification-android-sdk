package com.ipification.mobile.sdk.sms.response

import com.ipification.mobile.sdk.ip.request.SMSChannelOptions
import org.json.JSONObject

/**
 * Response from SMS auth initiation.
 *
 * This is returned when the backend successfully initiates the CIBA flow
 * and the auth server has sent the OTP to the user's phone.
 *
 * @property authReqId The CIBA auth request ID (used for token exchange)
 * @property nonce Session/correlation ID (used for token exchange)
 * @property authServer Information about the auth server used
 * @property options Channel options captured when verification started; the SDK reuses their
 *   token parameters and headers for the OTP verification request.
 */
data class SMSAuthResponse(
    val authReqId: String,
    val nonce: String,
    val authServer: AuthServer? = null,
    val options: SMSChannelOptions = SMSChannelOptions.EMPTY
) {
    companion object {
        /**
         * Parses the backend /sms/auth response and validates the fields required for OTP verification.
         */
        @JvmStatic
        @JvmOverloads
        fun fromJson(
            rawResponse: String,
            options: SMSChannelOptions = SMSChannelOptions.EMPTY
        ): SMSAuthResponse {
            val json = JSONObject(rawResponse)
            val authReqId = json.optString("auth_req_id", "").takeIf(String::isNotBlank)
            val nonce = json.optString("nonce", "").takeIf(String::isNotBlank)

            require(!authReqId.isNullOrBlank()) { "Missing 'auth_req_id' in SMS auth response" }
            require(!nonce.isNullOrBlank()) { "Missing 'nonce' in SMS auth response" }

            return SMSAuthResponse(
                authReqId = authReqId.orEmpty(),
                nonce = nonce.orEmpty(),
                authServer = json.optJSONObject("auth_server")?.toAuthServer(),
                options = options
            )
        }

        private fun JSONObject.toAuthServer(): AuthServer? {
            val id = optString("id", "").takeIf(String::isNotBlank)
            val url = optString("url", "").takeIf(String::isNotBlank)
            return if (id != null || url != null) {
                AuthServer(id = id.orEmpty(), url = url.orEmpty())
            } else {
                null
            }
        }
    }

    /**
     * Information about the auth server used for this verification.
     *
     * @property id The server ID (e.g., "stage", "production")
     * @property url The server URL
     */
    data class AuthServer(
        val id: String,
        val url: String
    )
}
