package com.ipification.demoapp.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipification.demoapp.BuildConfig
import com.ipification.demoapp.manager.ConfigManager
import com.ipification.demoapp.model.config.ClientConfig
import com.ipification.demoapp.repository.ConfigRepository
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.IPEnvironment
import com.ipification.mobile.sdk.ip.utils.IPLogs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Represents the different navigation destinations from the main screen
sealed class MainScreenNavigation {
    data object PNV : MainScreenNavigation()
    data class Process(
        val userFlow: String,
        val loginHint: String? = null
    ) : MainScreenNavigation()
    data object Idle : MainScreenNavigation()
}

// Represents the state of the MainScreen
data class MainScreenState(
    val selectedEnvironment: IPEnvironment = IPEnvironment.PRODUCTION,
    val selectedServerId: String? = null,
    val showInfoDialog: Boolean = false,
    val infoDialogMessage: String = "",
    val navigation: MainScreenNavigation = MainScreenNavigation.Idle,
    val isLoadingConfig: Boolean = true,
    val configError: String? = null,
    val availableClients: List<ClientConfig> = emptyList(),
    val availableAuthServers: List<com.ipification.demoapp.model.config.AuthServer> = emptyList()
)

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(MainScreenState())
    val state = _state.asStateFlow()
    
    private val configRepository = ConfigRepository()

    init {
        // Initialize the IPConfiguration based on the default state
        updateIpificationConfiguration()
        loadConfig()
    }
    
    fun reloadConfig() {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingConfig = true, configError = null) }
            
            val result = configRepository.fetchConfig()
            result.fold(
                onSuccess = { config ->
                    ConfigManager.setConfig(config)
                    
                    // Set default selected server to the first auth server
                    val firstServer = config.authServers?.firstOrNull()
                    if (firstServer != null) {
                        ConfigManager.setSelectedServer(firstServer.id)
                    }
                    
                    _state.update { 
                        it.copy(
                            isLoadingConfig = false,
                            selectedServerId = firstServer?.id,
                            availableClients = config.clients ?: emptyList(),
                            availableAuthServers = config.authServers ?: emptyList(),
                            configError = null
                        )
                    }
                    
                    // Update IP configuration with the selected server
                    updateIpificationConfiguration()
                    
                    Log.d("MainViewModel", "Config loaded successfully with ${config.clients?.size ?: 0} clients and ${config.authServers?.size ?: 0} auth servers. Selected server: ${firstServer?.id}")
                },
                onFailure = { error ->
                    _state.update { 
                        it.copy(
                            isLoadingConfig = false,
                            configError = error.message ?: "Failed to load configuration"
                        )
                    }
                    Log.e("MainViewModel", "Failed to load config", error)
                }
            )
        }
    }

    /**
     * Handles the change of environment between PRODUCTION and SANDBOX.
     */
    fun onEnvironmentChanged(newEnvironment: IPEnvironment) {
        if (state.value.selectedEnvironment != newEnvironment) {
            _state.update { it.copy(selectedEnvironment = newEnvironment) }
            updateIpificationConfiguration()
        }
    }

    fun onServerChanged(serverId: String) {
        ConfigManager.setSelectedServer(serverId)
        _state.update { it.copy(selectedServerId = serverId) }
        updateIpificationConfiguration()
    }

    /**
     * Navigates to the Phone Number Verification (PNV) flow.
     */
    fun onPnvClicked() {
        if (state.value.navigation !is MainScreenNavigation.Idle) {
            return
        }
        IPConfiguration.getInstance().customUrls = false

        _state.update { it.copy(navigation = MainScreenNavigation.PNV) }
    }

    fun onAutomaticPnvVerifyClicked(phoneNumber: String, countryIso: String? = null) {
        if (state.value.navigation !is MainScreenNavigation.Idle) {
            return
        }

        val formattedNumber = formatPhoneNumber(phoneNumber, countryIso)
        if (formattedNumber == null) {
            _state.update { it.copy(navigation = MainScreenNavigation.PNV) }
            return
        }

        val client = state.value.availableClients.firstOrNull { it.userFlow == "pvn_ip" }
            ?: state.value.availableClients.firstOrNull { it.userFlow.startsWith("pvn_ip") }

        if (client == null) {
            Log.e("MainViewModel", "No PNV client found in API config")
            _state.update {
                it.copy(
                    showInfoDialog = true,
                    infoDialogMessage = "Phone Number Verification is not available in current config."
                )
            }
            return
        }

        IPConfiguration.getInstance().customUrls = false

        val process = MainScreenNavigation.Process(client.userFlow, formattedNumber)
        _state.update { it.copy(navigation = process) }
    }

    fun onAutomaticPnvVerifyUnavailable() {
        _state.update { it.copy(navigation = MainScreenNavigation.PNV) }
    }

    private fun formatPhoneNumber(number: String, countryIso: String? = null): String? {
        val cleanedNumber = number.trim().takeIf { it.isNotEmpty() } ?: return null
        val phoneUtil = PhoneNumberUtil.getInstance()
        return try {
            val parsedNumber = phoneUtil.parse(cleanedNumber, countryIso)
            if (!phoneUtil.isValidNumber(parsedNumber)) {
                cleanedNumber.replace("+", "").takeIf { it.isNotBlank() }
            } else {
                phoneUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164).replace("+", "")
            }
        } catch (e: NumberParseException) {
            cleanedNumber.replace("+", "").takeIf { it.isNotBlank() }
        }
    }

    /**
     * Navigates to the Quick Login flow using first available login client from API config.
     */
    fun onQuickLoginClicked() {
        IPConfiguration.getInstance().customUrls = false
        if (state.value.navigation !is MainScreenNavigation.Idle) {
            return
        }
        
        // Find first login client from API config
        val loginClient = state.value.availableClients.firstOrNull { it.userFlow == "login_ip" }
            ?: state.value.availableClients.firstOrNull { it.userFlow == "login_ip_plus" }
        if (loginClient == null) {
            Log.e("MainViewModel", "No login client found in API config")
            return
        }
        
        val process = MainScreenNavigation.Process(loginClient.userFlow)
        _state.update { it.copy(navigation = process) }
    }
    
    /**
     * Navigates to the TS43 Quick Login flow using first available TS43 client from API config.
     */
    fun onTS43QuickLoginClicked() {
        IPConfiguration.getInstance().customUrls = false
        if (state.value.navigation !is MainScreenNavigation.Idle) {
            return
        }
        
        // Find first TS43 client from API config (includes _sim flows)
        val ts43Client = state.value.availableClients.firstOrNull { it.userFlow.contains("ts43") || it.userFlow.endsWith("_sim") }
        if (ts43Client == null) {
            Log.e("MainViewModel", "No TS43 client found in API config")
            return
        }
        
        val process = MainScreenNavigation.Process(ts43Client.userFlow, "anonymous")
        _state.update { it.copy(navigation = process) }
    }
    
    /**
     * Navigates to the Anonymous Login flow using anonymous client from API config.
     */
    fun onAnonymousLoginClicked() {
        IPConfiguration.getInstance().customUrls = false
        if (state.value.navigation !is MainScreenNavigation.Idle) {
            return
        }
        
        // Find anonymous client from API config
        val anonymousClient = state.value.availableClients.firstOrNull { it.userFlow == "anonymous" }
        if (anonymousClient == null) {
            Log.e("MainViewModel", "No anonymous client found in API config")
            return
        }
        
        val process = MainScreenNavigation.Process(anonymousClient.userFlow)
        _state.update { it.copy(navigation = process) }
    }

    /**
     * Resets the navigation state after navigation has occurred.
     */
    fun onNavigationHandled() {
        _state.update { it.copy(navigation = MainScreenNavigation.Idle) }
    }
    
    /**
     * Navigates to a flow based on the user_flow identifier from the API config
     */
    fun onFlowClicked(userFlow: String) {
        if (state.value.navigation !is MainScreenNavigation.Idle) {
            return
        }
        
        val client = ConfigManager.getClientByUserFlow(userFlow)
        if (client == null) {
            Log.e("MainViewModel", "Client config not found for user_flow: $userFlow")
            return
        }
        
        IPConfiguration.getInstance().customUrls = false
        
        when (userFlow) {
            "pvn_ip", "pvn_ip_plus", "pvn_im", "pvn_sim", "pvn_sms" -> {
                _state.update { it.copy(navigation = MainScreenNavigation.PNV) }
            }
            "login_ip", "login_ip_plus", "login_sim" -> {
                val process = MainScreenNavigation.Process(userFlow)
                _state.update { it.copy(navigation = process) }
            }
            "login_im" -> {
                val process = MainScreenNavigation.Process(userFlow)
                _state.update { it.copy(navigation = process) }
            }
            "anonymous" -> {
                val process = MainScreenNavigation.Process(userFlow)
                _state.update { it.copy(navigation = process) }
            }
            "kyc_phone" -> {
                val process = MainScreenNavigation.Process(userFlow)
                _state.update { it.copy(navigation = process) }
            }
            "sample_app_im_backchannel" -> {
                val process = MainScreenNavigation.Process(userFlow)
                _state.update { it.copy(navigation = process) }
            }
            else -> {
                Log.w("MainViewModel", "Unknown user_flow: $userFlow, defaulting to Process flow")
                val process = MainScreenNavigation.Process(userFlow)
                _state.update { it.copy(navigation = process) }
            }
        }
    }


    /**
     * Prepares and shows the information dialog with current configuration details.
     */
    fun onInfoClicked() {
        val config = IPConfiguration.getInstance()
        val message = """
            AppVersion: ${BuildConfig.VERSION_NAME}
            ClientId: ${config.CLIENT_ID.orEmpty()}
            RedirectUri: ${config.REDIRECT_URI ?: ""}
            Url: ${config.AUTHORIZATION_URL}
        """.trimIndent()

        _state.update { it.copy(showInfoDialog = true, infoDialogMessage = message) }
    }

    /**
     * Hides the information dialog.
     */
    fun onDismissInfoDialog() {
        _state.update { it.copy(showInfoDialog = false, infoDialogMessage = "") }
    }

    /**
     * Updates the IPConfiguration singleton based on the selected environment.
     */
    private fun updateIpificationConfiguration() {

        IPLogs.getInstance().LOG = ""

        val config = IPConfiguration.getInstance()
        config.ENV = state.value.selectedEnvironment
        
        // Set BASE_URL from the selected auth server
        val authServerUrl = ConfigManager.getSelectedServerUrl()
        if (authServerUrl != null) {
            config.BASE_URL = authServerUrl.replace("/auth", "")
            Log.d("MainViewModel", "Set BASE_URL from selected server (${ConfigManager.selectedServerId}): ${config.BASE_URL}")
        } else {
            config.BASE_URL = null
            Log.w("MainViewModel", "No selected auth server, using SDK default")
        }

        config.bindAppToCellularNetwork = false
    }

}
