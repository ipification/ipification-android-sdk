package com.ipification.mobile.sdk.ts43.response

import org.json.JSONObject

/**
 * Response model for TS43 token exchange.
 * 
 * @property loginHint The phone number returned by the system.
 * @property phoneNumberVerified Whether the phone number was successfully verified.
 * @property rawResponse The raw JSON response string.
 */
data class TS43TokenResponse(
    val loginHint: String?,
    val phoneNumberVerified: Boolean,
    val rawResponse: String
) {
    companion object {
        /**
         * Parse JSON response string into TS43TokenResponse.
         * 
         * @param jsonResponse Raw JSON response from the server.
         * @return Parsed TS43TokenResponse.
         */
        fun fromJson(jsonResponse: String): TS43TokenResponse {
            val json = JSONObject(jsonResponse)
            
            val loginHint = json.optString("login_hint", "")
                .takeIf(String::isNotBlank)
            val phoneNumberVerified = parseBoolean(json.opt("phone_number_verified"))
            
            return TS43TokenResponse(
                loginHint = loginHint,
                phoneNumberVerified = phoneNumberVerified,
                rawResponse = jsonResponse
            )
        }

        private fun parseBoolean(value: Any?): Boolean {
            return when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value.equals("true", ignoreCase = true) || value == "1"
                else -> false
            }
        }
    }

    /**
     * Check if verification was successful.
     */
    fun isSuccess(): Boolean = phoneNumberVerified

    /**
     * Get the verified phone number (if available).
     */
    fun getPhoneNumber(): String? = loginHint
}
