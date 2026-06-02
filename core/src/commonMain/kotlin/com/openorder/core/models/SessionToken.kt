package com.openorder.core.models

import kotlinx.serialization.Serializable

@Serializable
data class SessionToken(
    val token: String,
    val deviceId: String,
    val role: String,
    val expiresAt: Long,
)
