package com.ipification.mobile.sdk.im

import android.app.Activity
import android.content.Context
import android.util.Log
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.SubmitErrorService
import com.ipification.mobile.sdk.ip.common.internal.CellularCallback
import com.ipification.mobile.sdk.im.callback.IMCallback
import com.ipification.mobile.sdk.ip.InternalService
import com.ipification.mobile.sdk.ip.exception.CellularException
import com.ipification.mobile.sdk.ip.network.NetworkManager
import com.ipification.mobile.sdk.ip.request.AuthRequest
import com.ipification.mobile.sdk.ip.response.AuthApiResponse
import com.ipification.mobile.sdk.ip.response.CoverageResponse
import com.ipification.mobile.sdk.ip.response.toIPAuthResponse
import com.ipification.mobile.sdk.ip.utils.ErrorCode
import com.ipification.mobile.sdk.ip.utils.ErrorMessages
import java.util.concurrent.atomic.AtomicBoolean

/** Provides coverage and authentication operations for the IM flow. */
class IMServices {

    companion object Factory {
        private const val LOG_TAG = "IMServices"

        /** Appearance applied to the IM verification screen. */
        var theme: IMTheme? = null

        /** Text and locale overrides applied to the IM verification screen. */
        var locale: IMLocale? = null

//        private val isCoverageRequestInProgress = AtomicBoolean(false)
        private val isAuthRequestInProgress = AtomicBoolean(false)


        /** Starts IP authentication with optional IM verification fallback. */
        fun startAuthentication(
            activity: Activity,
            authRequest: AuthRequest,
            callback: IMCallback
        ) {
            if (!acquireRequest(isAuthRequestInProgress, "Authentication")) return

            val hasDeliveredResult = AtomicBoolean(false)
            val authService = InternalService<AuthApiResponse>(activity).apply {
                imTheme = theme
                imLocale = locale
            }

            val serviceCallback = object : CellularCallback<AuthApiResponse> {
                override fun onSuccess(response: AuthApiResponse) {
                    finishOnce(hasDeliveredResult, isAuthRequestInProgress) {
                        val authResponse = response.toIPAuthResponse()
                        if (authResponse != null) {
                            callback.onSuccess(authResponse)
                        } else {
                            deliverAuthError(
                                activity = activity,
                                authRequest = authRequest,
                                error = CellularException().apply {
                                    sdkErrorCode = ErrorCode.SERVER_RESPONSE_FAILED
                                    errorDescription = ErrorMessages.EMPTY_RESPONSE_ERROR
                                },
                                callback = callback
                            )
                        }
                    }
                }

                override fun onError(error: CellularException) {
                    finishOnce(hasDeliveredResult, isAuthRequestInProgress) {
                        deliverAuthError(activity, authRequest, error, callback)
                    }
                }

                override fun onIMCancel() {
                    finishOnce(hasDeliveredResult, isAuthRequestInProgress, callback::onIMCancel)
                }
            }

            authService.performAuthentication(activity, authRequest, serviceCallback)
        }


        /** Atomically reserves an operation when single-request mode is enabled. */
        private fun acquireRequest(requestState: AtomicBoolean, operation: String): Boolean {
            if (!IPConfiguration.getInstance().enableSingleRequest) {
                requestState.set(true)
                return true
            }

            val acquired = requestState.compareAndSet(false, true)
            if (!acquired) {
                Log.e(LOG_TAG, "$operation request is already in progress")
            }
            return acquired
        }

        /** Delivers the first terminal event and releases its in-progress state. */
        private inline fun finishOnce(
            hasDeliveredResult: AtomicBoolean,
            requestState: AtomicBoolean,
            action: () -> Unit = {}
        ) {
            if (!hasDeliveredResult.compareAndSet(false, true)) return

            requestState.set(false)
            action()
        }

        private fun deliverAuthError(
            activity: Activity,
            authRequest: AuthRequest,
            error: CellularException,
            callback: IMCallback
        ) {
            callback.onError(error.toIPificationError())
            SubmitErrorService().sendErrorReport(
                context = activity,
                apiType = IPConfiguration.getInstance().AUTH_API_STR,
                errorDescription = error.getErrorMessage(),
                errorCode = "${error.sdkErrorCode}|${error.errorCode.orEmpty()}",
                phoneNumber = authRequest.queryParameters?.get("login_hint")
            )
        }
    }
}
