package com.ipification.mobile.sdk.im

import android.view.View

/** Text shown by the SDK-hosted IM verification screen. */
data class IMLocale(
    /** Main title shown above the provider list. */
    var mainTitle: String = "Verify Your Phone Number",

    /** Description shown above the provider list. */
    var description: String =
        "Please tap on your preferred messaging app and follow the instruction appearing on the screen",

    /** WhatsApp button text. */
    var whatsappText: String = "Quick Login via Whatsapp",

    /** Telegram button text. */
    var telegramText: String = "Quick Login via Telegram",

    /** Viber button text. */
    var viberText: String = "Quick Login via Viber",

    /** Toolbar title, or null to leave it empty. */
    var toolbarTitle: String? = "IPification Verification",

    /** Toolbar visibility, for example [View.VISIBLE] or [View.GONE]. */
    var toolbarVisibility: Int = View.VISIBLE
) {
    /** Generic error dialog title. */
    var errorTitle: String = "Error"

    /** Generic error dialog button text. */
    var errorButtonText: String = "Ok"

    /** Loading text formatted with the selected provider name. */
    var loadingText: String = "Booting up your %s ..."

    /** Text shown while checking the IM completion result. */
    var checkingResult: String = "Authorizing..."

    /** Message shown when an IM session does not exist or has expired. */
    var sessionNotFoundMessage: String = "The session has expired or could not be found."

    /** Message shown when an IM session has already completed. */
    var sessionAlreadyCompletedMessage: String = "The session has already expired."

    /** Legacy property retained for source compatibility. */
    @Deprecated("Use errorTitle.", ReplaceWith("errorTitle"))
    var error_title: String
        get() = errorTitle
        set(value) {
            errorTitle = value
        }

    /** Legacy property retained for source compatibility. */
    @Deprecated("Use errorButtonText.", ReplaceWith("errorButtonText"))
    var error_button_text: String
        get() = errorButtonText
        set(value) {
            errorButtonText = value
        }

    /** Legacy property retained for source compatibility. */
    @Deprecated("Use sessionNotFoundMessage.", ReplaceWith("sessionNotFoundMessage"))
    var error_session_not_found_message: String
        get() = sessionNotFoundMessage
        set(value) {
            sessionNotFoundMessage = value
        }

    /** Legacy property retained for source compatibility. */
    @Deprecated(
        "Use sessionAlreadyCompletedMessage.",
        ReplaceWith("sessionAlreadyCompletedMessage")
    )
    var error_session_already_completed_message: String
        get() = sessionAlreadyCompletedMessage
        set(value) {
            sessionAlreadyCompletedMessage = value
        }
}
