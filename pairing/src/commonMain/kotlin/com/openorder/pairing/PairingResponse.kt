package com.openorder.pairing

import kotlinx.serialization.Serializable

@Serializable
enum class PairingStatus {
    APPROVED,
    REJECTED,
    EXPIRED,
    INVALID,
}

@Serializable
data class PairingResponse(
    val status: PairingStatus,
    val encryptedToken: String? = null,
    val message: String,
)
