package com.ipification.mobile.sdk.ip

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.annotation.Keep
import com.ipification.mobile.sdk.ip.request.AuthScope
import com.ipification.mobile.sdk.ip.utils.DeviceUtils
import java.security.SecureRandom

/**
 * Global SDK configuration.
 *
 * Configure this singleton during application startup before invoking SDK services.
 * Values such as [CLIENT_ID], [REDIRECT_URI], [ENV], and [AUTH_CHANNELS] define
 * which backend and authentication channels are used for partner authentication.
 */
class IPConfiguration private constructor() {
    // Common Configuration

    /** Enables standard SDK debug logging. */
    var debug = false

    /** Enables DNS-specific debug logging. */
    var dnsDebug = false

    /** Enables additional verbose diagnostic logging. */
    var extraDebug = false

    /** Selects the backend environment used by the SDK. */
    var ENV: IPEnvironment = IPEnvironment.PRODUCTION

    /** Client identifier provided during IPification onboarding. */
    var CLIENT_ID: String = ""

    /** Registered URI that receives authentication results. */
    var REDIRECT_URI: Uri? = null

    /** Ordered authentication channels attempted by multi-channel flows. */
    var AUTH_CHANNELS: List<AuthChannel> = listOf(AuthChannel.IP)

    /** Length of the randomly generated OAuth state value. */
    var STATE_LENGTH: Int = 32

    /** Stores the state value for the active request. */
    var currentState = ""

    /** Stores the most recent request or redirect URL. */
    var currentUrl = ""

    /** Generates a state value when the request does not provide one. */
    var automaticStateGenerationEnabled = true

    /** Enables sending SDK error reports. */
    var sendErrorReportsEnabled = true

    /** Includes carrier headers in SDK error reports. */
    var errorReportEnableCarrierHeaders: Boolean = true

    /** Error-report request timeout in milliseconds. */
    var ERROR_REPORT_TIMEOUT: Long = 10000

    /** Legacy asset filename used for SDK configuration. */
//    var configFileName: String = "ipification-services.json"

    /** Prevents concurrent requests of the same SDK operation. */
    var enableSingleRequest = true

    /** Enables request count limiting. */
//    var enableLimitRequest = false

    /** Maximum requests allowed within the request-limit window. */
//    var MAX_REQUEST_NUMBER = 1

    /** Request-limit window duration in milliseconds. */
//    var MAX_REQUEST_TIME = 2000L

    /** Error-report label for coverage operations. */
    val COVERAGE_API_STR = "COVERAGE"

    /** Error-report label for authentication operations. */
    val AUTH_API_STR = "AUTH"

    /** Error-report label for token operations. */
    val TOKEN_API_STR = "TOKEN"

    /** Error-report label for TS43 operations. */
    val TS43_API_STR = "TS43"

    /** Error-report label for SMS operations. */
    val SMS_API_STR = "SMS"

    // IP Configuration

    /** Default OAuth scope for IP authentication requests. */
    var DEFAULT_SCOPE: String = "${AuthScope.OPENID} ${AuthScope.IP_PHONE_VERIFY}"

    /** Optional User-Agent header override for IP requests. */
    var OKHTTP_USER_AGENT: String = ""

    /** Accept header value used by IP requests. */
    var OKHTTP_ACCEPT = "*/*"

    /** Prefix added to the SDK version request header. */
    var SDK_TYPE_VALUE = ""

    /** Releases cellular network binding after a request finishes. */
    var autoUnregisterNetwork = true

    /** Limits network unbinding workarounds to affected device brands. */
    var onlyAffectedBrands: Boolean = false

    /** Enables OkHttp retries after connection failures. */
    var retryOnConnectionFailure = true

    /** Maximum connection retry count. */
    var MAX_RETRIES = 1

    /** Enables HTTP cookie handling for IP requests. */
    var enabledHandleCookie = false

    /** Makes sandbox coverage checks use the forced-true endpoint. */
    var coverageAlwaysTrue = false

    /** OAuth response type used by authentication requests. */
    var RESPONSE_TYPE_CODE: String = "code"

    /** Path appended when forced-true coverage is enabled. */
    var COVERAGE_PATH_FORCED_TRUE = "/1.2.3.4"

    /** Stores the current login hint used by IP requests. */
    var LOGIN_HINT = ""

    /** Sandbox coverage endpoint. */
    var COVERAGE_URL_STAGE = "https://api.stage.ipification.com/auth/realms/ipification/coverage"

    /** Production coverage endpoint. */
    var COVERAGE_URL_LIVE = "https://api.ipification.com/auth/realms/ipification/coverage"

    /** Sandbox authorization endpoint. */
    var AUTH_URL_STAGE = "https://api.stage.ipification.com/auth/realms/ipification/protocol/openid-connect/auth"

    /** Production authorization endpoint. */
    var AUTH_URL_LIVE = "https://api.ipification.com/auth/realms/ipification/protocol/openid-connect/auth"

