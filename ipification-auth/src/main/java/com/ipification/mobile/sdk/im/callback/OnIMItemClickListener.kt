package com.ipification.mobile.sdk.im.callback

import com.ipification.mobile.sdk.im.data.IMInfo

/** Receives selected-provider events from the SDK IM provider list. */
internal interface OnIMItemClickListener {

    /** Called when the user selects an IM provider. */
    fun onItemClick(item: IMInfo)
}
