package com.ipification.mobile.sdk.ts43.helper

/**
 * Helper class to extract carrier hint (MCC+MNC) from phone number.
 * 
 * This analyzes the phone number prefix to determine:
 * - MCC (Mobile Country Code) from country calling code
 * - MNC (Mobile Network Code) from carrier-specific number prefix
 * 
 * Example usage:
 * ```kotlin
 * val carrierHint = CarrierHintHelper.getCarrierHint("84932383421")
 * // Returns "45201" (Vietnam Mobifone)
 * ```
 */
object CarrierHintHelper {

    /**
     * Get carrier hint (MCC+MNC) from phone number.
     * 
     * @param phoneNumber Phone number with country code
     * @return MCC+MNC string if found, or null if not recognized.
     */
    @JvmStatic
    fun getCarrierHint(phoneNumber: String?): String? {
        val cleanNumber = normalizePhoneNumber(phoneNumber) ?: return null
        
        // Try matching country codes from longest to shortest (1-3 digits)
        for (countryCodeLength in 3 downTo 1) {
            if (cleanNumber.length > countryCodeLength + 2) {
                val countryCode = cleanNumber.substring(0, countryCodeLength)
                val countryInfo = COUNTRY_CARRIER_MAP[countryCode]
                
                if (countryInfo != null) {
                    val mcc = countryInfo.mcc
                    val numberAfterCountryCode = cleanNumber.substring(countryCodeLength)
                    
                    // Try to find carrier by number prefix (longest prefix first)
                    val sortedPrefixes = countryInfo.carrierPrefixes.keys.sortedByDescending { it.length }
                    for (prefix in sortedPrefixes) {
                        if (numberAfterCountryCode.startsWith(prefix)) {
                            val mnc = countryInfo.carrierPrefixes[prefix] ?: continue
                            val carrierHint = "$mcc$mnc"
                            return carrierHint
                        }
                    }
                    
                    // If no carrier prefix matched, use default MNC for the country
                    if (countryInfo.defaultMnc.isNotBlank()) {
                        val carrierHint = "$mcc${countryInfo.defaultMnc}"
                        return carrierHint
                    }
                    
                    // Return just MCC if no MNC info available
                    return mcc
                }
            }
        }
        
        return null
    }

    /**
     * Get MCC (Mobile Country Code) only from phone number.
     * 
     * @param phoneNumber Phone number with country code
     * @return MCC string if found, or null if not recognized.
     */
    @JvmStatic
    fun getMcc(phoneNumber: String?): String? {
        val cleanNumber = normalizePhoneNumber(phoneNumber) ?: return null
        
        for (countryCodeLength in 3 downTo 1) {
            if (cleanNumber.length > countryCodeLength) {
                val countryCode = cleanNumber.substring(0, countryCodeLength)
                val countryInfo = COUNTRY_CARRIER_MAP[countryCode]
                if (countryInfo != null) {
                    return countryInfo.mcc
                }
            }
        }
        return null
    }

    /**
     * Get country calling code from phone number.
     * 
     * @param phoneNumber Phone number with country code
     * @return Country calling code if found, or null if not recognized.
     */
    @JvmStatic
    fun getCountryCode(phoneNumber: String?): String? {
        val cleanNumber = normalizePhoneNumber(phoneNumber) ?: return null
        
        for (countryCodeLength in 3 downTo 1) {
            if (cleanNumber.length > countryCodeLength) {
                val countryCode = cleanNumber.substring(0, countryCodeLength)
                if (COUNTRY_CARRIER_MAP.containsKey(countryCode)) {
                    return countryCode
                }
            }
        }
        return null
    }

    /** Removes formatting characters while preserving only phone-number digits. */
    private fun normalizePhoneNumber(phoneNumber: String?): String? {
        val cleanNumber = phoneNumber
            ?.filter(Char::isDigit)
            ?.takeIf(String::isNotBlank)
        return cleanNumber
    }

