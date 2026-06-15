package com.ipification.mobile.sdk.im.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.R
import com.ipification.mobile.sdk.databinding.ActivityImVerificationBinding
import com.ipification.mobile.sdk.im.IMService
import com.ipification.mobile.sdk.im.base.BaseVerificationActivity
import com.ipification.mobile.sdk.im.callback.RedirectDataCallback
import com.ipification.mobile.sdk.im.data.IMInfo
import com.ipification.mobile.sdk.im.data.SessionResponse
import com.ipification.mobile.sdk.im.di.RepositoryModule
import com.ipification.mobile.sdk.im.util.IMAPI
import com.ipification.mobile.sdk.im.util.VerificationExtensionKt
import com.ipification.mobile.sdk.ip.exception.CellularException
import com.ipification.mobile.sdk.ip.response.AuthApiResponse
import com.ipification.mobile.sdk.ip.utils.IPConstant
import com.ipification.mobile.sdk.ip.utils.IPLogs

/** Hosts the IM provider selection and session-completion flow. */
class IMVerificationActivity : BaseVerificationActivity() {

    lateinit var binding: ActivityImVerificationBinding
        private set

    internal var isPendingComplete = false

    private val configuration = IPConfiguration.getInstance()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionRepository
        get() = RepositoryModule.getInstance().getSessionRepository()

