package com.ipification.mobile.sdk.ip

/**
 * Backend environment used by IPification services.
 */
enum class IPEnvironment {
    /** Sandbox environment for development and partner testing. */
    SANDBOX,

    /** Production environment for live applications. */
    PRODUCTION,

    /** custom DC endpoints configured through [IPConfiguration.BASE_URL]. */
    CUSTOM_URL
}
