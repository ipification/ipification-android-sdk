package com.ipification.mobile.sdk.im.di

import androidx.annotation.Keep
import com.ipification.mobile.sdk.im.data.SessionDataSource
import com.ipification.mobile.sdk.im.repository.SessionRepository
import com.ipification.mobile.sdk.im.repository.SessionRepositoryImpl

/** Provides IM repositories used by the SDK's manual and automatic IM flows. */
internal class RepositoryModule {

    @Volatile
    private var sessionRepository: SessionRepository? = null

    /** Returns the shared IM session repository. */
    fun getSessionRepository(): SessionRepository {
        return sessionRepository ?: synchronized(this) {
            sessionRepository ?: createSessionRepository().also { sessionRepository = it }
        }
    }

    private fun createSessionRepository(): SessionRepository {
        return SessionRepositoryImpl(SessionDataSource())
    }

    private object Holder {
        val INSTANCE = RepositoryModule()
    }

    @Keep
    companion object {
        /** Returns the shared repository module. */
        @JvmStatic
        fun getInstance(): RepositoryModule {
            return Holder.INSTANCE
        }
    }
}
