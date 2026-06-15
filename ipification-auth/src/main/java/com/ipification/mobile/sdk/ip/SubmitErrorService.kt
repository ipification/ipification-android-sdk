package com.ipification.mobile.sdk.ip

import android.content.Context
import android.util.Log
import com.ipification.mobile.sdk.ip.interceptor.LoggingInterceptor
import com.ipification.mobile.sdk.ip.interceptor.SdkLogInterceptor
import com.ipification.mobile.sdk.ts43.exception.TS43ErrorCode
import com.ipification.mobile.sdk.ip.utils.ErrorCode
import com.ipification.mobile.sdk.ip.utils.IPLogs
import com.ipification.mobile.sdk.ip.utils.LogUtils
import com.ipification.mobile.sdk.ip.utils.PrintingEventListener
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Submits diagnostic reports for failed SDK operations. */
class SubmitErrorService {

    /**
     * Builds and asynchronously submits an SDK error report.
     *
     * Reporting failures are logged locally and never propagated to the active SDK operation.
     */
    @JvmOverloads
    fun sendErrorReport(
        context: Context,
        apiType: String,
        errorDescription: String,
        errorCode: String?,
        phoneNumber: String? = null,
        requestUrl: String? = null
    ) {
        if (!IPConfiguration.getInstance().sendErrorReportsEnabled) {
            log("Error reporting is disabled")
            return
        }

        runCatching {
            val normalizedErrorCode = errorCode?.trim()?.trimEnd('|')
            val logData = buildLogData(errorDescription, normalizedErrorCode, requestUrl)
            submitReport(
                context = context.applicationContext,
                apiType = apiType,
                logData = logData,
                errorType = parseType(logData, normalizedErrorCode),
                phoneNumber = phoneNumber
            )
        }.onFailure { exception ->
            Log.e(LOG_TAG, "Failed to prepare error report", exception)
            log("Failed to prepare error report: ${exception.message}")
        }
    }

    /** Creates the semicolon-delimited diagnostic payload expected by the reporting endpoint. */
    private fun buildLogData(
        errorDescription: String,
        errorCode: String?,
        requestUrl: String?
    ): String {
        val fields = buildList {
            sanitizeValue(requestUrl)?.let { add("request_url=$it") }
            sanitizeValue(IPConfiguration.getInstance().currentState)?.let { add("state=$it") }
            sanitizeValue(errorDescription, MAX_ERROR_DESCRIPTION_LENGTH)?.let {
                add("error_description=$it")
            }
            add("sdk_error_code=${sanitizeValue(errorCode).orEmpty()}")
        }
        return fields.joinToString(separator = ";", postfix = ";")
    }

    /** Sends the prepared report without exposing its potentially sensitive values in local logs. */
    private fun submitReport(
        context: Context,
        apiType: String,
        logData: String,
        errorType: String,
        phoneNumber: String?
    ) {
        val configuration = IPConfiguration.getInstance()
        val reportUrl = configuration.getSDKLogUrl()
        val timeoutMillis = configuration.ERROR_REPORT_TIMEOUT

        val body = FormBody.Builder()
            .add("log_data", logData)
            .add("type", errorType)
            .add("api", apiType)
            .add("phone", phoneNumber.orEmpty())
            .build()

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(SdkLogInterceptor(context))
            .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)

        if (configuration.dnsDebug) {
            clientBuilder
                .eventListenerFactory(PrintingEventListener.FACTORY)
                .addNetworkInterceptor(LoggingInterceptor())
        }

        val request = Request.Builder()
            .url(reportUrl)
            .post(body)
            .build()

        log("Submitting $errorType report for $apiType")
        clientBuilder.build().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                log("Error report request failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    log("Error report completed with HTTP ${it.code}")
                }
            }
        })
    }

    /** Classifies a report so the backend can group related failures. */
    private fun parseType(logData: String, errorCode: String?): String {
        val normalizedLogData = logData.lowercase()
        if ("interaction_required" in normalizedLogData) return "INTERACTION_REQUIRED"
        if ("failed to connect" in normalizedLogData) return "TIMEOUT"
        if ("cleartext" in normalizedLogData) return "CLEARTEXT"

        val sdkErrorCode = errorCode
            ?.substringBefore('|')
            ?.toIntOrNull()
            ?: return "UNKNOWN"

        return when {
            sdkErrorCode == TS43ErrorCode.AUTH_REQUEST_FAILED -> "AUTH"
            sdkErrorCode == TS43ErrorCode.TOKEN_EXCHANGE_FAILED -> "TOKEN_EXCHANGE"
            sdkErrorCode == TS43ErrorCode.VP_TOKEN_EXTRACTION_FAILED -> "VP_TOKEN"
            sdkErrorCode in CREDENTIAL_MANAGER_ERROR_CODES -> "CREDENTIAL_MANAGER"
            sdkErrorCode in CONFIGURATION_ERROR_CODES -> "CONFIG"
            sdkErrorCode in TS43_ERROR_RANGE -> "TS43"
            sdkErrorCode == ErrorCode.NETWORK_IS_NOT_ACTIVE ||
                sdkErrorCode == LEGACY_NETWORK_ERROR_CODE -> "CELLULAR_NOT_ACTIVE"
            sdkErrorCode == ErrorCode.NETWORK_IS_UNAVAILABLE -> "CELLULAR_NETWORK_UNAVAILABLE"
            else -> "UNKNOWN"
        }
    }

    /** Removes separators and line breaks that could corrupt the report payload. */
    private fun sanitizeValue(value: String?, maxLength: Int? = null): String? {
        val sanitized = value
            ?.replace(REPORT_SEPARATOR_REGEX, " ")
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null

        return maxLength?.let(sanitized::takeLast) ?: sanitized
    }

    private fun log(message: String) {
        Log.d(LOG_TAG, message)
        if (IPConfiguration.getInstance().debug) {
            IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - $LOG_TAG - $message\n"
        }
    }

    private companion object {
        const val LOG_TAG = "SubmitErrorService"
        const val MAX_ERROR_DESCRIPTION_LENGTH = 2_056
        const val LEGACY_NETWORK_ERROR_CODE = 1_001

        val REPORT_SEPARATOR_REGEX = Regex("[;\\r\\n]")
        val WHITESPACE_REGEX = Regex("\\s+")
        val TS43_ERROR_RANGE = 4_300..4_399

        val CREDENTIAL_MANAGER_ERROR_CODES = setOf(
            TS43ErrorCode.CREDENTIAL_MANAGER_ERROR,
            TS43ErrorCode.CREDENTIAL_CANCELLED,
            TS43ErrorCode.CREDENTIAL_INTERRUPTED,
            TS43ErrorCode.NO_CREDENTIAL_AVAILABLE
        )

        val CONFIGURATION_ERROR_CODES = setOf(
            TS43ErrorCode.MISSING_CLIENT_ID,
            TS43ErrorCode.MISSING_PHONE_NUMBER,
            TS43ErrorCode.MISSING_TS43_ENDPOINT,
            TS43ErrorCode.MISSING_CREDENTIAL_MANAGER
        )
    }
}
