package com.ipification.demoapp.manager

import com.ipification.demoapp.BuildConfig
import com.ipification.demoapp.model.config.ApiConfigResponse
import com.ipification.demoapp.model.config.ClientConfig

object ConfigManager {
    
    private var currentConfig: ApiConfigResponse? = null
    var selectedServerId: String? = null
        private set
    
    fun setConfig(config: ApiConfigResponse) {
        currentConfig = config
    }
    
    fun getConfig(): ApiConfigResponse? = currentConfig
    
    fun setSelectedServer(serverId: String) {
        selectedServerId = serverId
    }
    
    fun getSelectedServerUrl(): String? {
        val id = selectedServerId ?: return null
        return currentConfig?.authServers?.find { it.id == id }?.url
    }

    fun getBackendUrl(): String {
        return BuildConfig.BASE_URL
    }
    
    fun getClientByUserFlow(userFlow: String): ClientConfig? {
        return currentConfig?.clients?.find { it.userFlow == userFlow }
    }
    
    fun getAllClients(): List<ClientConfig> {
        return currentConfig?.clients ?: emptyList()
    }
    
    fun getAuthServerUrl(serverId: String): String? {
        return currentConfig?.authServers?.find { it.id == serverId }?.url
    }
    
    fun getRealm(): String {
        return currentConfig?.realm ?: "ipification"
    }
    
    fun isConfigLoaded(): Boolean = currentConfig != null
    
    fun clear() {
        currentConfig = null
        selectedServerId = null
    }
}
