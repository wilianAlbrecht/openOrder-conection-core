package com.openorder.pairing

import com.openorder.core.permissions.Role
import com.openorder.core.security.TokenState

interface PairingManager {
    val pendingRequests: List<PendingPairingRequest>
    val activeTokens: Collection<TokenState>
    val blockedDeviceIds: Set<String>

    suspend fun requestPairing(request: com.openorder.core.models.PairingRequest): PairingResponse
    fun approvePairing(requestId: String, role: Role, verifiedSessionKey: String): Boolean
    fun rejectPairing(requestId: String): Boolean
    fun updateDeviceRole(deviceId: String, role: Role): Boolean
    fun revokeDeviceTokens(deviceId: String): Boolean
    fun blockDevice(deviceId: String): Boolean
    fun unblockDevice(deviceId: String): Boolean
    fun observePendingRequests(listener: (List<PendingPairingRequest>) -> Unit): () -> Unit
}
