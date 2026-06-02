package com.openorder.websocket

import io.ktor.server.websocket.DefaultWebSocketServerSession

data class ConnectedClient(
    val deviceId: String,
    val role: String,
    val session: DefaultWebSocketServerSession,
    val connectedAt: Long,
)
