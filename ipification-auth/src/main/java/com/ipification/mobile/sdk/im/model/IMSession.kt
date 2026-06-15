package com.ipification.mobile.sdk.im.model

import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.im.data.IMInfo

/** IM session returned by the authorization API before manual provider verification. */
class IMSession(
    /** Viber provider link returned for this session. */
    var viberLink: String? = null,

    /** Telegram provider link returned for this session. */
    var telegramLink: String? = null,

    /** WhatsApp provider link returned for this session. */
    var waLink: String? = null,

    /** Server-side IM session identifier. */
    var sessionId: String? = null,

    /** Endpoint used to complete the IM session after provider verification. */
    var completeSessionUrl: String? = null
) {

    /** Converts available provider links into the ordered provider list shown by the SDK UI. */
    fun convertToIMList(): List<IMInfo> {
        val configuration = IPConfiguration.getInstance()
        return buildList {
            waLink?.takeIf(String::isNotBlank)?.let { link ->
                add(
                    IMInfo(
                        brand = IMInfo.BRAND_WHATSAPP,
                        packageName = configuration.whatsappPackageName,
                        packageName2 = null,
                        message = link,
                        isInstalled = false
                    )
                )
            }

            telegramLink?.takeIf(String::isNotBlank)?.let { link ->
                add(
                    IMInfo(
                        brand = IMInfo.BRAND_TELEGRAM,
                        packageName = configuration.telegramPackageName,
                        packageName2 = configuration.telegramWebPackageName,
                        message = link,
                        isInstalled = false
                    )
                )
            }

            viberLink?.takeIf(String::isNotBlank)?.let { link ->
                add(
                    IMInfo(
                        brand = IMInfo.BRAND_VIBER,
                        packageName = configuration.viberPackageName,
                        packageName2 = null,
                        message = link,
                        isInstalled = false
                    )
                )
            }
        }
    }
}
