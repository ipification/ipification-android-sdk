package com.ipification.mobile.sdk.sms.response

import org.json.JSONObject

/**
 * Response from SMS token exchange.
 *
 * This is returned after successful OTP verification and token exchange.
 * It contains user information extracted from the ID token or userinfo endpoint.
 *
 * @property sub Subject identifier (unique user ID)
 * @property phoneNumber The verified phone number
 * @property phoneNumberVerified Whether the phone number was successfully verified
 * @property loginHint The login hint used (if different from phone number)
 * @property rawResponse The raw JSON response from the backend (for debugging)
 */
data class SMSTokenResponse(
    val sub: String? = null,
    val phoneNumber: String? = null,
    val phoneNumberVerified: Boolean = false,
    val loginHint: String? = null,
    val rawResponse: String? = null
) {
    companion object {
        /** Parses the backend /sms/token response into a public SMS token model. */
        fun fromJson(rawResponse: String): SMSTokenResponse {
            val json = JSONObject(rawResponse)
            return SMSTokenResponse(
                sub = json.optNullableString("sub"),
                phoneNumber = json.optNullableString("phone_number"),
                phoneNumberVerified = json.optBooleanValue("phone_number_verified"),
                loginHint = json.optNullableString("login_hint"),
                rawResponse = rawResponse
            )
        }

        private fun JSONObject.optNullableString(name: String): String? {
            return optString(name, "").takeIf(String::isNotBlank)
        }

        private fun JSONObject.optBooleanValue(name: String): Boolean {
            return when (val value = opt(name)) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value.equals("true", ignoreCase = true) || value == "1"
                else -> false
            }
        }
    }
}
