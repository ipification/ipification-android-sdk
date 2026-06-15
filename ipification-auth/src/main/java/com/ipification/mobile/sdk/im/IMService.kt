package com.ipification.mobile.sdk.im

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.im.di.RepositoryModule
import com.ipification.mobile.sdk.im.model.IMSession
import com.ipification.mobile.sdk.im.repository.SessionRepository
import com.ipification.mobile.sdk.im.ui.IMVerificationActivity
import com.ipification.mobile.sdk.im.util.VerificationExtensionKt
import com.ipification.mobile.sdk.ip.utils.IPConstant
import com.ipification.mobile.sdk.ip.utils.IPLogs

/**
 * Coordinates the IM verification screen, activity result, saved session, and return notification.
 *
 * Only one IM verification flow can be active at a time.
 */
class IMService {

    companion object Factory {
        internal var theme: IMTheme? = null
        internal var locale: IMLocale = IMLocale()

        private var completeListener: VerifyCompleteListener? = null

        /**
         * Saves the IM session and opens the verification screen.
         */
        @Suppress("DEPRECATION")
        @JvmStatic
        fun startVerification(
            activity: Activity,
            theme: IMTheme?,
            locale: IMLocale?,
            imSessionInfo: IMSession,
            verifyListener: VerifyCompleteListener
        ) {
            completeListener = verifyListener
            this.theme = theme
            if (locale != null) {
                this.locale = locale
            }
            sessionRepository().saveSessionInfo(activity.applicationContext, imSessionInfo)

            if (IPConfiguration.getInstance().validateIMApps &&
                !VerificationExtensionKt.validateInstallApp(
                    imSessionInfo.convertToIMList(),
                    activity.packageManager
                )
            ) {
                completeListener = null
                verifyListener.onFail(NO_SUPPORTED_PROVIDER_ERROR)
                return
            }

            val intent = Intent(activity, IMVerificationActivity::class.java)
                .putExtra(INIT_EXTRA, true)
            activity.startActivityForResult(intent, IPConfiguration.getInstance().REQUEST_CODE)
        }

        /** Clears the saved IM verification session. */
        @JvmStatic
        fun clearIMSession(context: Context) {
            sessionRepository().clearIMSession(context)
        }

        /**
         * Processes the result returned by the IM verification activity.
         *
         * Host activities using the legacy result API should forward their `onActivityResult` here.
         */
        @JvmStatic
        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode != IPConfiguration.getInstance().REQUEST_CODE) return

            val listener = completeListener ?: return
            completeListener = null

            runCatching {
                when {
                    resultCode != Activity.RESULT_OK -> {
                        logResult("failed: USER_CANCELED")
                        listener.onCancel()
                    }

                    IPConfiguration.getInstance().IM_AUTO_MODE -> {
                        listener.onSuccess(
                            data?.getStringExtra(IPConstant.IM_SESSION_ID).orEmpty(),
                            data?.getStringExtra(IPConstant.IP_RESPONSE_DATA)
                        )
                    }

                    else -> processManualResult(data, listener)
                }
            }.onFailure {
                listener.onFail(it.localizedMessage ?: UNKNOWN_RESULT_ERROR)
            }
        }

        /**
         * Returns whether the IM verification activity remains in the application's task.
         */
        @Suppress("DEPRECATION")
        @JvmStatic
        fun isNotificationActivityRunning(context: Context): Boolean {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val verificationActivityName = IMVerificationActivity::class.java.canonicalName
            return activityManager.getRunningTasks(MAX_TASKS_TO_CHECK).any { task ->
                task.topActivity?.className == verificationActivityName
            }
        }

        /** Shows a return-to-verification notification using the supplied channel details. */
        @JvmStatic
        fun showIPNotification(
            context: Context,
            notificationTitle: String?,
            messageBody: String?,
            notificationChannelId: String,
            notificationChannelName: String,
            ic_notification: Int
        ) {
            if (!isNotificationActivityRunning(context)) return
            showNotification(
                context,
                notificationTitle,
                messageBody,
                notificationChannelId,
                notificationChannelName,
                ic_notification
            )
        }

        /** Shows a return-to-verification notification using the default SDK channel. */
        @JvmStatic
        fun showIPNotification(
            context: Context,
            notificationTitle: String?,
            messageBody: String,
            ic_notification: Int
        ) {
            showNotification(
                context,
                notificationTitle,
                messageBody,
                DEFAULT_NOTIFICATION_CHANNEL_ID,
                DEFAULT_NOTIFICATION_CHANNEL_NAME,
                ic_notification
            )
        }

        /** Returns the application label shown to users. */
        @JvmStatic
        fun getApplicationName(context: Context): String {
            return context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        }

        private fun processManualResult(data: Intent?, listener: VerifyCompleteListener) {
            val responseData = data?.getStringExtra(IPConstant.IP_RESPONSE_DATA)
            val sessionId = data?.getStringExtra(IPConstant.IM_SESSION_ID)
            val errorMessage = data?.getStringExtra(IPConstant.ERROR_MESSAGE)

            if (!sessionId.isNullOrBlank() && !responseData.isNullOrBlank()) {
                logResult("success")
                listener.onSuccess(sessionId, responseData)
            } else {
                logResult("failed: ${errorMessage.orEmpty()}")
                listener.onFail(errorMessage ?: UNKNOWN_RESULT_ERROR)
            }
        }

        private fun showNotification(
            context: Context,
            title: String?,
            message: String?,
            channelId: String,
            channelName: String,
            iconResource: Int
        ) {
            if (!canPostNotifications(context)) return

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        channelName,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        enableVibration(true)
                    }
                )
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                IPConfiguration.getInstance().REQUEST_CODE,
                Intent(context, IMVerificationActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(iconResource)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVibrate(LongArray(0))
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(IPConfiguration.getInstance().NOTIFICATION_ID, notification)
        }

        private fun canPostNotifications(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        }

        private fun sessionRepository(): SessionRepository {
            return RepositoryModule.getInstance().getSessionRepository()
        }

        private fun logResult(message: String) {
            IPLogs.getInstance().LOG += "onActivityResult - $message\n"
        }

        private const val DEFAULT_NOTIFICATION_CHANNEL_ID = "ip_notification_cid"
        private const val DEFAULT_NOTIFICATION_CHANNEL_NAME = "ip_notification"
        private const val INIT_EXTRA = "init"
        private const val MAX_TASKS_TO_CHECK = 100
        private const val NO_SUPPORTED_PROVIDER_ERROR =
            "Error: No supported messaging apps are available on your phone."
        private const val UNKNOWN_RESULT_ERROR = "onActivityResult - failed: unknown error"
    }
}
