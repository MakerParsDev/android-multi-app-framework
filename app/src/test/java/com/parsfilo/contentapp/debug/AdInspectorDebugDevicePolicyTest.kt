package com.parsfilo.contentapp.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdInspectorDebugDevicePolicyTest {
    @Test
    fun allowsOnlyTheVerifiedDeveloperDevice() {
        assertTrue(AdInspectorDebugDevicePolicy.isAllowed("SM-G611F"))
        assertTrue(AdInspectorDebugDevicePolicy.isAllowed(" sm-g611f "))
    }

    @Test
    fun requiresTheExplicitInternalBuildFlagEvenOnTheAllowedDevice() {
        assertFalse(AdInspectorDebugDevicePolicy.isEnabled(false, "SM-G611F"))
        assertTrue(AdInspectorDebugDevicePolicy.isEnabled(true, "SM-G611F"))
        assertFalse(AdInspectorDebugDevicePolicy.isEnabled(true, "SM-A346E"))
    }

    @Test
    fun rejectsOtherDeviceModels() {
        assertFalse(AdInspectorDebugDevicePolicy.isAllowed("SM-A346E"))
        assertFalse(AdInspectorDebugDevicePolicy.isAllowed("SM-G610F"))
        assertFalse(AdInspectorDebugDevicePolicy.isAllowed("Pixel 9"))
        assertFalse(AdInspectorDebugDevicePolicy.isAllowed(""))
    }

    @Test
    fun acceptsOnlyTheExplicitAdInspectorAction() {
        assertTrue(
            AdInspectorDebugDevicePolicy.isLaunchAction(
                AdInspectorDebugActivity.ACTION_OPEN_AD_INSPECTOR,
            ),
        )
        assertFalse(AdInspectorDebugDevicePolicy.isLaunchAction(null))
        assertFalse(AdInspectorDebugDevicePolicy.isLaunchAction("android.intent.action.VIEW"))
    }
}
