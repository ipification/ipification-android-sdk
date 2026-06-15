package com.ipification.mobile.sdk.ts43.request

import org.json.JSONObject

/**
 * Request model for TS43 token exchange.
 * 
 * @property vpToken The VP (Verifiable Presentation) token received from Credential Manager.
 * @property authReqId The authentication request ID from the initial CIBA auth response.
 * @property clientId OAuth client ID.
 * @property customParams Extra partner-specific parameters sent with the token request.
 */
data class TS43TokenRequest(
    val vpToken: String,
    val authReqId: String,
    val clientId: String,
    val customParams: Map<String, String>? = null
) {
    /**
     * Convert the token exchange request into the JSON body expected by /ts43/token.
     */
    fun toJsonString(): String {
        return JSONObject().apply {
            put("vp_token", vpToken)
            put("auth_req_id", authReqId)
            put("client_id", clientId)
            
            // Include partner-specific token fields after the standard TS43 fields.
            customParams?.let { params ->
                for ((key, value) in params) {
                    put(key, value)
                }
            }
        }.toString()
    }
}
