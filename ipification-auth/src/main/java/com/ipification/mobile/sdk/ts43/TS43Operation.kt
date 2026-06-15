package com.ipification.mobile.sdk.ts43

/**
 * Enum representing the type of TS43 operation.
 */
enum class TS43Operation(val value: String, val scope: String) {
    /**
     * Verify that a provided phone number matches the SIM card's number.
     * Requires login_hint (phone number) to be provided.
     */
    VERIFY_PHONE_NUMBER("VerifyPhoneNumber", "openid ip:phone_verify"),

    /**
     * Get the phone number from the SIM card without providing one.
     * No login_hint required - uses "anonymous".
     */
    GET_PHONE_NUMBER("GetPhoneNumber", "openid ip:phone")
}
