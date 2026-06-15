package com.ipification.mobile.sdk.im.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.ipification.mobile.sdk.im.callback.RedirectDataCallback
import com.ipification.mobile.sdk.im.di.RepositoryModule
import com.ipification.mobile.sdk.ip.utils.IPLogs

/** Internal helper for resolving and opening IM provider deep links. */
internal class IMAPI {

    companion object Factory {

        /** Resolves the final IM provider deep link for a fragment activity host. */
        @JvmStatic
        fun startGetRedirect(
            url: String,
            activity: FragmentActivity,
            callback: RedirectDataCallback
        ) {
            resolveRedirect(url, activity, activity, callback)
        }

        /** Resolves the final IM provider deep link for an appcompat activity host. */
        @JvmStatic
        fun startGetRedirect(
            url: String,
            activity: AppCompatActivity,
            callback: RedirectDataCallback
        ) {
            resolveRedirect(url, activity, activity, callback)
        }

        /** Opens a deep link and returns whether Android accepted the intent. */
        @JvmStatic
        fun openLink(activity: Activity, url: String): Boolean {
            IPLogs.getInstance().LOG += "openLink: $url\n"

            return runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                activity.startActivity(intent)
                true
            }.getOrElse { error ->
                val message = when (error) {
                    is ActivityNotFoundException -> "No activity found to open IM link."
                    else -> error.message ?: "Unable to open IM link."
                }
                Log.d(LOG_TAG, message, error)
                IPLogs.getInstance().LOG += "openLink error: $message\n"
                false
            }
        }

        private fun resolveRedirect(
            url: String,
            context: android.content.Context,
            lifecycleOwner: LifecycleOwner,
            callback: RedirectDataCallback
        ) {
            IPLogs.getInstance().LOG += "startGetRedirect url $url\n"

            val result = RepositoryModule.getInstance()
                .getSessionRepository()
                .getRedirectLink(context, url)

            if (result == null) {
                callback.onResponse(url)
                return
            }

            result.observe(lifecycleOwner) { redirectUrl ->
                val resolvedUrl = redirectUrl.takeUnless(String?::isNullOrBlank) ?: url
                Log.d(LOG_TAG, "getRedirectLink dest url: $resolvedUrl")
                callback.onResponse(resolvedUrl)
            }
        }

        private const val LOG_TAG = "IMAPI"
    }
}