    /**
     * Data class for country carrier information.
     */
    private data class CountryCarrierInfo(
        val mcc: String,
        val defaultMnc: String = "",
        val carrierPrefixes: Map<String, String> = emptyMap()
    )

    /**
     * Mapping of country calling codes to MCC and carrier prefixes.
     * Carrier prefixes map number prefix (after country code) to MNC.
     */
    private val COUNTRY_CARRIER_MAP = mapOf(
        // Vietnam (+84) - MCC: 452
        "84" to CountryCarrierInfo(
            mcc = "452",
            defaultMnc = "04",
            carrierPrefixes = mapOf(
                // Viettel - MNC: 04
                "86" to "04", "96" to "04", "97" to "04", "98" to "04",
                "32" to "04", "33" to "04", "34" to "04", "35" to "04",
                "36" to "04", "37" to "04", "38" to "04", "39" to "04",
                // Mobifone - MNC: 01
                "90" to "01", "93" to "01", "89" to "01",
                "70" to "01", "76" to "01", "77" to "01", "78" to "01", "79" to "01",
                // Vinaphone - MNC: 02
                "91" to "02", "94" to "02", "88" to "02",
                "81" to "02", "82" to "02", "83" to "02", "84" to "02", "85" to "02",
                // Vietnamobile - MNC: 05
                "92" to "05", "56" to "05", "58" to "05",
                // Gmobile - MNC: 07
                "99" to "07", "59" to "07"
            )
        ),
        
        // Thailand (+66) - MCC: 520
        "66" to CountryCarrierInfo(
            mcc = "520",
            defaultMnc = "01",
            carrierPrefixes = mapOf(
                // AIS - MNC: 01
                "81" to "01", "82" to "01", "83" to "01", "84" to "01", "85" to "01",
                // DTAC - MNC: 05
                "61" to "05", "62" to "05", "63" to "05",
                // TrueMove - MNC: 04
                "91" to "04", "92" to "04", "93" to "04", "94" to "04", "95" to "04"
            )
        ),
        
        // Indonesia (+62) - MCC: 510
        "62" to CountryCarrierInfo(
            mcc = "510",
            defaultMnc = "10",
            carrierPrefixes = mapOf(
                // Telkomsel - MNC: 10
                "811" to "10", "812" to "10", "813" to "10", "821" to "10", "822" to "10", "852" to "10", "853" to "10",
                // Indosat - MNC: 01
                "814" to "01", "815" to "01", "816" to "01", "855" to "01", "856" to "01", "857" to "01", "858" to "01",
                // XL - MNC: 11
                "817" to "11", "818" to "11", "819" to "11", "859" to "11", "877" to "11", "878" to "11"
            )
        ),
        
        // Malaysia (+60) - MCC: 502
        "60" to CountryCarrierInfo(
            mcc = "502",
            defaultMnc = "12",
            carrierPrefixes = mapOf(
                // Maxis - MNC: 12
                "12" to "12", "17" to "12",
                // Celcom - MNC: 13
                "13" to "13", "14" to "13", "19" to "13",
                // Digi - MNC: 16
                "16" to "16", "10" to "16", "11" to "16"
            )
        ),
        
        // Philippines (+63) - MCC: 515
        "63" to CountryCarrierInfo(
            mcc = "515",
            defaultMnc = "02",
            carrierPrefixes = mapOf(
                // Globe - MNC: 02
                "917" to "02", "927" to "02", "905" to "02", "906" to "02", "915" to "02", "916" to "02", 
                "926" to "02", "935" to "02", "936" to "02", "937" to "02", "956" to "02", "965" to "02", 
                "966" to "02", "967" to "02", "975" to "02", "976" to "02", "977" to "02", "978" to "02", 
                "979" to "02", "995" to "02", "996" to "02", "997" to "02",
                // Smart - MNC: 03
                "908" to "03", "918" to "03", "919" to "03", "920" to "03", "921" to "03", "928" to "03", 
                "929" to "03", "930" to "03", "938" to "03", "939" to "03", "940" to "03", "946" to "03", 
                "947" to "03", "948" to "03", "949" to "03", "950" to "03", "951" to "03", "961" to "03", 
                "968" to "03", "969" to "03", "970" to "03", "981" to "03", "989" to "03", "992" to "03", 
                "998" to "03", "999" to "03"
            )
        ),
        // Cambodia (+855) - MCC: 456
        "855" to CountryCarrierInfo(
            mcc = "456",
            defaultMnc = "06", // Smart is often the primary target for digital services
            carrierPrefixes = mapOf(
                // Smart (Hello) - MNC: 06
                "10" to "06", "15" to "06", "16" to "06", "69" to "06", "70" to "06", 
                "81" to "06", "86" to "06", "87" to "06", "93" to "06", "96" to "06", "98" to "06",
                // Cellcard (CamShin) - MNC: 01
                "11" to "01", "12" to "01", "14" to "01", "17" to "01", "61" to "01", 
                "76" to "01", "77" to "01", "78" to "01", "79" to "01", "85" to "01", "89" to "01", "92" to "01", "95" to "01", "99" to "01",
                // Metfone (Viettel Cambodia) - MNC: 08
                "31" to "08", "38" to "08", "60" to "08", "66" to "08", "67" to "08", 
                "68" to "08", "71" to "08", "88" to "08", "90" to "08", "97" to "08"
            )
        ),
        // Singapore (+65) - MCC: 525
        "65" to CountryCarrierInfo(
            mcc = "525",
            defaultMnc = "01", // Singtel
            carrierPrefixes = mapOf(
                // Singtel - MNC: 01
                "81" to "01", "82" to "01", "83" to "01", "84" to "01", "85" to "01", 
                "90" to "01", "91" to "01", "92" to "01", "95" to "01", "98" to "01",
                // M1 - MNC: 03
                "86" to "03", "87" to "03", "88" to "03", "89" to "03", 
                "93" to "03", "94" to "03", "98" to "03",
                // StarHub - MNC: 05
                "81" to "05", "82" to "05", "83" to "05", "84" to "05", 
                "96" to "05", "97" to "05", "98" to "05",
                // Simba (formerly TPG) - MNC: 10
                "80" to "10"
            )
        ),
        
        // India (+91) - MCC: 404
        "91" to CountryCarrierInfo(
            mcc = "404",
            defaultMnc = "10", // Airtel (Delhi/Common)
            carrierPrefixes = mapOf(
                // Reliance Jio - MNC: 405/840 (Using 405840 or just 840)
                // Jio uses prefixes across 6, 7, 8, 9
                "700" to "840", "800" to "840", "900" to "840", "600" to "840",
                
                // Airtel - MNC: 10 (Delhi), 02 (Punjab), etc.
                "981" to "10", "991" to "10", "911" to "10", "813" to "10",
                
                // Vi (Vodafone Idea) - MNC: 11 (Vodafone), 04 (Idea)
                "982" to "11", "983" to "11", "770" to "11", "880" to "11",
                
                // BSNL - MNC: 34
                "94" to "34", "95" to "34"
            )
        ),
        
        // China (+86) - MCC: 460
        "86" to CountryCarrierInfo(
            mcc = "460",
            defaultMnc = "00",
            carrierPrefixes = mapOf(
                // China Mobile - MNC: 00
                "134" to "00", "135" to "00", "136" to "00", "137" to "00", "138" to "00", "139" to "00", 
                "147" to "00", "150" to "00", "151" to "00", "152" to "00", "157" to "00", "158" to "00", 
                "159" to "00", "178" to "00", "182" to "00", "183" to "00", "184" to "00", "187" to "00", "188" to "00",
                // China Unicom - MNC: 01
                "130" to "01", "131" to "01", "132" to "01", "145" to "01", "155" to "01", "156" to "01", 
                "166" to "01", "175" to "01", "176" to "01", "185" to "01", "186" to "01",
                // China Telecom - MNC: 03
                "133" to "03", "149" to "03", "153" to "03", "173" to "03", "177" to "03", "180" to "03", 
                "181" to "03", "189" to "03", "199" to "03"
            )
        ),
        
        // Japan (+81) - MCC: 440
        "81" to CountryCarrierInfo(mcc = "440", defaultMnc = "10"),
        
        // South Korea (+82) - MCC: 450
        "82" to CountryCarrierInfo(mcc = "450", defaultMnc = "05"),
        
        // Taiwan (+886) - MCC: 466
        "886" to CountryCarrierInfo(mcc = "466", defaultMnc = "92"),
        
        // Hong Kong (+852) - MCC: 454
        "852" to CountryCarrierInfo(
            mcc = "454",
            defaultMnc = "00", // CSL
            carrierPrefixes = mapOf(
                // CSL / 1O1O - MNC: 00
                "51" to "00", "52" to "00", "53" to "00", "60" to "00", "61" to "00", "62" to "00", "90" to "00", "91" to "00", "92" to "00",
                
                // 3 Hong Kong (Hutchison) - MNC: 03
                "57" to "03", "66" to "03", "68" to "03", "69" to "03", "93" to "03", "95" to "03",
                
                // SmarTone - MNC: 06
                "54" to "06", "55" to "06", "56" to "06", "63" to "06", "96" to "06", "97" to "06", "98" to "06",
                
                // China Mobile HK (CMHK) - MNC: 12
                "59" to "12", "65" to "12", "67" to "12", "94" to "12",
                
                // HKT / PCCW - MNC: 19
                "51" to "19", "52" to "19", "64" to "19", "92" to "19"
            )
        ),
        
        // UK (+44) - MCC: 234
        "44" to CountryCarrierInfo(mcc = "234", defaultMnc = "10"),
        
        // Germany (+49) - MCC: 262
        "49" to CountryCarrierInfo(mcc = "262", defaultMnc = "01"),
        
        // France (+33) - MCC: 208
        "33" to CountryCarrierInfo(mcc = "208", defaultMnc = "01"),
        
        // Italy (+39) - MCC: 222
        "39" to CountryCarrierInfo(mcc = "222", defaultMnc = "01"),
        
        // Spain (+34) - MCC: 214
        "34" to CountryCarrierInfo(mcc = "214", defaultMnc = "01"),
        
        // Serbia (+381) - MCC: 220
        "381" to CountryCarrierInfo(
            mcc = "220",
            defaultMnc = "03", // mts (Telekom Srbija) is the largest
            carrierPrefixes = mapOf(
                // Telekom Srbija (mts) - MNC: 03
                "64" to "03", "65" to "03", "66" to "03",
                // Yettel (formerly Telenor) - MNC: 01
                "62" to "01", "63" to "01", "69" to "01",
                // A1 (formerly VIP Mobile) - MNC: 05
                "60" to "05", "61" to "05", "68" to "05",
                // Globaltel (Virtual/MVNO, now owned by mts) - MNC: 11
                "677" to "11"
            )
        ),
        
        // USA/Canada (+1) - MCC: 310
        "1" to CountryCarrierInfo(mcc = "310", defaultMnc = "260"),
        
        // Australia (+61) - MCC: 505
        "61" to CountryCarrierInfo(mcc = "505", defaultMnc = "01"),
        
        // UAE (+971) - MCC: 424
        "971" to CountryCarrierInfo(mcc = "424", defaultMnc = "02"),
        
        // Saudi Arabia (+966) - MCC: 420
        "966" to CountryCarrierInfo(mcc = "420", defaultMnc = "01")
    )
}