    /** Sandbox SDK error-report endpoint. */
    var SDK_LOG_URL_STAGE = "https://api.stage.ipification.com/auth/realms/ipification/sdk"

    /** Production SDK error-report endpoint. */
    var SDK_LOG_URL_LIVE = "https://api.ipification.com/auth/realms/ipification/sdk"

    /** Optional resolved or custom coverage URL. */
    var COVERAGE_URL: Uri? = null

    /** Optional resolved or custom authorization URL. */
    var AUTHORIZATION_URL: Uri? = null

    /** Uses explicitly configured URLs instead of SDK defaults. */
    var customUrls = false

    /** Validates required IP request parameters before execution. */
    var enableParamsValidation = true

    /** Optional base URL used to build IP API endpoints. */
    var BASE_URL: String? = null

    /** Realm used to build default IP API paths. */
    var REALM: String = "ipification"

    /** Custom coverage path backing value. */
    private var _COVERAGE_PATH: String? = null

    /** Coverage path used with BASE_URL. */
    var COVERAGE_PATH: String
        get() = _COVERAGE_PATH ?: "/auth/realms/$REALM/coverage"
        set(value) { _COVERAGE_PATH = value }

    /** Custom authorization path backing value. */
    private var _AUTH_PATH: String? = null

    /** Authorization path used with BASE_URL. */
    var AUTH_PATH: String
        get() = _AUTH_PATH ?: "/auth/realms/$REALM/protocol/openid-connect/auth"
        set(value) { _AUTH_PATH = value }

    /** Custom SDK error-report path backing value. */
    private var _SDK_LOG_PATH: String? = null

    /** SDK error-report path used with BASE_URL. */
    var SDK_LOG_PATH: String
        get() = _SDK_LOG_PATH ?: "/auth/realms/$REALM/sdk"
        set(value) { _SDK_LOG_PATH = value }

    /** Coverage response read timeout in milliseconds. */
    var COVERAGE_READ_TIMEOUT: Long = 10000

    /** Coverage connection timeout in milliseconds. */
    var COVERAGE_CONNECT_TIMEOUT: Long = 10000

    /** Authentication response read timeout in milliseconds. */
    var AUTH_READ_TIMEOUT: Long = 10000

    /** Authentication connection timeout in milliseconds. */
    var AUTH_CONNECT_TIMEOUT: Long = 10000

    /** Cellular network acquisition timeout in milliseconds. */
    var CONNECT_NETWORK_TIMEOUT: Long = 5000

    /** Short cellular network acquisition timeout in milliseconds. */
    var CONNECT_NETWORK_TIMEOUT_SHORT: Long = 3000

    /** Consent identifier added to default IP requests. */
    var CONSENT_ID_VALUE = "ipconsent001eng"

    /** Delay before releasing the cellular network in milliseconds. */
    var TIMEOUT_RELEASE_NETWORK = 100L

    /** Default cellular network release delay in milliseconds. */
    var DEFAULT_TIMEOUT_RELEASE_NETWORK = 300L

    /** Stores the detected cellular private IP address. */
    var CELLULAR_PRIVATE_IP = ""

    /** Binds the whole application process to the cellular network. */
    var bindAppToCellularNetwork = false

    /** Uses an embedded WebView instead of direct API authentication. */
    var useWebViewInsteadOfApi = false

    /** Optional backend URL for exchanging an IP authorization code. */
    var IP_TOKEN_URL: String = ""

    // IM Configuration

    /** Persists active IM session data in app preferences. */
    var enable_Save_Session_In_Preference = true

    /** Android package name used to launch WhatsApp. */
    var whatsappPackageName: String = "com.whatsapp"

    /** Android package name used to launch Telegram. */
    var telegramPackageName: String = "org.telegram.messenger"

    /** Android package name used for Telegram web links. */
    var telegramWebPackageName: String = "org.telegram.messenger.web"

    /** Android package name used to launch Viber. */
    var viberPackageName: String = "com.viber.voip"

    /** Enables automatic IM authentication flow handling. */
    var IM_AUTO_MODE: Boolean = false

    /** Preferred IM application order. */
    var IM_PRIORITY_APP_LIST = arrayOf<String>()

    /** Automatically opens the only available IM application. */
    val ENABLE_AUTO_OPEN_IM_APP: Boolean = true

    /** Checks whether supported IM applications are installed. */
    var validateIMApps = true

    /** Notification identifier used by IM authentication. */
    var NOTIFICATION_ID = 9009

    /** Activity request code used by IM authentication. */
    var REQUEST_CODE = 2101

    // TS43 Configuration

    /** Sandbox backend base URL for TS43 operations. */
    var TS43_BACKEND_URL_SANDBOX: String = ""

    /** Production backend base URL for TS43 operations. */
    var TS43_BACKEND_URL_PRODUCTION: String = ""

    /** Endpoint path for TS43 authentication. */
    var TS43_AUTH_PATH: String = ""

