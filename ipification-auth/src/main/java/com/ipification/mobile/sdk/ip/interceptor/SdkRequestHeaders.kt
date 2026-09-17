package com.ipification.mobile.sdk.ip.interceptor

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import com.ipification.mobile.sdk.BuildConfig
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.utils.AppInfo
import com.ipification.mobile.sdk.ip.utils.DeviceUtils
import com.ipification.mobile.sdk.ip.utils.NetworkUtils
import okhttp3.Request

/**
 * Shared builders for the SDK, host app, device and carrier headers sent to IPification
 * backends. Used by [SdkLogInterceptor] (error reports) and [DeviceHeadersInterceptor]
 * (TS.43 and SMS backend calls).
 */
internal object SdkRequestHeaders {

    /** Adds client, SDK, device and host app identification headers. */
    fun addSdkHeaders(requestBuilder: Request.Builder, context: Context): Request.Builder {
        val appInfo = AppInfo.get(context)
        return requestBuilder
            .addHeader(IPHeaders.CLIENT_ID, IPConfiguration.getInstance().CLIENT_ID)
            .addHeader(IPHeaders.SDK_VERSION, BuildConfig.VERSION_NAME)
            .addHeader(IPHeaders.DEVICE_TYPE, "android")
            .addHeader(IPHeaders.DEVICE_NAME, "${Build.MANUFACTURER} - ${Build.MODEL}")
            .addHeader(IPHeaders.OS_VERSION, Build.VERSION.RELEASE)
            .addHeader(IPHeaders.OS_API_LEVEL, Build.VERSION.SDK_INT.toString())
            .addHeader(IPHeaders.APP_PACKAGE, appInfo.packageName)
            .addHeader(IPHeaders.APP_VERSION, appInfo.versionName)
            .addHeader(IPHeaders.APP_BUILD, appInfo.versionCode)
            .addHeader(IPHeaders.ERROR_REPORT, errorReportState())
    }

    /** `on` when the SDK submits error reports to the IP server, `off` when the host app disabled it. */
    fun errorReportState(): String =
        if (IPConfiguration.getInstance().sendErrorReportsEnabled) "on" else "off"

    /** Adds SIM, network state and active data-session information. */
    fun addCarrierHeaders(requestBuilder: Request.Builder, context: Context): Request.Builder {
        val appContext = context.applicationContext
        val deviceUtils = DeviceUtils.getInstance(appContext)
        val sim1 = deviceUtils.getInfoSIM1()
        val isDualSim = deviceUtils.isDualSim()

        requestBuilder
            .addHeader(IPHeaders.SIM_1_MCC, sim1.getMCC())
            .addHeader(IPHeaders.SIM_1_MNC, sim1.getMNC())
            .addHeader(IPHeaders.SIM_1_STATE, sim1.getSimState().toString())
            .addHeader(IPHeaders.SIM_1_SIGNAL_STRENGTH, sim1.getSignalStrength().toString())
            .addHeader(IPHeaders.SIM_1_ERROR_MESSAGE, sim1.getErrorMessage())
            .addHeader(IPHeaders.DUAL_SIM, if (isDualSim) "yes" else "no")
            .addHeader(IPHeaders.WIFI_ENABLED, if (NetworkUtils.isWifiEnabled(appContext)) "yes" else "no")
            .addHeader(IPHeaders.VPN_ENABLED, if (NetworkUtils.isVpnEnabled(appContext)) "yes" else "no")
            .addHeader(IPHeaders.ROAMING, if (NetworkUtils.isRoaming(appContext)) "yes" else "no")

        if (!isDualSim) {
            return requestBuilder.addHeader(IPHeaders.ACTIVE_DATA_SIM, "1")
        }

        addSecondSimHeaders(requestBuilder, deviceUtils, sim1.getMNC())
        addActiveDataSessionHeader(requestBuilder, appContext, deviceUtils)
        return requestBuilder
    }

    /** Adds information reported by the second SIM slot. */
    private fun addSecondSimHeaders(
        requestBuilder: Request.Builder,
        deviceUtils: DeviceUtils,
        firstSimMnc: String
    ) {
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
    private fun addActiveDataSessionHeader(
        requestBuilder: Request.Builder,
        context: Context,
        deviceUtils: DeviceUtils
    ) {
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
