package com.openorder.websocket

data class ConnectedClientInfo(
    val deviceId: String,
    val role: String,
    val connectedAt: Long,
)