    /** Endpoint path for TS43 token exchange. */
    var TS43_TOKEN_PATH: String = ""

    /** Default TS43 scope for phone verification. */
    var TS43_SCOPE_VERIFY_PHONE: String = "openid ip:phone_verify"

    /** Default TS43 scope for phone-number retrieval. */
    var TS43_SCOPE_GET_PHONE: String = "openid ip:phone"

    /** Default login hint used for TS43 phone-number retrieval. */
    var TS43_DEFALT_LOGIN_HINT_SCOPE_GET_PHONE: String = "anonymous"

    /** Default carrier hint used by TS43 requests. */
    var TS43_DEFAULT_CARRIER_HINT: String = ""


    /**
     * Get the resolved TS43 backend URL based on environment.
     * Priority:
     * 1. TS43_BACKEND_URL_SANDBOX (if ENV is SANDBOX)
     * 2. TS43_BACKEND_URL_PRODUCTION (default)
     */
    fun getTS43BackendUrl(): String {
        return if (ENV == IPEnvironment.SANDBOX) {
            TS43_BACKEND_URL_SANDBOX
        } else {
            TS43_BACKEND_URL_PRODUCTION
        }
    }

    // SMS Configuration

    /** Sandbox backend base URL for SMS operations. */
    var SMS_BACKEND_URL_SANDBOX: String = ""

    /** Production backend base URL for SMS operations. */
    var SMS_BACKEND_URL_PRODUCTION: String = ""

    /** Endpoint path that initiates SMS authentication. */
    var SMS_AUTH_PATH: String = "/sms/auth"

    /** Endpoint path that verifies OTP and exchanges tokens. */
    var SMS_TOKEN_PATH: String = "/sms/token"

    /** Default OAuth scope for SMS phone verification. */
    var SMS_SCOPE_VERIFY_PHONE: String = "openid ip:phone_verify"

    /**
     * Get the resolved SMS backend URL based on environment.
     * Priority:
     * 1. SMS_BACKEND_URL_SANDBOX (if ENV is SANDBOX)
     * 2. SMS_BACKEND_URL_PRODUCTION (default)
     */
    fun getSMSBackendUrl(): String {
        return if (ENV == IPEnvironment.SANDBOX) {
            SMS_BACKEND_URL_SANDBOX
        } else {
            SMS_BACKEND_URL_PRODUCTION
        }
    }

    fun generateState(): String {
        val sr = SecureRandom()
        val random = ByteArray(STATE_LENGTH)
        sr.nextBytes(random)
        var result = Base64.encodeToString(random, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        if(result.length > STATE_LENGTH){
            result = result.substring(0, STATE_LENGTH)
        }
        return "ip-sdk-android-$result"
    }

    private object Holder {
        val INSTANCE = IPConfiguration()
    }

    @Keep companion object {
        @JvmStatic
        fun getInstance(): IPConfiguration{
            return Holder.INSTANCE
        }
    }

    fun generateDeviceInfo(context: Context, phoneNumber: String): String{
       return DeviceUtils.getInstance(context).generateHeaderLogs(phoneNumber)
    }

//    internal fun getHostName() : String{
//        if(ENV == IPEnvironment.SANDBOX){
//            HOST_NAME = STAGE_HOST
//        }else{
//            HOST_NAME = PRODUCTION_HOST
//        }
//        return HOST_NAME
//    }
    internal fun getCheckCoverageUrl() : String{
        //implement https://github.com/ipification/ipification-android-sdk/issues/22
        if(BASE_URL.isNullOrEmpty()){
            if(ENV == IPEnvironment.SANDBOX){
                if (coverageAlwaysTrue){
                    return COVERAGE_URL_STAGE + COVERAGE_PATH_FORCED_TRUE
                }
                return COVERAGE_URL_STAGE
            }
            return COVERAGE_URL_LIVE
        }
        ENV = IPEnvironment.CUSTOM_URL
        if (coverageAlwaysTrue){
            return BASE_URL + COVERAGE_PATH + COVERAGE_PATH_FORCED_TRUE
        }
        return BASE_URL + COVERAGE_PATH
    }
    internal fun getAuthorizationUrl() : String{
        if(BASE_URL.isNullOrEmpty()){
            if (ENV == IPEnvironment.SANDBOX){
                return AUTH_URL_STAGE
            }
            return AUTH_URL_LIVE
        }
        ENV = IPEnvironment.CUSTOM_URL
        return BASE_URL + AUTH_PATH
    }


    // implement https://github.com/ipification/ipification-android-sdk/issues/23
    // start
    internal fun getSDKLogUrl() : String{
        if(BASE_URL.isNullOrEmpty()){
            if (ENV == IPEnvironment.SANDBOX){
                return SDK_LOG_URL_STAGE
            }
            return SDK_LOG_URL_LIVE
        }
        return BASE_URL + SDK_LOG_PATH
    }
    //end
}
