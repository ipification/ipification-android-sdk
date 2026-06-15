package com.ipification.mobile.sdk.im.internal.callback

import com.ipification.mobile.sdk.ip.exception.CellularException
import com.ipification.mobile.sdk.ip.response.AuthApiResponse

internal interface SessionDataCallback {
    fun onSuccess(res: AuthApiResponse)
    fun onError(error: CellularException)
}
