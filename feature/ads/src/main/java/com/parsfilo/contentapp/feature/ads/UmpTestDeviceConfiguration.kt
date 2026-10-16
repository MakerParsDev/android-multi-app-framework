package com.parsfilo.contentapp.feature.ads

private val umpTestDeviceHashPattern = Regex("[A-Fa-f0-9]{32}")

/** Only opt-in, locally supplied 32-hex hashed IDs may enter a debug UMP request. */
internal fun registeredUmpTestDeviceHash(
    debugBuild: Boolean,
    configuredHash: String,
): String? =
    configuredHash.takeIf {
        debugBuild && umpTestDeviceHashPattern.matches(it)
    }
