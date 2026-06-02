package com.openorder.pairing

import com.openorder.core.models.PairingRequest

data class PendingPairingRequest(
    val requestId: String,
    val request: PairingRequest,
    val receivedAt: Long,
)
