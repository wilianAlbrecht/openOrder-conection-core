package com.openorder.core.models

import kotlinx.serialization.Serializable

@Serializable
data class SocketEvent(
    val type: String,
    val payload: String,
    val timestamp: Long,
)

object SocketEventTypes {
    const val ORDER_CREATED = "ORDER_CREATED"
    const val ORDER_UPDATED = "ORDER_UPDATED"
    const val TABLE_UPDATED = "TABLE_UPDATED"
    const val DEVICE_DISCONNECTED = "DEVICE_DISCONNECTED"
    const val ROLE_CHANGED = "ROLE_CHANGED"
    const val SESSION_CONNECTED = "SESSION_CONNECTED"
}

fun socketEvent(
    type: String,
    payload: String = "",
    timestamp: Long = currentTimeMillis(),
): SocketEvent {
    return SocketEvent(
        type = type,
        payload = payload,
        timestamp = timestamp,
    )
}

private fun currentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
