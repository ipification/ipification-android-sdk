package com.ipification.mobile.sdk.ip.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Host application package information sent in SDK request headers.
 *
 * Mirrors the iOS SDK headers: `app-package` (bundle identifier), `app-version`
 * (marketing version) and `app-build` (build number).
 *
 * @property packageName Application ID of the host app.
 * @property versionName `versionName` of the host app, empty when not declared.
 * @property versionCode `versionCode` of the host app as a string, empty when it cannot be read.
 */
internal data class AppInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: String
) {
    companion object {
        @Volatile
        private var cached: AppInfo? = null

        /** Returns the host app information, reading it from [PackageManager] once and caching it. */
        fun get(context: Context): AppInfo {
            cached?.let { return it }
            return read(context.applicationContext).also { cached = it }
        }

        private fun read(context: Context): AppInfo {
            val packageName = context.packageName.orEmpty()
            return try {
                val packageInfo = context.packageManager.getPackageInfoCompat(packageName)
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
                AppInfo(
                    packageName = packageName,
                    versionName = packageInfo.versionName.orEmpty(),
                    versionCode = versionCode.toString()
                )
            } catch (e: Exception) {
                IPLogs.getInstance().LOG += "AppInfo - unable to read package info: ${e.message}\n"
                AppInfo(packageName = packageName, versionName = "", versionCode = "")
            }
        }

        private fun PackageManager.getPackageInfoCompat(packageName: String) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                getPackageInfo(packageName, 0)
            }
    }
}
