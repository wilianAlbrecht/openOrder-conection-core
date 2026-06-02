package com.openorder.core.models

import kotlinx.serialization.Serializable

@Serializable
data class PairingRequest(
    val deviceId: String,
    val deviceName: String,
    val timestamp: Long,
    val nonce: String,
)
