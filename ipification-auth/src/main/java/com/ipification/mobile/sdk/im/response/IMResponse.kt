package com.ipification.mobile.sdk.im.response

import com.ipification.mobile.sdk.im.model.IMSession

/** Response containing the IM session that the app should complete manually. */
data class IMResponse(
    /** IM session details and provider links. */
    val sessionInfo: IMSession
)
