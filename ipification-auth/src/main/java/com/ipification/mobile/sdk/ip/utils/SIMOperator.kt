package com.ipification.mobile.sdk.ip.utils

import java.util.Locale

/**
 * Describes the carrier and subscription information reported for one SIM.
 *
 * Empty strings and `-1` values indicate that Android did not expose the corresponding value.
 *
 * @param simOperator Combined mobile country code (MCC) and mobile network code (MNC).
 * @param simCountryIso Two-letter ISO country code reported for the SIM.
 * @param simOperatorName Carrier display name reported by Android.
 * @param dataSubscriptionId Android subscription ID associated with the SIM.
 * @param simState Current Android SIM state.
 * @param signalStrength Signal strength ASU level.
 * @param errorMessage Diagnostic message generated when SIM information could not be read.
 */
class SIMOperator(
    private val simOperator: String,
    private val simCountryIso: String,
    private val simOperatorName: String,
    private val dataSubscriptionId: Int = INVALID_VALUE,
    private val simState: Int = INVALID_VALUE,
    private val signalStrength: Int? = INVALID_VALUE,
    private val errorMessage: String = ""
) {

    /** Returns the mobile network code (MNC), or an empty string when unavailable. */
    fun getMNC(): String = if (hasValidOperatorCode()) simOperator.drop(MCC_LENGTH) else ""

    /** Returns the mobile country code (MCC), or an empty string when unavailable. */
    fun getMCC(): String = if (hasValidOperatorCode()) simOperator.take(MCC_LENGTH) else ""

    /** Returns the complete MCC and MNC operator code. */
    fun getOperatorCode(): String = simOperator

    /** Returns the carrier display name reported by Android. */
    fun getOperatorName(): String = simOperatorName

    /** Returns the English country name for the SIM country code. */
    fun getCountryName(): String {
        if (simCountryIso.isBlank()) return ""

        return runCatching {
            Locale.Builder()
                .setRegion(simCountryIso.uppercase(Locale.US))
                .build()
                .getDisplayCountry(Locale.ENGLISH)
        }.getOrDefault("")
    }

    /** Returns the Android subscription ID, or `-1` when unavailable. */
    fun getSubscriptionId(): Int = dataSubscriptionId

    /** Returns the SIM state reported by Android, or `-1` when unavailable. */
    fun getSimState(): Int = simState

    /** Returns the signal strength ASU level, or `-1` when unavailable. */
    fun getSignalStrength(): Int = signalStrength ?: INVALID_VALUE

    /** Returns a bounded diagnostic message generated while reading this SIM. */
    fun getErrorMessage(): String = errorMessage.take(MAX_ERROR_MESSAGE_LENGTH)

    @Deprecated(
        message = "Use getOperatorCode().",
        replaceWith = ReplaceWith("getOperatorCode()")
    )
    fun getSimOperatorStr(): String = getOperatorCode()

    @Deprecated(
        message = "Use getSubscriptionId().",
        replaceWith = ReplaceWith("getSubscriptionId()")
    )
    fun getSubID(): Int = getSubscriptionId()

    @Deprecated(
        message = "Use getSignalStrength().",
        replaceWith = ReplaceWith("getSignalStrength()")
    )
    fun getSimSignalStrength(): Int = getSignalStrength()

    private fun hasValidOperatorCode(): Boolean = simOperator.length >= MCC_LENGTH

    private companion object {
        const val MCC_LENGTH = 3
        const val INVALID_VALUE = -1
        const val MAX_ERROR_MESSAGE_LENGTH = 30
    }
}
