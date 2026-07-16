package com.ipification.mobile.sdk.ip.interceptor

/** HTTP header names sent by SDK network interceptors. */
internal object IPHeaders {

    // SDK and device headers

    /** Partner client identifier. */
    const val CLIENT_ID = "IP-client-id"

    /** IPification SDK type and version. */
    const val SDK_VERSION = "ip-sdk-version"

    /** Device platform:`android`. */
    const val DEVICE_TYPE = "device-type"

    /** Device manufacturer and model. */
    const val DEVICE_NAME = "device-name"

    /** Android release version. */
    const val OS_VERSION = "os-version"

    /** Android API level. */
    const val OS_API_LEVEL = "os-sdk"

    // First SIM headers

    /** Mobile country code reported by SIM slot 1. */
    const val SIM_1_MCC = "mcc-1"

    /** Mobile network code reported by SIM slot 1. */
    const val SIM_1_MNC = "mnc-1"

    /** State reported by SIM slot 1. */
    const val SIM_1_STATE = "mnc-1-state"

    /** Signal strength reported by SIM slot 1. */
    const val SIM_1_SIGNAL_STRENGTH = "mnc-1-signal-strength"

    /** Error encountered while reading SIM slot 1. */
    const val SIM_1_ERROR_MESSAGE = "mnc-1-error-msg"

    // Second SIM headers

    /** Mobile country code reported by SIM slot 2. */
    const val SIM_2_MCC = "mcc-2"

    /** Mobile network code reported by SIM slot 2. */
    const val SIM_2_MNC = "mnc-2"

    /** State reported by SIM slot 2. */
    const val SIM_2_STATE = "mnc-2-state"

    /** Signal strength reported by SIM slot 2. */
    const val SIM_2_SIGNAL_STRENGTH = "mnc-2-signal-strength"

    /** Error encountered while reading SIM slot 2. */
    const val SIM_2_ERROR_MESSAGE = "mnc-2-error-msg"

    // Network state headers

    /** Whether the device reports multiple SIM slots. */
    const val DUAL_SIM = "dual-sim-phone"

    /** Whether Wi-Fi is enabled. */
    const val WIFI_ENABLED = "wifi"

    /** Whether the active network is a VPN. */
    const val VPN_ENABLED = "vpn"

    /** Whether the active cellular network is roaming. */
    const val ROAMING = "roaming"

    /** SIM slot currently used for cellular data. Value is `1` or `2`. */
    const val ACTIVE_DATA_SIM = "active-data-session-sim"

    /** MCC and MNC of the most recently active cellular data SIM. */
    const val LAST_ACTIVE_DATA_SIM = "last-active-data-session-sim"

    /** Private IP address assigned to the cellular network. */
    const val CELLULAR_PRIVATE_IP = "private-ip"
}
