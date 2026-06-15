package com.ipification.mobile.sdk.ip.utils

import androidx.annotation.Keep

class IPLogs() {

    var LOG = ""

    private object Holder {
        val INSTANCE = IPLogs()
    }

    @Keep
    companion object {
        @JvmStatic
        fun getInstance(): IPLogs {
            return Holder.INSTANCE
        }
    }
}