package com.ipification.mobile.sdk.ip.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.ipification.mobile.sdk.BuildConfig
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.SingletonHolder

/** Reads SIM, carrier, and device information used by SDK requests and diagnostics. */
class DeviceUtils private constructor(context: Context) {

    companion object : SingletonHolder<DeviceUtils, Context>(::DeviceUtils) {
        private const val INVALID_SUBSCRIPTION_ID = -1
    }

    private val context = context.applicationContext
    private val telephonyManager =
        this.context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    /** Most recently resolved active data SIM, retained as a fallback when Android returns no data. */
    internal var cachedActiveSimOperator: SIMOperator? = null
        private set

    /** Clears the cached active data SIM. */
    fun reset() {
        cachedActiveSimOperator = null
    }

    /** Returns the active data SIM, falling back to the most recently resolved operator. */
    fun activeSimOperator(): SIMOperator {
        val resolvedOperator = getActiveDataSimOperator()
        if (resolvedOperator.getOperatorCode().isBlank()) {
            return cachedActiveSimOperator ?: resolvedOperator
        }

        cachedActiveSimOperator = resolvedOperator
        return resolvedOperator
    }

    /** Returns whether Android reports more than one modem or phone slot. */
    @Suppress("DEPRECATION")
    fun isDualSim(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> telephonyManager.activeModemCount > 1
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> telephonyManager.phoneCount > 1
            else -> false
        }
    }

    private fun getActiveDataSimOperator(): SIMOperator {
        try {
            return if (!isDualSim()) {
                createOperator(telephonyManager, defaultDataSubscriptionId())
            } else {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        var dataSubId = SubscriptionManager.getActiveDataSubscriptionId()
                        var dataSimManager = telephonyManagerForSubscription(dataSubId)

                        if (dataSimManager.simOperator.isNullOrEmpty()) {
                            dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
                            dataSimManager = telephonyManagerForSubscription(dataSubId)
                        }

                        createOperator(dataSimManager, dataSubId)
                    }

                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                        val dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()

                        if (dataSubId != INVALID_SUBSCRIPTION_ID) {
                            val dataSimManager = telephonyManagerForSubscription(dataSubId)
                            createOperator(dataSimManager, dataSubId)
                        } else {
                            SIMOperator("", "", "", errorMessage = "No valid data subscription found")
                        }
                    }

                    else -> {
                        SIMOperator("", "", "", errorMessage = "Unsupported OS version")
                    }
                }
            }
        } catch (e: Exception) {
            return SIMOperator("", "", "", errorMessage = "Error exception: ${e.message}")
        }
    }

    private fun defaultDataSubscriptionId(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return SubscriptionManager.getDefaultDataSubscriptionId()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return INVALID_SUBSCRIPTION_ID
        }

        return runCatching {
            SubscriptionManager::class.java
                .getDeclaredMethod("getDefaultDataSubId")
                .apply { isAccessible = true }
                .invoke(null) as Int
        }.getOrDefault(INVALID_SUBSCRIPTION_ID)
    }

    private fun createOperator(manager: TelephonyManager, subscriptionId: Int): SIMOperator {
        val signalStrength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            manager.signalStrength?.cellSignalStrengths?.firstOrNull()?.asuLevel ?: -1
        } else {
            -1
        }
        return SIMOperator(
            manager.simOperator.orEmpty(),
            manager.simCountryIso.orEmpty(),
            manager.simOperatorName.orEmpty(),
            subscriptionId,
            manager.simState,
            signalStrength
        )
    }

    /** Returns carrier information for the first SIM slot. */
    fun getInfoSIM1(): SIMOperator {
        try {
            if (!isDualSim()) {
                val subscriptionId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    SubscriptionManager.getDefaultSubscriptionId()
                } else 1
                return createOperator(telephonyManager, subscriptionId)
            } else {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    operatorForSlot(0, "SIM1")
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
                    if (dataSubId != INVALID_SUBSCRIPTION_ID) {
                        createOperator(telephonyManagerForSubscription(dataSubId), dataSubId)
                    } else {
                        SIMOperator("", "", "", errorMessage = "Invalid subscription ID for active SIM")
                    }
                } else {
                    SIMOperator("", "", "", errorMessage = "Unsupported OS version")
                }
            }
        } catch (e: Exception) {
            return SIMOperator("", "", "", errorMessage = "Error exception: ${e.message}")
        }
    }


    /** Returns carrier information for the second SIM slot when Android exposes it. */
    fun getInfoSIM2(): SIMOperator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            operatorForSlot(1, "SIM2")
        } else {
            SIMOperator("", "", "", errorMessage = "Second SIM details require Android 10 or newer")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun operatorForSlot(slotIndex: Int, label: String): SIMOperator {
        return runCatching {
            val subscriptionId = subscriptionIdForSlot(slotIndex)
            if (subscriptionId == INVALID_SUBSCRIPTION_ID) {
                return SIMOperator("", "", "", errorMessage = "No valid subscription for $label")
            }

            val manager = telephonyManagerForSubscription(subscriptionId)
            createOperator(manager, subscriptionId)
        }.getOrElse { exception ->
            SIMOperator("", "", "", errorMessage = "Failed to read $label: ${exception.message}")
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun subscriptionIdForSlot(slotIndex: Int): Int {
        val subscriptionManager =
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        return subscriptionManager.activeSubscriptionInfoList
            ?.firstOrNull { it.simSlotIndex == slotIndex }
            ?.subscriptionId
            ?: INVALID_SUBSCRIPTION_ID
    }

    /** Creates a telephony manager scoped to a subscription on Android 7.0 or newer. */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun telephonyManagerForSubscription(subscriptionId: Int): TelephonyManager {
        return telephonyManager.createForSubscriptionId(subscriptionId)
    }

    /** Generates device and carrier diagnostics, with the supplied phone number redacted. */
    fun generateHeaderLogs(inputPhone: String): String {
        val activeSimOperator = activeSimOperator()
        val infoSIM1 = getInfoSIM1()
        val isDualSim = isDualSim()
        val log = StringBuilder()
            .appendLine("#####################################")
            .appendLine("#####################################")

        if (isDualSim) {
            val infoSIM2 = getInfoSIM2()
            log.appendLine("DUAL SIM: yes")
                .appendLine("ACTIVE MCC: ${activeSimOperator.getMCC()}")
                .appendLine("ACTIVE MNC: ${activeSimOperator.getMNC()}")
                .appendLine("ACTIVE COUNTRY CODE: ${activeSimOperator.getCountryName()}")
                .appendLine("ACTIVE OPERATOR NAME: ${activeSimOperator.getOperatorName()}")
                .appendLine()
                .appendLine("MCC SIM 1: ${infoSIM1.getMCC()}")
                .appendLine("MNC SIM 1: ${infoSIM1.getMNC()}")
                .appendLine("COUNTRY CODE SIM 1: ${infoSIM1.getCountryName()}")
                .appendLine("OPERATOR NAME SIM 1: ${infoSIM1.getOperatorName()}")
                .appendLine()
                .appendLine("MCC SIM 2: ${infoSIM2.getMCC()}")
                .appendLine("MNC SIM 2: ${infoSIM2.getMNC()}")
                .appendLine("COUNTRY CODE SIM 2: ${infoSIM2.getCountryName()}")
                .appendLine("OPERATOR NAME SIM 2: ${infoSIM2.getOperatorName()}")
        } else {
            log.appendLine("DUAL SIM: no")
                .appendLine("MCC: ${activeSimOperator.getMCC()}")
                .appendLine("MNC: ${activeSimOperator.getMNC()}")
                .appendLine("ACTIVE COUNTRY NAME: ${activeSimOperator.getCountryName()}")
                .appendLine("ACTIVE OPERATOR NAME: ${activeSimOperator.getOperatorName()}")
        }

        val configuration = IPConfiguration.getInstance()
        log.appendLine("-------------------------------------")
            .appendLine("WIFI: ${enabledState(NetworkUtils.isWifiEnabled(context))}")
            .appendLine("VPN: ${enabledState(NetworkUtils.isVpnEnabled(context))}")
            .appendLine("ROAMING: ${enabledState(NetworkUtils.isRoaming(context))}")
            .appendLine("CELLULAR_IP: ${configuration.CELLULAR_PRIVATE_IP}")
            .appendLine("-------------------------------------")
            .appendLine("DEVICE NAME: ${Build.MANUFACTURER} - ${Build.MODEL}")
            .appendLine("OS VERSION: ${Build.VERSION.RELEASE} - ${Build.VERSION.SDK_INT}")
            .appendLine("SDK VERSION: ${BuildConfig.VERSION_NAME}")
            .appendLine("COOKIE HANDLING: ${enabledState(configuration.enabledHandleCookie)}")
            .appendLine("-------------------------------------")
            .appendLine("INPUT PHONE NUMBER: ${redactPhoneNumber(inputPhone)}")
            .appendLine("-------------------------------------")
            .appendLine("#####################################")
            .appendLine("#####################################")
            .appendLine()
        return log.toString()
    }

    private fun enabledState(enabled: Boolean): String = if (enabled) "enabled" else "disabled"

    private fun redactPhoneNumber(phoneNumber: String): String {
        return phoneNumber.takeLast(4).padStart(phoneNumber.length, '*')
    }
}
