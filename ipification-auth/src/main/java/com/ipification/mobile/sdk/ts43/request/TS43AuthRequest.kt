package com.ipification.mobile.sdk.ts43.request

import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ts43.TS43Operation
import org.json.JSONObject

/**
 * Request model for TS43 CIBA authentication.
 * 
 * @property loginHint Login hint value for VerifyPhoneNumber. A leading '+' is removed for phone numbers.
 *                     or null/empty for GetPhoneNumber operation.
 * @property carrierHint MCC+MNC value used to route the TS43 request.
 * @property clientId OAuth client ID provided by IPification.
 * @property scope OAuth scope for the operation.
 * @property operation The type of TS43 operation (VerifyPhoneNumber or GetPhoneNumber).
 * @property customParams Extra parameters sent only with the /ts43/auth request.
 * @property tokenCustomParams Extra parameters carried forward to the /ts43/token request.
 */
data class TS43AuthRequest(
    val loginHint: String?,
    val carrierHint: String,
    val clientId: String,
    val scope: String,
    val operation: TS43Operation,
    val customParams: Map<String, String>? = null,
    val tokenCustomParams: Map<String, String>? = null
) {
    /**
     * Convert the auth request into the JSON body expected by /ts43/auth.
     */
    fun toJsonString(): String {
        return JSONObject().apply {
            put("login_hint", loginHint ?: "")
            put("carrier_hint", carrierHint)
            put("client_id", clientId)
            put("scope", scope)
            put("operation", operation.value)
            
            // Include partner-specific auth fields after the standard TS43 fields.
            customParams?.let { params ->
                for ((key, value) in params) {
                    put(key, value)
                }
            }
        }.toString()
    }

    /**
     * Builder for creating a TS43 auth request with configuration defaults.
     */
    class Builder {
        private var loginHint: String? = null
        private var carrierHint: String = ""
        private var clientId: String = ""
        private var scope: String? = null
        private var operation: TS43Operation = TS43Operation.VERIFY_PHONE_NUMBER
        private val customParams: MutableMap<String, String> = mutableMapOf()
        private val tokenCustomParams: MutableMap<String, String> = mutableMapOf()

        /**
         * Set the login hint value to verify (for VerifyPhoneNumber operation).
         * @param loginHint Login hint value. A leading '+' is removed for phone numbers.
         */
        fun setLoginHint(loginHint: String?): Builder {
            this.loginHint = loginHint?.removePrefix("+")
            return this
        }

        /**
         * Set the carrier hint (MCC+MNC).
         * @param mccMnc Combined MCC and MNC string (e.g., "310260").
         */
        fun setCarrierHint(mccMnc: String): Builder {
            this.carrierHint = mccMnc
            return this
        }

        /**
         * Set the client ID.
         * @param clientId OAuth client ID.
         */
        fun setClientId(clientId: String): Builder {
            this.clientId = clientId
            return this
        }

        /**
         * Set the OAuth scope.
         * @param scope OAuth scope string.
         */
        fun setScope(scope: String): Builder {
            this.scope = scope
            return this
        }

        /**
         * Set the operation type.
         * @param operation TS43Operation enum value.
         */
        fun setOperation(operation: TS43Operation): Builder {
            this.operation = operation
            return this
        }

        /**
         * Add a partner-specific parameter to the TS43 auth request body.
         * @param key Parameter name.
         * @param value Parameter value.
         */
        fun addCustomParam(key: String, value: String): Builder {
            this.customParams[key] = value
            return this
        }

        /**
         * Add a partner-specific parameter to the later TS43 token exchange body.
         * @param key Parameter name.
         * @param value Parameter value.
         */
        fun addTokenCustomParam(key: String, value: String): Builder {
            this.tokenCustomParams[key] = value
            return this
        }

        /**
         * Build the TS43AuthRequest.
         * @return Configured TS43AuthRequest instance.
         * @throws IllegalArgumentException if required fields are missing.
         */
        fun build(): TS43AuthRequest {
            val config = IPConfiguration.getInstance()
            
            // Use the configured carrier hint when one is not supplied explicitly.
            val finalCarrierHint = carrierHint.ifBlank { config.TS43_DEFAULT_CARRIER_HINT }

            // Use operation-specific scope if not explicitly set
            val finalScope = scope ?: if (operation == TS43Operation.VERIFY_PHONE_NUMBER) {
                config.TS43_SCOPE_VERIFY_PHONE
            } else {
                config.TS43_SCOPE_GET_PHONE
            }

            // Use configured client ID if not explicitly set
            val finalClientId = clientId.ifBlank { config.CLIENT_ID }

            require(finalClientId.isNotBlank()) { "CLIENT_ID is required" }

            // For VerifyPhoneNumber, login_hint is required
            if (operation == TS43Operation.VERIFY_PHONE_NUMBER) {
                require(!loginHint.isNullOrBlank()) { "Phone number (login_hint) is required for VerifyPhoneNumber operation" }
            }

            return TS43AuthRequest(
                loginHint = loginHint,
                carrierHint = finalCarrierHint,
                clientId = finalClientId,
                scope = finalScope,
                operation = operation,
                customParams = if (customParams.isNotEmpty()) customParams.toMap() else null,
                tokenCustomParams = if (tokenCustomParams.isNotEmpty()) tokenCustomParams.toMap() else null
            )
        }
    }
}
