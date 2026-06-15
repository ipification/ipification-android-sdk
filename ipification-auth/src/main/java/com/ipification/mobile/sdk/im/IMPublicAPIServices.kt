package com.ipification.mobile.sdk.im

import android.app.Activity
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.ipification.mobile.sdk.im.callback.CompleteSessionCallback
import com.ipification.mobile.sdk.im.callback.CompleteStatus
import com.ipification.mobile.sdk.im.callback.IMPublicAPICallback
import com.ipification.mobile.sdk.im.callback.RedirectDataCallback
import com.ipification.mobile.sdk.im.data.IMInfo
import com.ipification.mobile.sdk.im.di.RepositoryModule
import com.ipification.mobile.sdk.im.model.IMSession
import com.ipification.mobile.sdk.im.util.IMAPI
import com.ipification.mobile.sdk.im.util.VerificationExtensionKt
import com.ipification.mobile.sdk.ip.request.AuthRequest
import com.ipification.mobile.sdk.ip.response.AuthApiResponse
import com.ipification.mobile.sdk.ip.response.IPAuthResponse
import com.ipification.mobile.sdk.ip.response.toIPAuthResponse
import com.ipification.mobile.sdk.ip.utils.IPLogs

/** Public API for the manual IM verification flow. */
class IMPublicAPIServices {

    companion object Factory {

        /** Starts IM public authentication and returns either IM session data or IP auth data. */
        @JvmStatic
        fun startAuthentication(
            activity: Activity,
            authRequest: AuthRequest,
            callback: IMPublicAPICallback
        ) {
            PublicService(activity).performAuthentication(authRequest, callback)
        }

        /** Returns IM provider links marked with installed-app availability. */
        @JvmStatic
        fun checkValidApps(
            sessionInfo: IMSession,
            packageManager: PackageManager
        ): List<IMInfo> {
            return checkInstalledApp(sessionInfo.convertToIMList(), packageManager)
        }

        /** Returns IM provider links marked with installed-app availability. */
        @JvmStatic
        fun checkInstalledApp(
            imList: List<IMInfo>,
            packageManager: PackageManager
        ): List<IMInfo> {
            return VerificationExtensionKt.checkInstalledApp(imList, packageManager)
        }

        /** Resolves an IM provider link to the final deep link. */
        @JvmStatic
        fun startGetRedirect(
            url: String,
            activity: FragmentActivity,
            callback: RedirectDataCallback
        ) {
            IMAPI.startGetRedirect(url, activity, callback)
        }

        /** Resolves an IM provider link to the final deep link. */
        @JvmStatic
        fun startGetRedirect(
            url: String,
            activity: AppCompatActivity,
            callback: RedirectDataCallback
        ) {
            IMAPI.startGetRedirect(url, activity, callback)
        }

        /** Opens an IM provider deep link. */
        @JvmStatic
        fun openAppViaDeepLink(activity: Activity, url: String) {
            IMAPI.openLink(activity, url)
        }

        /** Completes the saved IM session and returns the authorization result. */
        @JvmStatic
        fun completeSession(activity: AppCompatActivity, callback: CompleteSessionCallback) {
            val result = RepositoryModule.getInstance()
                .getSessionRepository()
                .completeSession(activity)

            if (result == null) {
                callback.onError(CompleteStatus.UNKNOWN, NO_RESPONSE_MESSAGE)
                return
            }

            result.observe(activity) { sessionResult ->
                if (sessionResult?.isSuccess == true) {
                    handleCompleteSessionSuccess(sessionResult.response, callback)
                } else {
                    handleCompleteSessionError(
                        sessionResult?.exception?.errorDescription,
                        callback
                    )
                }
            }
        }

        private fun handleCompleteSessionSuccess(
            response: AuthApiResponse?,
            callback: CompleteSessionCallback
        ) {
            IPLogs.getInstance().LOG +=
                "completeSession - success: ${response?.authorizationCode}\n"

            val ipResponse = response?.toIPAuthResponse()
            if (ipResponse == null) {
                callback.onError(
                    CompleteStatus.UNKNOWN,
                    response?.getErrorMessage() ?: NO_RESPONSE_MESSAGE
                )
                return
            }

            callback.onSuccess(
                ipResponse.code,
                ipResponse.state.orEmpty(),
                ipResponse
            )
        }

        private fun handleCompleteSessionError(
            description: String?,
            callback: CompleteSessionCallback
        ) {
            val status = CompleteStatus.fromValue(description)
            IPLogs.getInstance().LOG += "completeSession - error: $description\n"
            callback.onError(
                status,
                if (status == CompleteStatus.UNKNOWN) description.orEmpty() else ""
            )
        }

        private const val NO_RESPONSE_MESSAGE = "no response"
    }
}
