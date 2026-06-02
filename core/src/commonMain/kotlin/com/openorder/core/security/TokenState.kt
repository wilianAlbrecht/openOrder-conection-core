package com.openorder.core.security

data class TokenState(
    val token: String,
    val sessionKey: String,
    val deviceId: String,
    val role: String,
    val createdAt: Long,
    val expiresAt: Long,
    val active: Boolean,
)
