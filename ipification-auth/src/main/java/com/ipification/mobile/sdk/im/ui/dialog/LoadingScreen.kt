package com.ipification.mobile.sdk.im.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import com.ipification.mobile.sdk.databinding.LoadingDialogBinding

/**
 * Non-cancelable full-screen progress dialog used while an IM verification step is running.
 */
internal class LoadingScreen(context: Context) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val binding = LoadingDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCanceledOnTouchOutside(false)
        setCancelable(false)
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    /** Shows the loading dialog when it is not already visible. */
    fun showLoading() {
        if (!isShowing) {
            show()
        }
    }

    /** Dismisses the loading dialog when it is currently visible. */
    fun hideLoading() {
        if (isShowing) {
            dismiss()
        }
    }
}
