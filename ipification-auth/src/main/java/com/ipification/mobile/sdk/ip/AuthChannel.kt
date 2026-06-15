package com.ipification.mobile.sdk.ip

import com.ipification.mobile.sdk.ip.IPConfiguration
/**
 * Authentication channels supported by the SDK.
 *
 * The order configured in [IPConfiguration.AUTH_CHANNELS] controls the fallback sequence.
 */
enum class AuthChannel {
    /** TS.43 credential-based authentication. */
    TS43,

    /** IPification network-based authentication. */
    IP,

    /** SMS one-time-password authentication. */
    SMS
}
