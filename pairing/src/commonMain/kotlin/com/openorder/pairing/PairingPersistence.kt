package com.openorder.pairing

import com.openorder.core.security.TokenState

data class PairedDeviceRecord(
    val deviceId: String,
    val deviceName: String,
    val role: String,
    val blocked: Boolean,
)

interface PairingPersistence {
    fun loadBlockedDeviceIds(): Set<String>
    fun savePairedDevice(device: PairedDeviceRecord)
    fun saveActiveToken(token: TokenState)
    fun updateDeviceRole(deviceId: String, role: String)
    fun updateDeviceBlocked(deviceId: String, blocked: Boolean)
    fun revokeDeviceTokens(deviceId: String): Boolean
}

object NoOpPairingPersistence : PairingPersistence {
    override fun loadBlockedDeviceIds(): Set<String> = emptySet()
    override fun savePairedDevice(device: PairedDeviceRecord) = Unit
    override fun saveActiveToken(token: TokenState) = Unit
    override fun updateDeviceRole(deviceId: String, role: String) = Unit
    override fun updateDeviceBlocked(deviceId: String, blocked: Boolean) = Unit
    override fun revokeDeviceTokens(deviceId: String): Boolean = false
}