    private var receivedNewIntent = false

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        customizeToolbar(binding.toolbar)
        initializeProviderScreen()
    }

    override fun getBindingRoot(): LinearLayout {
        binding = ActivityImVerificationBinding.inflate(layoutInflater)
        binding.ivBackBtn.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        return binding.root
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        receivedNewIntent = true
        finishSessionIfRequired(intent)
    }

    override fun onResume() {
        super.onResume()
        if (receivedNewIntent) {
            receivedNewIntent = false
        } else {
            finishSessionIfRequired(intent)
        }
    }

    /** Initializes automatic provider opening or displays provider selection. */
    private fun initializeProviderScreen() {
        if (intent?.extras?.getBoolean(INIT_EXTRA) != true) return

        val sessionInfo = sessionRepository.getSavedSessionInfo(applicationContext)
        Log.d(LOG_TAG, "sessionInfo $sessionInfo")

        if (sessionInfo == null) {
            showErrorAndFinish(UNEXPECTED_INIT_ERROR)
            return
        }

        val providers = sessionInfo.convertToIMList()
        if (configuration.IM_AUTO_MODE) {
            val provider = VerificationExtensionKt.findFirstInstalledApp(providers, packageManager)
            if (provider == null) {
                showUnavailableAndFinish()
            } else {
                openProviderAfterDelay(provider)
            }
            return
        }

        val installedProviders = VerificationExtensionKt
            .checkInstalledApp(providers, packageManager)
            .filter(IMInfo::isInstalled)

        when {
            installedProviders.isEmpty() -> showUnavailableAndFinish()
            installedProviders.size == 1 && configuration.ENABLE_AUTO_OPEN_IM_APP ->
                openProviderAfterDelay(installedProviders.first())
            else -> showProviderSelection(installedProviders)
        }
    }

    /** Opens an IM provider after showing its localized loading text. */
    private fun openProviderAfterDelay(provider: IMInfo) {
        val providerName = provider.getBrandName().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        binding.loadingTextView.text = String.format(IMService.locale.loadingText, providerName)
        binding.verificationContainer.visibility = View.GONE

        mainHandler.postDelayed({
            IMAPI.startGetRedirect(provider.message, this, object : RedirectDataCallback {
                override fun onResponse(link: String) {
                    IMAPI.openLink(this@IMVerificationActivity, link)
                }
            })
        }, PROVIDER_OPEN_DELAY_MS)
    }

    /** Displays the installed IM providers for manual selection. */
    private fun showProviderSelection(providers: List<IMInfo>) {
        binding.verificationContainer.visibility = View.VISIBLE
        replaceFragmentWith(
            VerificationExtensionKt.chooseStartScreen(providers),
            false,
            R.id.verification_container
        )
    }

    /** Completes the saved session after the user returns from an IM provider. */
    private fun finishSessionIfRequired(intent: Intent?) {
        if (getSessionId() == null) return

        runCatching {
            NotificationManagerCompat.from(this).cancel(configuration.NOTIFICATION_ID)
        }.onFailure { Log.e(LOG_TAG, "Unable to cancel IM notification", it) }

        if (intent?.extras?.getBoolean(INIT_EXTRA) == true) {
            intent.replaceExtras(null)
            return
        }

        binding.loadingTextView.text = IMService.locale.checkingResult
        if (configuration.IM_AUTO_MODE) {
            mainHandler.postDelayed({ returnToApp(null) }, AUTO_MODE_RETURN_DELAY_MS)
            return
        }

        showLoading()
        IPLogs.getInstance().LOG += "completeSession - start \n"

        val result = sessionRepository.completeSession(this)
        if (result == null) {
            hideLoading()
            showToast(UNEXPECTED_COMPLETE_ERROR)
            returnErrorToApp()
            return
        }

        result.observe(this, ::handleSessionResult)
    }

    /** Handles the completed, pending, or failed session result. */
    private fun handleSessionResult(result: SessionResponse?) {
        Log.d(LOG_TAG, "complete session result: ${result?.isSuccess} ${result?.exception?.errorDescription}")
        isPendingComplete = false

        if (result?.isSuccess == true) {
            IPLogs.getInstance().LOG +=
                "completeSession - success: ${result.response?.authorizationCode} \n"
            result.response?.let(::returnToApp)
            return
        }

        val errorDescription = result?.exception?.errorDescription
        IPLogs.getInstance().LOG += "completeSession - error: $errorDescription\n"

        when (errorDescription) {
            SESSION_NOT_FOUND -> showSessionAlert(
                IMService.locale.sessionNotFoundMessage,
                IPConstant.IM_SESSION_NOT_FOUND_OR_EXPIRED
            )
            SESSION_FINISHED -> showSessionAlert(
                IMService.locale.sessionAlreadyCompletedMessage,
                IPConstant.IM_SESSION_COMPLETED_ERROR
            )
            SESSION_PENDING -> showPendingProviderSelection()
        }
        hideLoading()
    }

    /** Restores provider selection while the IM session is still pending. */
    private fun showPendingProviderSelection() {
        isPendingComplete = true
        val sessionInfo = sessionRepository.getSavedSessionInfo(applicationContext) ?: return
        val installedProviders = VerificationExtensionKt
            .checkInstalledApp(sessionInfo.convertToIMList(), packageManager)
            .filter(IMInfo::isInstalled)

        if (installedProviders.size == 1 && configuration.ENABLE_AUTO_OPEN_IM_APP) {
            showProviderSelection(installedProviders)
        }
    }

    /** Rechecks a pending session before allowing another provider selection. */
    fun checkCompleteAgain(onFailure: () -> Unit) {
        showLoading()
        IPLogs.getInstance().LOG += "retry to completeSession - start \n"

        val result = sessionRepository.completeSession(this)
        if (result == null) {
            hideLoading()
            onFailure()
            return
        }

        result.observe(this) {
            hideLoading()
            if (it?.isSuccess == true) {
                IPLogs.getInstance().LOG +=
                    "retry to completeSession - success: ${it.response?.authorizationCode} \n"
                it.response?.let(::returnToApp)
            } else {
                onFailure()
            }
        }
    }

    /** Returns an error result to the host application. */
    private fun returnErrorToApp(exception: CellularException? = null) {
        finishWithResult(errorMessage = exception?.getErrorMessage())
    }

    /** Returns a completed-session error to the host application. */
    private fun returnCompletedToApp(errorMessage: String) {
        finishWithResult(errorMessage = errorMessage)
    }

    /** Returns an authentication response to the host application. */
    private fun returnToApp(response: AuthApiResponse?) {
        val resolvedResponse = if (configuration.IM_AUTO_MODE && response == null) {
            val url = "${configuration.REDIRECT_URI}?state=${configuration.currentState}"
            AuthApiResponse(Activity.RESULT_OK, url, null)
        } else {
            response
        }

        finishWithResult(responseData = resolvedResponse?.responseData)
    }

    /** Sets the activity result and closes the IM verification screen. */
    private fun finishWithResult(errorMessage: String? = null, responseData: String? = null) {
        val resultIntent = Intent().apply {
            putExtra(IPConstant.IM_SESSION_ID, getSessionId())
            putExtra(IPConstant.ERROR_MESSAGE, errorMessage)
            putExtra(IPConstant.IP_RESPONSE_DATA, responseData)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    /** Displays a session error and returns its mapped result when dismissed. */
    private fun showSessionAlert(message: String, resultError: String) {
        runCatching {
            AlertDialog.Builder(this)
                .setTitle(IMService.locale.errorTitle)
                .setMessage(message)
                .setPositiveButton(IMService.locale.errorButtonText) { _, _ ->
                    returnCompletedToApp(resultError)
                }
                .show()
        }.onFailure {
            Log.e(LOG_TAG, "Unable to display session alert", it)
            returnCompletedToApp(resultError)
        }
    }

    /** Shows the unavailable-provider message and closes the screen. */
    private fun showUnavailableAndFinish() {
        showErrorAndFinish(PROVIDER_UNAVAILABLE_ERROR)
    }

    /** Shows an error message and closes the screen. */
    private fun showErrorAndFinish(message: String) {
        showToast(message)
        finish()
    }

    /** Shows a short user-facing message when the activity is available. */
    private fun showToast(message: String) {
        runCatching { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
            .onFailure { Log.e(LOG_TAG, "Unable to display message", it) }
    }

    /** Returns the currently saved IM session identifier. */
    private fun getSessionId(): String? {
        return sessionRepository.getSavedSessionInfo(applicationContext)?.sessionId
    }

    private companion object {
        const val LOG_TAG = "IMVerificationActivity"
        const val INIT_EXTRA = "init"
        const val SESSION_NOT_FOUND = "not_found"
        const val SESSION_FINISHED = "finished"
        const val SESSION_PENDING = "pending"
        const val PROVIDER_OPEN_DELAY_MS = 500L
        const val AUTO_MODE_RETURN_DELAY_MS = 2_000L
        const val PROVIDER_UNAVAILABLE_ERROR =
            "The supported instant messaging application is currently unavailable."
        const val UNEXPECTED_INIT_ERROR =
            "Error: An unexpected error occurred. Please try again later. [1019]"
        const val UNEXPECTED_COMPLETE_ERROR =
            "Error: An unexpected error occurred. Please try again later. [1020]"
    }
}
