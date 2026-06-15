package com.ipification.demoapp.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.ipification.demoapp.manager.ConfigManager
import com.ipification.mobile.sdk.ip.IPEnvironment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Represents the navigation state from the PNV screen
sealed class PnvNavigation {
    data class ToProcess(val userFlow: String, val loginHint: String) : PnvNavigation()
    data object Idle : PnvNavigation()
}

// Represents the UI state for the PnvScreen
data class PnvState(
    val countryCode: String = "", // Default country code
    val phoneNumber: String = "",
    val error: String? = null,
    val errorType: String? = null,
    val navigation: PnvNavigation = PnvNavigation.Idle,
    val hasSavedPhoneNumber: Boolean = false
)

class PnvViewModel(
    private val environment: IPEnvironment,
    private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("ipification_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(PnvState())
    val state = _state.asStateFlow()

    var hasRequestedPhoneNumbers = false
        private set

    fun markPhoneNumbersRequested() {
        hasRequestedPhoneNumbers = true
    }
    
    init {
        // Load saved phone number
        val savedCountryCode = prefs.getString("last_country_code", null)
        val savedPhoneNumber = prefs.getString("last_phone_number", null)
        val defaultCountryCode = if (environment == IPEnvironment.SANDBOX) "381" else ""
        val defaultPhoneNumber = if (environment == IPEnvironment.SANDBOX) "123456789" else ""

        _state.update {
            it.copy(
                countryCode = savedCountryCode ?: defaultCountryCode,
                phoneNumber = savedPhoneNumber ?: defaultPhoneNumber,
                hasSavedPhoneNumber = !savedPhoneNumber.isNullOrEmpty()
            )
        }
    }

    fun onPhoneNumberChanged(newNumber: String) {
        _state.update { it.copy(phoneNumber = newNumber, error = null, errorType = null) }
    }

    fun clearSavedPhoneNumber() {
        prefs.edit().remove("last_country_code").remove("last_phone_number").apply()
        _state.update { it.copy(countryCode = "", phoneNumber = "", hasSavedPhoneNumber = false) }
    }

    fun onCountryChanged(newCountryCode: String) {
        _state.update { it.copy(countryCode = newCountryCode, error = null, errorType = null) }
    }

    fun onPhoneNumberFromHint(fullNumber: String, countryISO: String? = null) {
        val phoneUtil = PhoneNumberUtil.getInstance()
        try {
            val parsedNumber = phoneUtil.parse(fullNumber, countryISO)
            val countryDialCode = "+${parsedNumber.countryCode}"
            val nationalNumber = parsedNumber.nationalNumber.toString()
            _state.update { it.copy(countryCode = countryDialCode, phoneNumber = nationalNumber) }
        } catch (e: NumberParseException) {
            _state.update { it.copy(countryCode = fullNumber) }
        }
    }

    fun onCountryCodeFromHint(countryCode: String) {
        _state.update { it.copy(countryCode = countryCode) }
    }

    /**
     * Handles the verification button click.
     * @param isPnvPlus True if "Verify+" was clicked, false for regular "Verify".
     */
    fun onVerifyClicked(isPnvPlus: Boolean) {

        if (state.value.navigation !is PnvNavigation.Idle) {
            return
        }
        if(state.value.countryCode.isBlank() || state.value.countryCode.trim() == "+"){
            _state.update { it.copy(errorType = "code", error = "Country Code cannot be empty") }
            return
        }
        if (state.value.phoneNumber.isBlank()) {
            _state.update { it.copy(errorType = "number", error = "Phone Number cannot be empty") }
            return
        }

        val fullNumber = "${state.value.countryCode}${state.value.phoneNumber}"
        val formattedNumber = formatPhoneNumber(fullNumber)

        if (formattedNumber == null) {
            _state.update { it.copy(errorType = "number", error = "Invalid phone number") }
            return
        }

        val userFlow = if (isPnvPlus) "pvn_ip_plus" else "pvn_ip"

        // Save phone number for next time
        prefs.edit().apply {
            putString("last_country_code", state.value.countryCode)
            putString("last_phone_number", state.value.phoneNumber)
            apply()
        }

        _state.update {
            it.copy(navigation = PnvNavigation.ToProcess(userFlow, formattedNumber))
        }
    }

    fun onNavigationHandled() {
        _state.update { it.copy(navigation = PnvNavigation.Idle) }
    }



    /**
     * Handles the TS43 verification button click.
     */
    fun onTS43VerifyClicked() {
        if (state.value.navigation !is PnvNavigation.Idle) {
            return
        }
        if(state.value.countryCode.isBlank() || state.value.countryCode.trim() == "+"){
            _state.update { it.copy(errorType = "code", error = "Country Code cannot be empty") }
            return
        }
        if (state.value.phoneNumber.isBlank()) {
            _state.update { it.copy(errorType = "number", error = "Phone Number cannot be empty") }
            return
        }

        val fullNumber = "${state.value.countryCode}${state.value.phoneNumber}"
        val formattedNumber = formatPhoneNumber(fullNumber)

        if (formattedNumber == null) {
            _state.update { it.copy(errorType = "number", error = "Invalid phone number") }
            return
        }

        val userFlow = "pvn_sim"

        // Save phone number for next time
        prefs.edit().apply {
            putString("last_country_code", state.value.countryCode)
            putString("last_phone_number", state.value.phoneNumber)
            apply()
        }

        _state.update {
            it.copy(navigation = PnvNavigation.ToProcess(userFlow, formattedNumber))
        }
    }

    /**
     * Handles the SMS verification button click.
     */
    fun onSmsVerifyClicked() {
        if (state.value.navigation !is PnvNavigation.Idle) {
            return
        }
        if (state.value.countryCode.isBlank() || state.value.countryCode.trim() == "+") {
            _state.update { it.copy(errorType = "code", error = "Country Code cannot be empty") }
            return
        }
        if (state.value.phoneNumber.isBlank()) {
            _state.update { it.copy(errorType = "number", error = "Phone Number cannot be empty") }
            return
        }

        val fullNumber = "${state.value.countryCode}${state.value.phoneNumber}"
        val formattedNumber = formatPhoneNumber(fullNumber)

        if (formattedNumber == null) {
            _state.update { it.copy(errorType = "number", error = "Invalid phone number") }
            return
        }

        val userFlow = "pvn_sms"

        // Save phone number for next time
        prefs.edit().apply {
            putString("last_country_code", state.value.countryCode)
            putString("last_phone_number", state.value.phoneNumber)
            apply()
        }

        _state.update {
            it.copy(navigation = PnvNavigation.ToProcess(userFlow, formattedNumber))
        }
    }

    fun onMultiChannelVerifyClicked() {
        if (state.value.navigation !is PnvNavigation.Idle) {
            return
        }
        if (state.value.countryCode.isBlank() || state.value.countryCode.trim() == "+") {
            _state.update { it.copy(errorType = "code", error = "Country Code cannot be empty") }
            return
        }
        if (state.value.phoneNumber.isBlank()) {
            _state.update { it.copy(errorType = "number", error = "Phone Number cannot be empty") }
            return
        }

        val fullNumber = "${state.value.countryCode}${state.value.phoneNumber}"
        val formattedNumber = formatPhoneNumber(fullNumber)

        if (formattedNumber == null) {
            _state.update { it.copy(errorType = "number", error = "Invalid phone number") }
            return
        }

        prefs.edit().apply {
            putString("last_country_code", state.value.countryCode)
            putString("last_phone_number", state.value.phoneNumber)
            apply()
        }

        _state.update {
            it.copy(
                navigation = PnvNavigation.ToProcess("pvn_ip_multi_channel", formattedNumber)
            )
        }
    }

    /**
     * Formats a phone number to a standard E.164 format without symbols.
     */
    private fun formatPhoneNumber(number: String): String? {
        val phoneUtil = PhoneNumberUtil.getInstance()
        return try {
            val parsedNumber = phoneUtil.parse(number, null)
            if (!phoneUtil.isValidNumber(parsedNumber)) {
                return number.replace("+", "")
            }
            phoneUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
                .replace("+", "")
        } catch (e: NumberParseException) {
            number.replace("+", "")
        }
    }


    companion object {
        fun provideFactory(env: IPEnvironment, context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PnvViewModel(env, context) as T
            }
        }
    }

}
