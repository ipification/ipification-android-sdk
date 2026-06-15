package com.ipification.mobile.sdk.im.ui.fragment

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ipification.mobile.sdk.databinding.ImListFragmentBinding
import com.ipification.mobile.sdk.im.IMService
import com.ipification.mobile.sdk.im.adapter.IMListAdapter
import com.ipification.mobile.sdk.im.callback.OnIMItemClickListener
import com.ipification.mobile.sdk.im.callback.RedirectDataCallback
import com.ipification.mobile.sdk.im.data.IMInfo
import com.ipification.mobile.sdk.im.ui.IMVerificationActivity
import com.ipification.mobile.sdk.im.util.IMAPI
import com.ipification.mobile.sdk.ip.utils.IPLogs

/** Shows the selectable IM providers for manual verification. */
internal class IMListFragment : Fragment(), OnIMItemClickListener {

    private var providers: List<IMInfo> = emptyList()
    private var _binding: ImListFragmentBinding? = null
    private val binding: ImListFragmentBinding
        get() = requireNotNull(_binding)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        providers = readProvidersFromArguments()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ImListFragmentBinding.inflate(inflater, container, false)
        binding.rvProvider.adapter = IMListAdapter(providers, this)
        bindLocaleText()
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    /** Handles provider row selection, including pending-session recheck. */
    override fun onItemClick(item: IMInfo) {
        val verificationActivity = activity as? IMVerificationActivity ?: return
        if (verificationActivity.isPendingComplete) {
            verificationActivity.checkCompleteAgain {
                openProvider(item)
            }
        } else {
            openProvider(item)
        }
    }

    /** Resolves and opens the selected provider link. */
    private fun openProvider(item: IMInfo) {
        val verificationActivity = activity as? IMVerificationActivity ?: return
        verificationActivity.showLoading()

        runCatching {
            IMAPI.startGetRedirect(item.message, requireActivity(), object : RedirectDataCallback {
                override fun onResponse(link: String) {
                    val opened = IMAPI.openLink(requireActivity(), link)
                    verificationActivity.hideLoading()
                    if (!opened) {
                        showError("Unable to open ${item.getBrandName()}.")
                    }
                }
            })
        }.onFailure { error ->
            verificationActivity.hideLoading()
            Log.e(LOG_TAG, "Unable to open IM provider", error)
            showError(error.localizedMessage ?: GENERIC_ERROR_MESSAGE)
        }
    }

    /** Applies localized title and description text. */
    private fun bindLocaleText() {
        val locale = IMService.locale
        binding.tvTitle.text = locale.mainTitle
        binding.tvDesc.text = locale.description
        IPLogs.getInstance().LOG += "show IM Screen\n"
    }

    private fun readProvidersFromArguments(): List<IMInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelableArrayList(ARG_IM_LIST, IMInfo::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelableArrayList<IMInfo>(ARG_IM_LIST).orEmpty()
        }
    }

    private fun showError(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        /** Creates a fragment containing the supplied provider list. */
        fun newInstance(validApp: List<IMInfo>): IMListFragment {
            return IMListFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_IM_LIST, ArrayList(validApp))
                }
            }
        }

        private const val ARG_IM_LIST = "IM_LIST"
        private const val GENERIC_ERROR_MESSAGE = "An error occurred"
        private const val LOG_TAG = "IMListFragment"
    }
}
