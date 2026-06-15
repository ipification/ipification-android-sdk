package com.ipification.mobile.sdk.ip.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceResponse
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.R
import com.ipification.mobile.sdk.ip.utils.IPLogs
import com.ipification.mobile.sdk.ip.utils.LogUtils

/**
 * Internal browser screen used when IP authentication must complete inside a WebView.
 */
class AuthWebViewActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_URL = "extra_url"

        private const val TAG = "AuthWebViewActivity"
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth_webview)

        applyStatusBarStyle()

        webView = findViewById(R.id.ip_webview)

        // Topbar back button handler
        findViewById<View>(R.id.ip_btn_back)?.setOnClickListener {
            handleBackNavigation()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        configureWebView(webView.settings)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(v: WebView?, isDialog: Boolean, isUserGesture: Boolean, msg: Message?): Boolean {
                val transport = msg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = webView
                msg.sendToTarget()
                return true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString()
                logDebug("shouldOverrideUrlLoading(request): ${target.safeUrlForLog()}")
                if (maybeHandleRedirect(target)) return true
                return false // Let WebView handle all navigations
            }

            // Deprecated callback for API < 21
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                logDebug("shouldOverrideUrlLoading(url): ${url.safeUrlForLog()}")
                if (maybeHandleRedirect(url)) return true
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                logDebug("onPageStarted: ${url.safeUrlForLog()}")
                if (maybeHandleRedirect(url)) return
                findViewById<View>(R.id.ip_progress)?.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                logDebug("onPageFinished: ${url.safeUrlForLog()}")
                findViewById<View>(R.id.ip_progress)?.visibility = View.GONE
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                logDebug("onReceivedError: code=$errorCode, desc=$description, url=${failingUrl.safeUrlForLog()}")
                finishWithError("$errorCode:$description")
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !request.isForMainFrame) return

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val description = error.description?.toString()
                    logDebug("onReceivedError(23+): code=${error.errorCode}, desc=$description, url=${request.url.toString().safeUrlForLog()}")
                    finishWithError("${error.errorCode}:$description")
                } else {
                    finishWithError("webview_error")
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !request.isForMainFrame) return

                logDebug("onReceivedHttpError: status=${errorResponse.statusCode}, reason=${errorResponse.reasonPhrase}, url=${request.url.toString().safeUrlForLog()}")
                finishWithError("HTTP ${errorResponse.statusCode}:${errorResponse.reasonPhrase}")
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                logDebug("onReceivedSslError: ${error?.primaryError}")
                finishWithError("SSL ${error?.primaryError}")
            }
        }

        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        if (url.isNotEmpty()) {
            logDebug("Loading initial URL: ${url.safeUrlForLog()}")
            webView.loadUrl(url)
        } else {
            finishWithError("Missing WebView URL")
        }
    }

    override fun onDestroy() {
        if (this::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun applyStatusBarStyle() {
        // Keep status-bar icons visible across API levels supported by the SDK.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.statusBarColor = Color.WHITE
            val decor = window.decorView
            decor.systemUiVisibility = decor.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.gray_200)
        }
    }

    private fun configureWebView(settings: WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.setSupportMultipleWindows(true)
    }

    private fun handleBackNavigation() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
            return
        }

        logDebug("User cancelled WebView auth")
        WebViewAuthBridge.notifyCancel()
        finishSafely()
    }

    private fun logDebug(message: String) {
        if (IPConfiguration.getInstance().debug) {
            Log.d(TAG, message)
            IPLogs.getInstance().LOG += "${LogUtils.currentTimestamp()} - WebView - $message\n"
        }
    }

    /** Matches the configured redirect URI while preserving the full redirect URL for the callback. */
    private fun maybeHandleRedirect(url: String?): Boolean {
        try {
            val conf = IPConfiguration.getInstance().REDIRECT_URI ?: return false
            val current = url ?: return false

            val confUri = conf
            val currUri = Uri.parse(current)

            // Normalize components (lowercase host, trimmed paths)
            val confScheme = (confUri.scheme ?: "").lowercase()
            val currScheme = (currUri.scheme ?: "").lowercase()
            val confHost = (confUri.host ?: "").lowercase()
            val currHost = (currUri.host ?: "").lowercase()
            val confPath = (confUri.encodedPath ?: "/").trimEnd('/')
            val currPath = (currUri.encodedPath ?: "/").trimEnd('/')

            // Match on scheme+host+path; for custom schemes some providers use no host => fall back to scheme+path
            val hostMatch = confHost.isNotEmpty() && currHost.isNotEmpty() && confHost == currHost
            val schemePathMatch = confScheme == currScheme && confPath == currPath
            val match = schemePathMatch && (hostMatch || confHost.isEmpty())

            if (match) {
                logDebug("Detected redirect: $currScheme://$currHost$currPath")
                WebViewAuthBridge.notifySuccess(current)
                finishSafely()
                return true
            }

            // Also accept prefix match when redirect_uri includes fixed query params
            val confNoQuery = Uri.Builder()
                .scheme(confScheme)
                .authority(confHost)
                .encodedPath(confPath)
                .build()
                .toString()

            if (current.startsWith(confNoQuery)) {
                logDebug("Detected redirect (prefix): ${current.safeUrlForLog()}")
                WebViewAuthBridge.notifySuccess(current)
                finishSafely()
                return true
            }
        } catch (e: Exception) {
            logDebug("maybeHandleRedirect exception: ${e.localizedMessage}")
        }
        return false
    }

    private fun finishWithError(message: String?) {
        WebViewAuthBridge.notifyError(message)
        finishSafely()
    }

    private fun finishSafely() {
        runCatching { finish() }
    }

    private fun String?.safeUrlForLog(): String {
        val raw = this ?: return ""
        return runCatching {
            Uri.parse(raw).buildUpon()
                .encodedQuery(null)
                .fragment(null)
                .build()
                .toString()
        }.getOrDefault("[invalid-url]")
    }
}
