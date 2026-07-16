package com.ipification.mobile.sdk.ip.interceptor

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import com.ipification.mobile.sdk.BuildConfig
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.utils.DeviceUtils
import com.ipification.mobile.sdk.ip.utils.NetworkUtils
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Adds client, SDK, device, and optional carrier headers to error-report requests.
 *
 * The class name is retained for compatibility with existing integrations.
 */
class SdkLogInterceptor(context: Context) : Interceptor {

    private val context = context.applicationContext
    private val deviceUtils = DeviceUtils.getInstance(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()

        addRequiredHeaders(requestBuilder)
        if (IPConfiguration.getInstance().errorReportEnableCarrierHeaders) {
            addCarrierHeaders(requestBuilder)
        }

        return chain.proceed(requestBuilder.build())
    }

    /** Adds client, SDK, and device information required by error reporting. */
    private fun addRequiredHeaders(requestBuilder: Request.Builder) {
        requestBuilder
            .addHeader(IPHeaders.CLIENT_ID, IPConfiguration.getInstance().CLIENT_ID)
            .addHeader(IPHeaders.SDK_VERSION, BuildConfig.VERSION_NAME)
            .addHeader(IPHeaders.DEVICE_TYPE, "android")
            .addHeader(IPHeaders.DEVICE_NAME, "${Build.MANUFACTURER} - ${Build.MODEL}")
            .addHeader(IPHeaders.OS_VERSION, Build.VERSION.RELEASE)
            .addHeader(IPHeaders.OS_API_LEVEL, Build.VERSION.SDK_INT.toString())
    }

    /** Adds SIM and active data-session information when enabled. */
    private fun addCarrierHeaders(requestBuilder: Request.Builder) {
        val sim1 = deviceUtils.getInfoSIM1()
        val isDualSim = deviceUtils.isDualSim()

        requestBuilder
            .addHeader(IPHeaders.SIM_1_MCC, sim1.getMCC())
            .addHeader(IPHeaders.SIM_1_MNC, sim1.getMNC())
            .addHeader(IPHeaders.SIM_1_STATE, sim1.getSimState().toString())
            .addHeader(IPHeaders.SIM_1_SIGNAL_STRENGTH, sim1.getSignalStrength().toString())
            .addHeader(IPHeaders.SIM_1_ERROR_MESSAGE, sim1.getErrorMessage())
            .addHeader(IPHeaders.DUAL_SIM, if (isDualSim) "yes" else "no")
            .addHeader(IPHeaders.WIFI_ENABLED, if (NetworkUtils.isWifiEnabled(context)) "yes" else "no")
            .addHeader(IPHeaders.VPN_ENABLED, if (NetworkUtils.isVpnEnabled(context)) "yes" else "no")
            .addHeader(IPHeaders.ROAMING, if (NetworkUtils.isRoaming(context)) "yes" else "no")

        if (!isDualSim) {
            requestBuilder.addHeader(IPHeaders.ACTIVE_DATA_SIM, "1")
            return
        }

        addSecondSimHeaders(requestBuilder, sim1.getMNC())
        addActiveDataSessionHeader(requestBuilder)
    }

    /** Adds information reported by the second SIM slot. */
    private fun addSecondSimHeaders(requestBuilder: Request.Builder, firstSimMnc: String) {
        val sim2 = deviceUtils.getInfoSIM2()

        if (sim2.getMNC() != firstSimMnc || sim2.getSimState() == TelephonyManager.SIM_STATE_READY) {
            requestBuilder
                .addHeader(IPHeaders.SIM_2_MCC, sim2.getMCC())
                .addHeader(IPHeaders.SIM_2_MNC, sim2.getMNC())
        }

        requestBuilder
            .addHeader(IPHeaders.SIM_2_STATE, sim2.getSimState().toString())
            .addHeader(IPHeaders.SIM_2_SIGNAL_STRENGTH, sim2.getSignalStrength().toString())
            .addHeader(IPHeaders.SIM_2_ERROR_MESSAGE, sim2.getErrorMessage())
    }

    /** Adds the current or most recently active data-session SIM. */
    private fun addActiveDataSessionHeader(requestBuilder: Request.Builder) {
        val activeSim = deviceUtils.cachedActiveSimOperator
        val activeOperator = activeSim ?: deviceUtils.activeSimOperator()
        val activeSimSlot = when (activeSim?.getSubscriptionId()) {
            deviceUtils.getInfoSIM2().getSubscriptionId() -> 2
            else -> 1
        }

        if (NetworkUtils.isMobileDataEnabled(context)) {
            requestBuilder.addHeader(IPHeaders.ACTIVE_DATA_SIM, activeSimSlot.toString())
        } else {
            requestBuilder.addHeader(
                IPHeaders.LAST_ACTIVE_DATA_SIM,
                "${activeOperator.getMCC()}${activeOperator.getMNC()}"
            )
        }
    }
}
