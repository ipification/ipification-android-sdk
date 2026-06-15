package com.ipification.mobile.sdk.im.internal.response

import com.ipification.mobile.sdk.im.model.IMSession
import com.ipification.mobile.sdk.ip.response.AuthApiResponse
import com.ipification.mobile.sdk.ip.utils.IPConstant

/** Returns true when an authorization response contains an IM session identifier. */
internal val AuthApiResponse.isImResponse: Boolean
    get() = header(IM_SESSION_ID) != null

/** Builds the IM session represented by authorization response headers. */
internal fun AuthApiResponse.toImSession(): IMSession? {
    val sessionId = header(IM_SESSION_ID) ?: return null
    val session = IMSession(
        sessionId = sessionId,
        waLink = header(IM_WA_LINK),
        telegramLink = header(IM_TELEGRAM_LINK),
        viberLink = header(IM_VIBER_LINK),
        completeSessionUrl = header(IMBOX_ENDPOINT)
    )

    if (session.waLink == null && session.telegramLink == null && session.viberLink == null) {
        session.assignFallbackLink(header(LOCATION_HEADER))
    }
    return session
}

/** Assigns a single fallback redirect link to its matching IM provider. */
private fun IMSession.assignFallbackLink(link: String?) {
    when {
        link?.contains(WHATSAPP_LINK_HINT) == true -> waLink = link
        link?.contains(VIBER_LINK_HINT) == true -> viberLink = link
        link?.contains(TELEGRAM_LINK_HINT) == true -> telegramLink = link
    }
}

private val IM_SESSION_ID = IPConstant.IM_SESSION_ID
private val IM_WA_LINK = IPConstant.IM_WA_LINK
private val IM_TELEGRAM_LINK = IPConstant.IM_TELEGRAM_LINK
private val IM_VIBER_LINK = IPConstant.IM_VIBER_LINK
private val IMBOX_ENDPOINT = IPConstant.IMBOX_ENDPOINT
private const val LOCATION_HEADER = "location"
private const val WHATSAPP_LINK_HINT = "wa"
private const val VIBER_LINK_HINT = "viber"
private const val TELEGRAM_LINK_HINT = "telegram"
