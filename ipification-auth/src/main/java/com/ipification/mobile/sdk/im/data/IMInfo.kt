package com.ipification.mobile.sdk.im.data

import android.os.Parcel
import android.os.Parcelable

/** IM provider link shown by the SDK provider-selection UI. */
class IMInfo(
    /** Provider identifier returned by the SDK. */
    val brand: String,

    /** Primary Android package name used to detect the provider app. */
    var packageName: String,

    /** Optional secondary package name used by providers with multiple Android packages. */
    val packageName2: String?,

    /** Provider deep link or redirect URL. */
    val message: String,

    /** True when a matching provider app is installed on the device. */
    var isInstalled: Boolean
) : Parcelable {

    /** Returns the user-facing provider name used by UI binding logic. */
    fun getBrandName(): String {
        return when (brand) {
            BRAND_WHATSAPP -> WHATSAPP_DISPLAY_NAME
            else -> brand
        }
    }

    private constructor(parcel: Parcel) : this(
        brand = parcel.readString().orEmpty(),
        packageName = parcel.readString().orEmpty(),
        packageName2 = parcel.readString(),
        message = parcel.readString().orEmpty(),
        isInstalled = parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(brand)
        parcel.writeString(packageName)
        parcel.writeString(packageName2)
        parcel.writeString(message)
        parcel.writeByte(if (isInstalled) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<IMInfo> {
        /** WhatsApp provider identifier. */
        const val BRAND_WHATSAPP = "wa"

        /** Telegram provider identifier. */
        const val BRAND_TELEGRAM = "telegram"

        /** Viber provider identifier. */
        const val BRAND_VIBER = "viber"

        private const val WHATSAPP_DISPLAY_NAME = "whatsapp"

        override fun createFromParcel(parcel: Parcel): IMInfo {
            return IMInfo(parcel)
        }

        override fun newArray(size: Int): Array<IMInfo?> {
            return arrayOfNulls(size)
        }
    }
}
