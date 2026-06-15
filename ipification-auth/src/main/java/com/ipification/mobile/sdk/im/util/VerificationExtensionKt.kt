package com.ipification.mobile.sdk.im.util

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.fragment.app.Fragment
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.im.data.IMInfo
import com.ipification.mobile.sdk.im.ui.fragment.IMListFragment
import com.ipification.mobile.sdk.ip.utils.IPLogs

/** Helper methods used to select and validate IM provider apps. */
internal class VerificationExtensionKt {

    companion object Factory {

        /** Builds the provider-selection fragment for the supplied provider list. */
        @JvmStatic
        fun chooseStartScreen(validApp: List<IMInfo>): Fragment {
            return IMListFragment.newInstance(validApp)
        }

        /** Marks providers as installed when either primary or secondary package is available. */
        @JvmStatic
        fun checkInstalledApp(
            supportedProviders: List<IMInfo>,
            packageManager: PackageManager
        ): List<IMInfo> {
            return supportedProviders.map { provider ->
                provider.applyInstalledPackage(packageManager)
            }
        }

        /** Returns true when at least one supported provider app is installed. */
        @JvmStatic
        fun validateInstallApp(
            supportedProviders: List<IMInfo>,
            packageManager: PackageManager
        ): Boolean {
            return supportedProviders.any { provider ->
                provider.findInstalledPackage(packageManager) != null
            }
        }

        /** Returns the first installed provider based on configured priority order. */
        @JvmStatic
        fun findFirstInstalledApp(
            supportedProviders: List<IMInfo>,
            packageManager: PackageManager
        ): IMInfo? {
            val installedProviders = checkInstalledApp(supportedProviders, packageManager)
                .filter(IMInfo::isInstalled)
            if (installedProviders.isEmpty()) return null

            val priorityList = IPConfiguration.getInstance().IM_PRIORITY_APP_LIST
            return priorityList.firstNotNullOfOrNull { priority ->
                installedProviders.firstOrNull { provider -> provider.brand == priority }
            } ?: installedProviders.first()
        }

        private fun IMInfo.applyInstalledPackage(packageManager: PackageManager): IMInfo {
            val installedPackage = findInstalledPackage(packageManager)
            if (installedPackage != null) {
                packageName = installedPackage
                isInstalled = true
            } else {
                isInstalled = false
            }
            return this
        }

        private fun IMInfo.findInstalledPackage(packageManager: PackageManager): String? {
            return listOfNotNull(packageName, packageName2)
                .firstOrNull(packageManager::isPackageInstalled)
        }
    }
}

/** Returns true when [packageName] is visible and installed on the device. */
internal fun PackageManager.isPackageInstalled(packageName: String): Boolean {
    return try {
        getPackageInfoCompat(packageName, PackageManager.GET_ACTIVITIES)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        IPLogs.getInstance().LOG += "$packageName ${e.message}\n"
        false
    }
}

/** Version-safe PackageInfo lookup. */
@Suppress("WrongConstant")
internal fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, flags)
    }
}
