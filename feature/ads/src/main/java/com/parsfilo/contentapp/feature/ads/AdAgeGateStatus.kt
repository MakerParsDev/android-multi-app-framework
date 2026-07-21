package com.parsfilo.contentapp.feature.ads

enum class AdAgeGateStatus(val storageValue: String) {
    UNKNOWN("UNKNOWN"),
    UNDER_13("UNDER_13"),
    AGE_13_TO_15("AGE_13_TO_15"),
    AGE_16_OR_OVER("AGE_16_OR_OVER"),
    ;

    companion object {
        fun fromStorage(value: String?): AdAgeGateStatus =
            when (value) {
                "UNDER_16" -> AGE_13_TO_15
                else -> entries.firstOrNull { it.storageValue == value } ?: UNKNOWN
            }
    }
}
