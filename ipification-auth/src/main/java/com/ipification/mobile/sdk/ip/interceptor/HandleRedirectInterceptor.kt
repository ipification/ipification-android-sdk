package com.ipification.mobile.sdk.ip.interceptor

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import com.ipification.mobile.sdk.BuildConfig
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.utils.DeviceUtils
import com.ipification.mobile.sdk.ip.utils.IPLogs
import com.ipification.mobile.sdk.ip.utils.LogUtils
import com.ipification.mobile.sdk.ip.utils.NetworkUtils
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

/**
 * Adds SDK headers to the initial request and converts matching redirects into successful responses.
 */
class HandleRedirectInterceptor(
    context: Context,
    private val initialRequestUrl: String,
    private val redirectUri: String,
    private val includeCarrierHeaders: Boolean
) : Interceptor {

    private val context = context.applicationContext
    private val deviceUtils = DeviceUtils.getInstance(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.nanoTime()

        log("-------")
        log("url: ${request.url}")

        val response = if (request.url.toString().startsWith(initialRequestUrl)) {
            proceedWithSdkHeaders(chain, request)
        } else {
            proceedWithRedirectHeaders(chain, request)
        }

        logResponse(response, startTime)
        IPConfiguration.getInstance().currentUrl = response.header("location").orEmpty()

        return if (isMatchingRedirect(response)) {
            buildSuccessfulRedirectResponse(request, response)
        } else {
            log("return response")
            response
        }
    }

    /** Adds SDK and optional carrier information to the initial request. */
    private fun proceedWithSdkHeaders(chain: Interceptor.Chain, request: Request): Response {
        val requestBuilder = request.newBuilder()
            .addHeader(
                IPHeaders.SDK_VERSION,
                IPConfiguration.getInstance().SDK_TYPE_VALUE + BuildConfig.VERSION_NAME
            )
            .addHeader(IPHeaders.DEVICE_TYPE, "android")
            .addHeader(IPHeaders.DEVICE_NAME, "${Build.MANUFACTURER} - ${Build.MODEL}")
            .addHeader(IPHeaders.OS_VERSION, Build.VERSION.RELEASE)
            .addHeader(IPHeaders.OS_API_LEVEL, Build.VERSION.SDK_INT.toString())

        if (includeCarrierHeaders) {
            addCarrierHeaders(requestBuilder)
        }

        return proceed(chain, requestBuilder.build())
    }

    /** Adds carrier and active data-session information when requested. */
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
            .addHeader(IPHeaders.CELLULAR_PRIVATE_IP, IPConfiguration.getInstance().CELLULAR_PRIVATE_IP)

        if (!isDualSim) {
            requestBuilder.addHeader(IPHeaders.ACTIVE_DATA_SIM, "1")
            return
        }

        val sim2 = deviceUtils.getInfoSIM2()
        if (
            sim2.getMNC() != sim1.getMNC() ||
            sim2.getSimState() == TelephonyManager.SIM_STATE_READY
        ) {
            requestBuilder
                .addHeader(IPHeaders.SIM_2_MCC, sim2.getMCC())
                .addHeader(IPHeaders.SIM_2_MNC, sim2.getMNC())
        }

        requestBuilder
            .addHeader(IPHeaders.SIM_2_STATE, sim2.getSimState().toString())
            .addHeader(IPHeaders.SIM_2_SIGNAL_STRENGTH, sim2.getSignalStrength().toString())
            .addHeader(IPHeaders.SIM_2_ERROR_MESSAGE, sim2.getErrorMessage())

        val activeSim = deviceUtils.cachedActiveSimOperator
        val activeOperator = activeSim ?: deviceUtils.activeSimOperator()
        val activeSimSlot = when (activeSim?.getSubscriptionId()) {
            sim2.getSubscriptionId() -> 2
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

    /** Applies configured browser-style headers to requests made after the initial request. */
    private fun proceedWithRedirectHeaders(chain: Interceptor.Chain, request: Request): Response {
        val configuration = IPConfiguration.getInstance()
        val requestBuilder = request.newBuilder()
            .removeHeader("User-Agent")
            .removeHeader("Accept")

        configuration.OKHTTP_USER_AGENT
            .takeIf(String::isNotEmpty)
            ?.let { requestBuilder.addHeader("User-Agent", it) }

        configuration.OKHTTP_ACCEPT
            .takeIf(String::isNotEmpty)
            ?.let { requestBuilder.addHeader("Accept", it) }

        return proceed(chain, requestBuilder.build())
    }

    /** Sends a request and logs its headers when debug logging is enabled. */
    private fun proceed(chain: Interceptor.Chain, request: Request): Response {
        if (IPConfiguration.getInstance().debug) {
            log(
                String.format(
                    "--> Sending request %s on %s%n%s",
                    request.url,
                    chain.connection(),
                    request.headers
                )
            )
        }
        return chain.proceed(request)
    }

    /** Checks whether a redirect marks the end of the cellular authorization flow. */
    private fun isMatchingRedirect(response: Response): Boolean {
        if (response.code !in 300..399) return false

        val location = response.header("location")
        return location?.startsWith(redirectUri) == true ||
            response.header("Location")?.startsWith(redirectUri) == true ||
            response.header("imbox_session_id") != null
    }

    /** Returns a successful response containing the matched redirect URL. */
    private fun buildSuccessfulRedirectResponse(request: Request, response: Response): Response {
        log("matched - process")

        val contentType: MediaType? =
            response.body.contentType() ?: "text/plain".toMediaTypeOrNull()
        val redirectLocation = response.header("location")
            ?: response.header("Location")
            ?: ""
        val responseBuilder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("success")
            .body(redirectLocation.toResponseBody(contentType))

        for (header in response.headers) {
            if (header.first.isNotEmpty() && header.second.isNotEmpty()) {
                responseBuilder.addHeader(header.first, header.second)
            }
        }

        response.close()
        return responseBuilder.build()
    }

    /** Logs response timing, headers, cookies, and status in debug mode. */
    private fun logResponse(response: Response, startTime: Long) {
        if (!IPConfiguration.getInstance().debug) return

        log(
            String.format(
                "<-- Received response for %s in %.1fms%n%s",
                response.request.url,
                (System.nanoTime() - startTime) / 1e6,
                response.headers
            )
        )
        for (cookie in response.headers("set-cookie")) {
            log("Set-Cookie: $cookie")
        }
        log("response - status code: ${response.code}")
        log("-------")
    }

    /** Appends an interceptor message to the SDK debug log. */
    private fun log(message: String) {
        if (IPConfiguration.getInstance().debug) {
            IPLogs.getInstance().LOG +=
                "${LogUtils.currentTimestamp()} - HandleRedirectInterceptor - $message\n"
        }
    }
}
