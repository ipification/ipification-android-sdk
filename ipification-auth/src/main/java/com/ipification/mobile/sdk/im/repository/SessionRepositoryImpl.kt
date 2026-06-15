package com.ipification.mobile.sdk.im.repository

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.im.data.SessionDataSource
import com.ipification.mobile.sdk.im.data.SessionResponse
import com.ipification.mobile.sdk.im.model.IMSession
import com.ipification.mobile.sdk.im.util.SingleLiveEvent

/** Default IM session repository backed by memory and optional shared preferences. */
internal class SessionRepositoryImpl(
    private val remoteDataSource: SessionDataSource
) : SessionRepository {

    private var sessionInfo: IMSession? = null

    /** Returns the in-memory session, or restores it from preferences when configured. */
    override fun getSavedSessionInfo(context: Context): IMSession? {
        if (!IPConfiguration.getInstance().enable_Save_Session_In_Preference) {
            return sessionInfo
        }

        val preferences = context.applicationContext
            .getSharedPreferences(PREFERENCE_FILE_NAME, MODE_PRIVATE)
        if (!preferences.contains(KEY_SESSION_ID)) {
            return sessionInfo
        }

        return IMSession(
            sessionId = preferences.getString(KEY_SESSION_ID, null),
            completeSessionUrl = preferences.getString(KEY_COMPLETE_SESSION_URL, null),
            waLink = preferences.getString(KEY_WA_LINK, null),
            telegramLink = preferences.getString(KEY_TELEGRAM_LINK, null),
            viberLink = preferences.getString(KEY_VIBER_LINK, null)
        ).also { sessionInfo = it }
    }

    /** Saves the current session in memory and, when enabled, preferences. */
    override fun saveSessionInfo(context: Context, imSessionInfo: IMSession) {
        sessionInfo = imSessionInfo
        if (!IPConfiguration.getInstance().enable_Save_Session_In_Preference) return

        context.applicationContext
            .getSharedPreferences(PREFERENCE_FILE_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSION_ID, imSessionInfo.sessionId)
            .putString(KEY_COMPLETE_SESSION_URL, imSessionInfo.completeSessionUrl)
            .putString(KEY_WA_LINK, imSessionInfo.waLink)
            .putString(KEY_TELEGRAM_LINK, imSessionInfo.telegramLink)
            .putString(KEY_VIBER_LINK, imSessionInfo.viberLink)
            .apply()
    }

    /** Completes the saved session when a valid session ID is available. */
    override fun completeSession(context: Context): SingleLiveEvent<SessionResponse>? {
        val session = getSavedSessionInfo(context)
            ?.takeIf { !it.sessionId.isNullOrBlank() }
            ?: return null

        return remoteDataSource.completeSession(context, session)
    }

    /** Resolves an IM provider URL through the remote data source. */
    override fun getRedirectLink(context: Context, url: String): SingleLiveEvent<String>? {
        return remoteDataSource.getRedirectLink(context, url)
    }

    /** Clears the in-memory and persisted session data. */
    override fun clearIMSession(context: Context) {
        sessionInfo = null
        if (!IPConfiguration.getInstance().enable_Save_Session_In_Preference) return

        context.applicationContext
            .getSharedPreferences(PREFERENCE_FILE_NAME, MODE_PRIVATE)
            .edit()
            .remove(KEY_SESSION_ID)
            .remove(KEY_COMPLETE_SESSION_URL)
            .remove(KEY_WA_LINK)
            .remove(KEY_TELEGRAM_LINK)
            .remove(KEY_VIBER_LINK)
            .apply()
    }

    private companion object {
        const val PREFERENCE_FILE_NAME = "imsession"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_COMPLETE_SESSION_URL = "complete_session_url"
        const val KEY_WA_LINK = "wa_link"
        const val KEY_TELEGRAM_LINK = "telegram_link"
        const val KEY_VIBER_LINK = "viber_link"
    }
}
