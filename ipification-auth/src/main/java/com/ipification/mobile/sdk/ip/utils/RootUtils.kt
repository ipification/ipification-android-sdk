package com.ipification.mobile.sdk.ip.utils

import android.os.Build
import java.io.File

/**
 * Heuristic root detection used for SDK diagnostics and risk signals.
 *
 * The result is a best-effort indication, not a security guarantee: a determined user can hide root
 * from every check below. It is intended to help IPification and partners spot risky sessions, and
 * must never be used on its own to grant or deny authentication.
 *
 * Detection is intentionally cheap and side-effect free: it inspects build tags and well-known root
 * artifacts on disk, and never spawns a process. The result is computed once per process and cached,
 * so it can be read from request paths without measurable cost.
 */
internal object RootUtils {

    /** Build tag left by non-production (typically rooted or self-signed) system images. */
    private const val TEST_KEYS_TAG = "test-keys"

    /** Common locations of the `su` binary on rooted devices. */
    private val SU_BINARY_PATHS = arrayOf(
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/system/sbin/su",
        "/vendor/bin/su",
        "/su/bin/su",
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su"
    )

    /** Files installed by common root managers. */
    private val ROOT_MANAGER_PATHS = arrayOf(
        "/system/app/Superuser.apk",
        "/system/app/SuperSU.apk",
        "/system/app/Magisk.apk",
        "/sbin/magisk",
        "/sbin/.magisk",
        "/data/adb/magisk",
        "/data/adb/ksu",
        "/system/xbin/daemonsu",
        "/system/etc/init.d/99SuperSUDaemon",
        "/dev/com.koushikdutta.superuser.daemon"
    )

    @Volatile
    private var cachedResult: Boolean? = null

    /**
     * Returns whether the device shows signs of being rooted.
     *
     * The first call performs the checks and caches the outcome for the lifetime of the process.
     */
    @JvmStatic
    fun isDeviceRooted(): Boolean {
        cachedResult?.let { return it }
        synchronized(this) {
            cachedResult?.let { return it }
            val result = runCatching { detect() }
                .onFailure { LogUtils.debug("Root detection failed: ${it.message}") }
                .getOrDefault(false)
            cachedResult = result
            return result
        }
    }

    /** Returns `yes` or `no`, for request headers and diagnostic logs. */
    @JvmStatic
    fun rootedHeaderValue(): String = if (isDeviceRooted()) "yes" else "no"

    /** Clears the cached result. Intended for tests. */
    internal fun reset() {
        synchronized(this) { cachedResult = null }
    }

    private fun detect(): Boolean {
        return hasTestKeysBuild() || hasAnyFile(SU_BINARY_PATHS) || hasAnyFile(ROOT_MANAGER_PATHS)
    }

    /** Checks whether the system image was signed with test keys. */
    private fun hasTestKeysBuild(): Boolean {
        return Build.TAGS?.contains(TEST_KEYS_TAG) == true
    }

    /** Checks whether any of the given paths exists, ignoring paths the app may not stat. */
    private fun hasAnyFile(paths: Array<String>): Boolean {
        return paths.any { path ->
            runCatching { File(path).exists() }.getOrDefault(false)
        }
    }
}
