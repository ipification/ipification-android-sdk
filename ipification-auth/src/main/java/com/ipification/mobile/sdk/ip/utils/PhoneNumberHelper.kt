package com.ipification.mobile.sdk.ip.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.AuthChannel
import java.util.Locale

/** Reads phone numbers exposed by Android for the active SIM subscriptions. */
object PhoneNumberHelper {

    /** Permissions required to inspect subscriptions and read numbers on the current device. */
    val PHONE_PERMISSIONS: Array<String>
        get() = getRuntimeRequiredPermissions()

    /** Returns the phone permissions required by the current Android version. */
    fun getRequiredPermissions(): Array<String> = getRuntimeRequiredPermissions()

    /** Returns only the phone permissions required by the current Android version. */
    fun getRuntimeRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            phoneNumberPermissions()
        } else {
            arrayOf(
                Manifest.permission.READ_PHONE_STATE
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun phoneNumberPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS
        )
    }

    /** Returns whether all phone permissions required by the current device are granted. */
    fun hasAllPhonePerms(context: Context): Boolean {
        return getRuntimeRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Immediately reads phone numbers available for [authChannel].
     *
     * IP authentication returns the active data SIM number when available. TS.43 returns every
     * available active subscription number. Each result contains the number and uppercase country
     * ISO code. An empty list is returned when permissions or phone numbers are unavailable.
     */
    @SuppressLint("MissingPermission")
    fun fetchPhoneNumberNow(
        context: Context,
        authChannel: AuthChannel? = AuthChannel.IP,
        onNumber: (List<Pair<String, String>>) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            log("Phone number retrieval requires Android 13 or newer")
            onNumber(emptyList())
            return
        }

        if (!hasAllPhonePerms(context)) {
            log("Required phone permissions are missing")
            onNumber(emptyList())
            return
        }

        val appContext = context.applicationContext
        val systemCountryIso = Locale.getDefault().country.uppercase(Locale.US)
        val activeSubscriptionId =
            DeviceUtils.getInstance(appContext).activeSimOperator().getSubscriptionId()

        val subscriptionResults = readSubscriptionNumbers(
            context = appContext,
            activeSubscriptionId = activeSubscriptionId,
            includeAllSubscriptions = authChannel == AuthChannel.TS43,
            fallbackCountryIso = systemCountryIso
        )

        log(
            if (subscriptionResults.isEmpty()) {
                "Phone number is unavailable"
            } else {
                "Resolved ${subscriptionResults.size} phone number(s) from subscriptions"
            }
        )
        onNumber(subscriptionResults)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("MissingPermission")
    private fun readSubscriptionNumbers(
        context: Context,
        activeSubscriptionId: Int,
        includeAllSubscriptions: Boolean,
        fallbackCountryIso: String
    ): List<Pair<String, String>> {
        val subscriptionManager =
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val subscriptions = runCatching { subscriptionManager.activeSubscriptionInfoList.orEmpty() }
            .onFailure { log("Unable to read active subscriptions: ${it.message}") }
            .getOrDefault(emptyList())

        val selectedSubscriptions = if (includeAllSubscriptions) {
            subscriptions
        } else {
            listOfNotNull(
                subscriptions.firstOrNull { it.subscriptionId == activeSubscriptionId }
                    ?: subscriptions.firstOrNull()
            )
        }

        val results = selectedSubscriptions.mapNotNull { subscription ->
            val phoneNumber = readSubscriptionNumber(
                subscriptionManager = subscriptionManager,
                subscriptionId = subscription.subscriptionId
            ) ?: return@mapNotNull null

            phoneNumber to subscription.countryIsoOr(fallbackCountryIso)
        }.distinctBy { it.first }

        if (results.isNotEmpty()) return results

        return runCatching {
            subscriptionManager.getPhoneNumber(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID)
        }.onFailure {
            log("Unable to read the default subscription phone number: ${it.message}")
        }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.let { listOf(it to fallbackCountryIso) }
            .orEmpty()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("MissingPermission")
    private fun readSubscriptionNumber(
        subscriptionManager: SubscriptionManager,
        subscriptionId: Int
    ): String? {
        return runCatching { subscriptionManager.getPhoneNumber(subscriptionId) }.onFailure {
            log("Unable to read a subscription phone number: ${it.message}")
        }.getOrNull()?.takeIf(String::isNotBlank)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun SubscriptionInfo.countryIsoOr(fallback: String): String {
        return countryIso
            ?.takeIf(String::isNotBlank)
            ?.uppercase(Locale.US)
            ?: fallback
    }

    private fun log(message: String) {
        if (!IPConfiguration.getInstance().debug) return

        Log.d(LOG_TAG, message)
        IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - $LOG_TAG - $message\n"
    }

    private const val LOG_TAG = "PhoneNumberHelper"
}
