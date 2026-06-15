package com.ipification.mobile.sdk.im.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.R
import com.ipification.mobile.sdk.databinding.ImItemBinding
import com.ipification.mobile.sdk.im.IMService
import com.ipification.mobile.sdk.im.callback.OnIMItemClickListener
import com.ipification.mobile.sdk.im.data.IMInfo

/** Displays the available IM providers and reports provider selections. */
internal class IMListAdapter(
    private val providers: List<IMInfo>,
    private val itemClickListener: OnIMItemClickListener
) : Adapter<IMViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IMViewHolder {
        val binding = ImItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IMViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IMViewHolder, position: Int) {
        val provider = providers[position]
        bindProvider(holder.binding, provider)
        bindAvailability(holder.binding, provider)
    }

    override fun getItemCount(): Int = providers.size

    /** Applies provider-specific text, background, and icon resources. */
    private fun bindProvider(binding: ImItemBinding, provider: IMInfo) {
        val context = binding.root.context
        val providerName = provider.getBrandName().lowercase()

        binding.tvProvider.setTextColor(ContextCompat.getColor(context, android.R.color.white))

        when (providerName) {
            WHATSAPP -> {
                binding.root.setBackgroundResource(R.drawable.im_bg_whatsapp_radius)
                binding.tvProvider.text = IMService.locale.whatsappText
                binding.ivProviderIcon.setImageResource(R.drawable.ic_whatsapp)
            }

            VIBER -> {
                binding.root.setBackgroundResource(R.drawable.im_bg_viber_radius)
                binding.tvProvider.text = IMService.locale.viberText
                binding.ivProviderIcon.setImageResource(R.drawable.ic_viber)
            }

            TELEGRAM -> {
                binding.root.setBackgroundResource(R.drawable.im_bg_telegram_radius)
                binding.tvProvider.text = IMService.locale.telegramText
                binding.ivProviderIcon.setImageResource(R.drawable.ic_telegram)
            }

            else -> {
                binding.root.setBackgroundResource(R.drawable.im_bg_grey)
                binding.tvProvider.text = provider.brand
                binding.tvProvider.setTextColor(
                    ContextCompat.getColor(context, android.R.color.black)
                )
                binding.ivProviderIcon.setImageDrawable(null)
            }
        }
    }

    /** Enables selection unless installed-app validation rejects the provider. */
    private fun bindAvailability(binding: ImItemBinding, provider: IMInfo) {
        val isEnabled = provider.isInstalled ||
            !IPConfiguration.getInstance().validateIMApps

        binding.root.alpha = if (isEnabled) ENABLED_ALPHA else DISABLED_ALPHA
        binding.root.isEnabled = isEnabled
        binding.root.isClickable = isEnabled
        binding.root.setOnClickListener {
            if (isEnabled) {
                itemClickListener.onItemClick(provider)
            }
        }
    }

    private companion object {
        const val WHATSAPP = "whatsapp"
        const val VIBER = "viber"
        const val TELEGRAM = "telegram"
        const val ENABLED_ALPHA = 1f
        const val DISABLED_ALPHA = 0.2f
    }
}
