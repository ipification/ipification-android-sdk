package com.ipification.mobile.sdk.im

import com.ipification.mobile.sdk.ip.IPConfiguration

import android.app.Activity
import com.ipification.mobile.sdk.ip.InternalService
import com.ipification.mobile.sdk.ip.common.internal.CellularCallback
import com.ipification.mobile.sdk.ip.exception.CellularException
import com.ipification.mobile.sdk.ip.request.AuthRequest
import com.ipification.mobile.sdk.ip.response.AuthApiResponse
import com.ipification.mobile.sdk.ip.response.toIPAuthResponse
import com.ipification.mobile.sdk.im.IMLocale
import com.ipification.mobile.sdk.im.IMTheme
import com.ipification.mobile.sdk.im.callback.IMAutoModeCallback
import com.ipification.mobile.sdk.im.callback.IMCallback

class IMAutoModeServices {
    companion object Factory {
        var theme: IMTheme? = null
        var locale: IMLocale? = null


        /**
         * perform IM AutoMode Authorization (IM Only - channel doesn't include `ip`)
         * @param activity : Activity
         * @param authRequest : AuthRequest
         * @param callback : IMAutoModeCallback
         */
        fun startAuthentication(
            activity: Activity,
            authRequest: AuthRequest,
            callback: IMAutoModeCallback
        ) {
            val authService = InternalService<AuthApiResponse>(activity)
            val cb = object : CellularCallback<AuthApiResponse>{
                override fun onSuccess(response: AuthApiResponse) {
                    callback.onSuccess()
                }

                override fun onError(error: CellularException) {
                    callback.onError(error.toIPificationError())
                }
                override fun onIMCancel() {
                    try{
                        callback.onCancel()
                    }catch (e: NoClassDefFoundError){

                    }
                }
            }
            authService.imTheme = theme
            authService.imLocale = locale
            authService.performAuthentication(activity, authRequest, cb)
        }
        /**
         * perform IM AutoMode Authorization (IM with IP - channel includes `ip`)
         * @param activity : Activity
         * @param authRequest : AuthRequest
         * @param callback : IMCallback
         */
        fun startAuthentication(
            activity: Activity,
            authRequest: AuthRequest,
            callback: IMCallback
        ) {
            val authService = InternalService<AuthApiResponse>(activity)
            val cb = object : CellularCallback<AuthApiResponse>{
                override fun onSuccess(response: AuthApiResponse) {
                    response.toIPAuthResponse()?.let { callback.onSuccess(it) }
                }

                override fun onError(error: CellularException) {
                    callback.onError(error.toIPificationError())
                }
                override fun onIMCancel() {
                    try{
                        callback.onIMCancel()
                    }catch (e: NoClassDefFoundError){

                    }
                }
            }
            authService.imTheme = theme
            authService.imLocale = locale
            authService.performAuthentication(activity, authRequest, cb)
        }
    }


}
