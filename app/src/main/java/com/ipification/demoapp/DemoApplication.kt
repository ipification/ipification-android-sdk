package com.ipification.demoapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.ipification.demoapp.im.IMHelper

class DemoApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "========================================")
        Log.d(TAG, "DemoApplication.onCreate() called")
        Log.d(TAG, "========================================")
        
        // Create default notification channel for FCM
        createNotificationChannel()
        
        // Initialize Firebase as early as possible
        initializeFirebase(this)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getString(R.string.notification_channel_id)
            val channelName = "Default Notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Default notification channel for FCM messages"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $channelId")
        }
    }
    
    companion object {
        private const val TAG = "DemoApplication"
        private var isFirebaseInitialized = false
        
        /**
         * Check if Firebase is initialized and initialize it if not
         */
        fun ensureFirebaseInitialized(context: Context): Boolean {
            if (isFirebaseInitialized) {
                return true
            }
            return initializeFirebase(context)
        }

        private fun initializeFirebase(context: Context): Boolean {
            try {
                Log.d(TAG, "Starting Firebase initialization...")
                val app = FirebaseApp.initializeApp(context)
                    ?: FirebaseApp.getApps(context).firstOrNull()
                if (app == null) {
                    Log.w(TAG, "Firebase config not found. IM flow will be unavailable until google-services.json is added.")
                    isFirebaseInitialized = false
                    return false
                }

                Log.d(TAG, "Requesting FCM token...")
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.e(TAG, "Fetching FCM registration token failed", task.exception)
                        return@addOnCompleteListener
                    }

                    // Get FCM token and store it in IMHelper
                    val token = task.result
                    if (token != null) {
                        IMHelper.deviceToken = token
                        Log.d(TAG, "FCM token retrieved and stored: ${token.take(20)}...")
                    } else {
                        Log.e(TAG, "FCM token is null")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase initialization failed", e)
                return false
            }
            isFirebaseInitialized = true
            return true
        }
    }
}
