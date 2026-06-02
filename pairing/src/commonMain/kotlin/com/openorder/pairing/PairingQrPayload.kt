package com.openorder.pairing

import kotlinx.serialization.Serializable

@Serializable
data class PairingQrPayload(
    val deviceId: String,
    val sessionKey: String,
    val nonce: String,
    val timestamp: Long,
)
