package com.ipification.demoapp.model.config

import com.google.gson.annotations.SerializedName

data class ApiConfigResponse(
    @SerializedName("auth_servers")
    val authServers: List<AuthServer>? = null,
    @SerializedName("realm")
    val realm: String? = null,
    @SerializedName("clients")
    val clients: List<ClientConfig>? = null,
    @SerializedName("app_config")
    val appConfig: AppConfiguration? = null
)

data class AuthServer(
    @SerializedName("id")
    val id: String,
    @SerializedName("url")
    val url: String
)

data class ClientConfig(
    @SerializedName("user_flow")
    val userFlow: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("scope")
    val scope: String,
    @SerializedName("client_id")
    val clientId: String,
    @SerializedName("redirect_uri")
    val redirectUri: String,
    @SerializedName("channel")
    val channel: String? = null
)

data class AppConfiguration(
    @SerializedName("config_version")
    val configVersion: String,
    @SerializedName("default_environment")
    val defaultEnvironment: String,
    @SerializedName("branding")
    val branding: Branding,
    @SerializedName("main_screen")
    val mainScreen: MainScreenConfig,
    @SerializedName("pnv_screen")
    val pnvScreen: PnvScreenConfig,
    @SerializedName("global_locale")
    val globalLocale: GlobalLocale
)

data class Branding(
    @SerializedName("app_name")
    val appName: String,
    @SerializedName("show_logo")
    val showLogo: Boolean,
    @SerializedName("logo_url")
    val logoUrl: String,
    @SerializedName("primary_color")
    val primaryColor: String,
    @SerializedName("secondary_color")
    val secondaryColor: String
)

data class MainScreenConfig(
    @SerializedName("locale")
    val locale: MainScreenLocale
)

data class MainScreenLocale(
    @SerializedName("title")
    val title: String,
    @SerializedName("footer")
    val footer: Footer
)

data class PnvScreenConfig(
    @SerializedName("locale")
    val locale: PnvScreenLocale
)

data class PnvScreenLocale(
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("country_code_label")
    val countryCodeLabel: String,
    @SerializedName("phone_number_label")
    val phoneNumberLabel: String,
    @SerializedName("footer")
    val footer: Footer,
    @SerializedName("errors")
    val errors: PnvErrors
)

data class PnvErrors(
    @SerializedName("phone_required")
    val phoneRequired: String,
    @SerializedName("phone_invalid")
    val phoneInvalid: String,
    @SerializedName("country_code_required")
    val countryCodeRequired: String
)

data class GlobalLocale(
    @SerializedName("app_name")
    val appName: String,
    @SerializedName("common")
    val common: CommonLocale
)

data class CommonLocale(
    @SerializedName("ok")
    val ok: String,
    @SerializedName("cancel")
    val cancel: String,
    @SerializedName("back")
    val back: String,
    @SerializedName("info_title")
    val infoTitle: String
)

data class Footer(
    @SerializedName("privacy_policy")
    val privacyPolicy: String,
    @SerializedName("privacy_policy_url")
    val privacyPolicyUrl: String
)
