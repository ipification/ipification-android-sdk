package com.ipification.mobile.sdk.im.base

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.ipification.mobile.sdk.im.IMService
import com.ipification.mobile.sdk.im.ui.dialog.LoadingScreen

/**
 * Provides shared layout, theme, loading, and fragment behavior for IM verification screens.
 */
abstract class BaseVerificationActivity : AppCompatActivity() {

    private val loadingScreenDelegate = lazy { LoadingScreen(this) }
    private val loadingScreen by loadingScreenDelegate

    /**
     * Called after the activity content view is created.
     *
     * Subclasses should initialize their screen and restore state here.
     */
    abstract fun onCreateActivity(savedInstanceState: Bundle?)

    /** Creates and returns the activity's root content view. */
    abstract fun getBindingRoot(): View

    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(Window.FEATURE_ACTION_BAR)
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()
        setContentView(getBindingRoot())
        applyBackgroundTheme()
        onCreateActivity(savedInstanceState)
    }

    override fun onDestroy() {
        hideLoading()
        super.onDestroy()
    }

    /** Applies configured IM text, visibility, and colors to the toolbar. */
    protected fun customizeToolbar(toolbar: TextView) {
        val locale = IMService.locale
        val theme = IMService.theme

        locale.toolbarTitle?.let { toolbar.text = it }
        toolbar.visibility = locale.toolbarVisibility
        theme?.toolbarTextColor?.let(toolbar::setTextColor)
        theme?.toolbarColor?.let(toolbar::setBackgroundColor)
    }

    /** Displays the full-screen loading dialog on the UI thread. */
    fun showLoading() {
        runOnUiThread {
            if (!isActivityClosing()) {
                runCatching { loadingScreen.showLoading() }
                    .onFailure { logFailure("Unable to show loading screen", it) }
            }
        }
    }

    /** Hides the full-screen loading dialog on the UI thread. */
    fun hideLoading() {
        if (!loadingScreenDelegate.isInitialized()) return

        runOnUiThread {
            runCatching { loadingScreen.hideLoading() }
                .onFailure { logFailure("Unable to hide loading screen", it) }
        }
    }

    /** Adds a fragment to a container when the fragment is available. */
    protected fun addFragmentTo(
        containerId: Int,
        fragment: Fragment?,
        addToBackStack: Boolean
    ) {
        fragment ?: return

        runCatching {
            supportFragmentManager.beginTransaction()
                .add(containerId, fragment)
                .apply {
                    if (addToBackStack) {
                        addToBackStack(fragment.javaClass.simpleName)
                    }
                }
                .commit()
        }.onFailure { logFailure("Unable to add fragment", it) }
    }

    /** Replaces a container's current fragment when the fragment is available. */
    protected fun replaceFragmentWith(
        fragment: Fragment?,
        addToBackStack: Boolean,
        containerId: Int
    ) {
        fragment ?: return

        runCatching {
            val tag = fragment.javaClass.simpleName
            supportFragmentManager.beginTransaction()
                .replace(containerId, fragment, tag)
                .apply {
                    if (addToBackStack) {
                        addToBackStack(tag)
                    }
                }
                .commit()
        }.onFailure { logFailure("Unable to replace fragment", it) }
    }

    /** Applies the configured background color to the activity window. */
    private fun applyBackgroundTheme() {
        IMService.theme?.backgroundColor?.let(window.decorView::setBackgroundColor)
    }

    /** Returns whether the activity can no longer safely display a dialog. */
    private fun isActivityClosing(): Boolean {
        return isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed)
    }

    /** Logs a recoverable IM screen failure. */
    private fun logFailure(message: String, throwable: Throwable) {
        Log.e(LOG_TAG, message, throwable)
    }

    private companion object {
        const val LOG_TAG = "BaseVerificationAct"
    }
}
