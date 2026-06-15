package com.ipification.mobile.sdk.ip.utils

import android.os.Build
import android.util.Log
import androidx.annotation.Keep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Internal timestamp formatting and Android log-level configuration. */
internal class LogUtils private constructor() {

    @Keep
    companion object {
        private val levelLock = Any()
        private val enabledLevels = mutableSetOf<LogLevel>()

        /** Returns the current local timestamp used in retained SDK diagnostic logs. */
        fun currentTimestamp(): String {
            return SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US).format(Date())
        }

        /** Enables one log level, or every supported level when [LogLevel.ALL] is supplied. */
        fun addLevel(logLevel: LogLevel) {
            addLevel(*arrayOf(logLevel))
        }

        /** Enables the supplied log levels. */
        fun addLevel(vararg logLevels: LogLevel) {
            synchronized(levelLock) {
                if (LogLevel.ALL in logLevels) {
                    enabledLevels.addAll(INDIVIDUAL_LEVELS)
                } else {
                    enabledLevels.addAll(logLevels)
                }
            }
        }

        /** Disables one log level. */
        fun removeLevel(logLevel: LogLevel) {
            synchronized(levelLock) {
                if (logLevel == LogLevel.ALL) {
                    enabledLevels.clear()
                } else {
                    enabledLevels.remove(logLevel)
                }
            }
        }

        /** Returns whether [logLevel] is currently enabled. */
        fun containsLevel(logLevel: LogLevel): Boolean {
            return synchronized(levelLock) {
                logLevel == LogLevel.ALL && enabledLevels.containsAll(INDIVIDUAL_LEVELS) ||
                    logLevel in enabledLevels
            }
        }

        private const val TIMESTAMP_PATTERN = "dd/MM/yyyy HH:mm:ss.SSS"
        private val INDIVIDUAL_LEVELS = LogLevel.entries.filterNot { it == LogLevel.ALL }
    }
}

/** Android log levels that can be enabled for SDK diagnostic messages. */
enum class LogLevel {
    ALL,
    INFO,
    DEBUG,
    WARN,
    VERBOSE,
    WTF,
    ERROR
}

/** Writes an info message when [LogLevel.INFO] is enabled. */
fun Any.info(message: String) {
    logWhenEnabled(LogLevel.INFO, message, Log::i)
}

/** Writes a debug message when [LogLevel.DEBUG] is enabled. */
fun Any.debug(message: String) {
    logWhenEnabled(LogLevel.DEBUG, message, Log::d)
}

/** Writes an error message when [LogLevel.ERROR] is enabled. */
fun Any.error(message: String) {
    logWhenEnabled(LogLevel.ERROR, message, Log::e)
}

/** Writes a verbose message when [LogLevel.VERBOSE] is enabled. */
fun Any.verbose(message: String) {
    logWhenEnabled(LogLevel.VERBOSE, message, Log::v)
}

/** Writes a warning message when [LogLevel.WARN] is enabled. */
fun Any.warn(message: String) {
    logWhenEnabled(LogLevel.WARN, message, Log::w)
}

/** Writes an assertion-failure message when [LogLevel.WTF] is enabled. */
fun Any.wtf(message: String) {
    logWhenEnabled(LogLevel.WTF, message, Log::wtf)
}

private inline fun Any.logWhenEnabled(
    level: LogLevel,
    message: String,
    writeLog: (String, String) -> Int
) {
    if (!LogUtils.containsLevel(level)) return

    writeLog(logTag(), message)
}

/** Avoids Android's 23-character log-tag limit on Android 7.0 and older. */
private fun Any.logTag(): String {
    val tag = javaClass.simpleName.ifBlank { DEFAULT_LOG_TAG }
    return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N && tag.length > MAX_LEGACY_TAG_LENGTH) {
        tag.take(MAX_LEGACY_TAG_LENGTH)
    } else {
        tag
    }
}

private const val DEFAULT_LOG_TAG = "IPification"
private const val MAX_LEGACY_TAG_LENGTH = 23
