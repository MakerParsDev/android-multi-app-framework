package com.parsfilo.contentapp.feature.ads

import com.google.android.ump.FormError

/**
 * Low-cardinality error category for Analytics / release telemetry.
 * Never expose the raw UMP error message as a dimension.
 */
internal fun classifyUmpConsentError(
    operation: String,
    errorCode: Int,
): String {
    val stage =
        when (operation) {
            "cold_start_form",
            "cold_start_info_update",
            "runtime_refresh",
            "privacy_options_form",
            "debug_menu_form",
            "debug_menu_info_update",
            -> operation
            else -> "other"
        }
    val category =
        when (errorCode) {
            FormError.ErrorCode.INTERNAL_ERROR -> "internal"
            FormError.ErrorCode.INTERNET_ERROR -> "network"
            FormError.ErrorCode.INVALID_OPERATION -> "invalid_operation"
            FormError.ErrorCode.TIME_OUT -> "timeout"
            else -> "other"
        }
    return "${stage}_$category"
}
