package com.openorder.core.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val ipAddress: String,
)
