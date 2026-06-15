package com.ipification.mobile.sdk.im.repository

import android.content.Context
import com.ipification.mobile.sdk.im.data.SessionResponse
import com.ipification.mobile.sdk.im.model.IMSession
import com.ipification.mobile.sdk.im.util.SingleLiveEvent

/** Stores IM session data and delegates remote IM session operations. */
internal interface SessionRepository {

    /** Returns the current saved IM session, if available. */
    fun getSavedSessionInfo(context: Context): IMSession?

    /** Saves the active IM session. */
    fun saveSessionInfo(context: Context, imSessionInfo: IMSession)

    /** Completes the current saved IM session. */
    fun completeSession(context: Context): SingleLiveEvent<SessionResponse>?

    /** Resolves a provider URL to the final redirect link. */
    fun getRedirectLink(context: Context, url: String): SingleLiveEvent<String>?

    /** Clears the active IM session. */
    fun clearIMSession(context: Context)
}
