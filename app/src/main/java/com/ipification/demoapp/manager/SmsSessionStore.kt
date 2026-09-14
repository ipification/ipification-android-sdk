package com.ipification.demoapp.manager

import com.ipification.mobile.sdk.sms.response.SMSAuthResponse

/**
 * Keeps the last [SMSAuthResponse] between the "OTP sent" step and the OTP screen.
 *
 * The SDK captures the SMS channel options (token params, headers) inside the response, so the OTP
 * screen can complete verification with `IPificationServices.verifySMSOTP(activity, otp, session, cb)`
 * without re-supplying `server_id` or any other partner parameter.
 */
object SmsSessionStore {
    @Volatile
    var pending: SMSAuthResponse? = null

    /** Returns the stored session when it matches [authReqId]; null otherwise. */
    fun sessionFor(authReqId: String): SMSAuthResponse? =
        pending?.takeIf { it.authReqId == authReqId }
}
