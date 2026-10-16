package com.parsfilo.contentapp.feature.ads

import com.google.android.ump.FormError
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UmpConsentErrorCategoryTest {
    @Test
    fun classifiesBoundedSdkFailuresWithOperation() {
        assertThat(classifyUmpConsentError("cold_start_form", FormError.ErrorCode.INTERNET_ERROR))
            .isEqualTo("cold_start_form_network")
        assertThat(classifyUmpConsentError("cold_start_info_update", FormError.ErrorCode.INTERNAL_ERROR))
            .isEqualTo("cold_start_info_update_internal")
        assertThat(classifyUmpConsentError("runtime_refresh", FormError.ErrorCode.TIME_OUT))
            .isEqualTo("runtime_refresh_timeout")
        assertThat(classifyUmpConsentError("privacy_options_form", FormError.ErrorCode.INVALID_OPERATION))
            .isEqualTo("privacy_options_form_invalid_operation")
    }

    @Test
    fun unknownOperationAndCodeCannotIntroduceUnboundedValues() {
        assertThat(classifyUmpConsentError("private-user-content", 99999)).isEqualTo("other_other")
        assertThat(classifyUmpConsentError("debug_menu_info_update", 99999))
            .isEqualTo("debug_menu_info_update_other")
    }
}
