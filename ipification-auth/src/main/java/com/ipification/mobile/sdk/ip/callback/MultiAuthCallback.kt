package com.ipification.mobile.sdk.ip.callback

import com.ipification.mobile.sdk.sms.response.SMSAuthResponse

interface MultiAuthCallback : IPAuthCallback {
    fun onOTPRequired(response: SMSAuthResponse)
}
