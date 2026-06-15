package com.ipification.demoapp.im

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ipification.demoapp.R
import com.ipification.mobile.sdk.im.IMService

/**
 * Firebase Messaging Service for handling IM authentication notifications
 */
class CustomFirebaseMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "CustomFMS"
    }
    
    /**
     * Called when message is received from Firebase Cloud Messaging
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")
        
        // Check if message contains a data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }
        
        // Check if message contains a notification payload
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Message Notification Body: ${notification.body}")
            sendNotification(notification.body)
        }
    }
    
    /**
     * Called when FCM registration token is updated
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed FCM token: $token")
        
        // Update token in IMHelper and register with backend
        IMHelper.deviceToken = token
        IMHelper.registerDevice(token)
    }
    
    /**
     * Handle data message payload
     */
    private fun handleDataMessage(data: Map<String, String>) {
        Log.d(TAG, "Handling data message")
        sendNotification(data["body"])
    }
    
    /**
     * Show IM notification using IPification SDK
     */
    private fun sendNotification(messageBody: String?) {
        try {
            // Use IPification SDK to show notification
            IMService.showIPNotification(
                context = this,
                notificationTitle = getString(R.string.app_name),
                messageBody = messageBody ?: "IM Authentication Request",
                ic_notification = R.drawable.ic_stat_name
            )
            Log.d(TAG, "IM notification shown: $messageBody")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show IM notification", e)
        }
    }
}
