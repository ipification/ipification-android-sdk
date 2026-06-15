package com.ipification.mobile.sdk.ts43.response

import org.json.JSONArray
import org.json.JSONObject

/**
 * Response model for TS43 CIBA authentication request.
 * 
 * @property authReqId Unique identifier for this authentication session.
 * @property digitalRequest The digital credential request JSON to pass to Credential Manager.
 * @property expiresIn Session expiration time in seconds.
 * @property interval Recommended polling interval (if applicable).
 * @property rawResponse The raw JSON response string.
 */
data class TS43AuthResponse(
    val authReqId: String,
    val digitalRequest: String,
    val expiresIn: Int,
    val interval: Int,
    val rawResponse: String
) {
    companion object {
        /**
         * Parse JSON response string into TS43AuthResponse.
         * 
         * @param jsonResponse Raw JSON response from the server.
         * @return Parsed TS43AuthResponse.
         * @throws IllegalArgumentException if required fields are missing.
         */
        fun fromJson(jsonResponse: String): TS43AuthResponse {
            val json = JSONObject(jsonResponse)
            
            val authReqId = json.optString("auth_req_id", "")
            val digitalRequestObj = json.optJSONObject("digital_request")
            val digitalRequest = digitalRequestObj?.toString() ?: ""
            
            require(authReqId.isNotBlank()) { "Missing 'auth_req_id' in response" }
            require(digitalRequest.isNotBlank()) { "Missing 'digital_request' in response" }
            
            return TS43AuthResponse(
                authReqId = authReqId,
                digitalRequest = digitalRequest,
                expiresIn = json.optInt("expires_in", 120),
                interval = json.optInt("interval", 5),
                rawResponse = jsonResponse
            )
        }
    }

    /**
     * Get the formatted request JSON for Credential Manager.
     * Wraps the digital_request in the expected format: {"requests": [digital_request]}
     */
    fun getCredentialManagerRequestJson(): String {
        return JSONObject()
            .put("requests", JSONArray().put(JSONObject(digitalRequest)))
            .toString()
    }
}
