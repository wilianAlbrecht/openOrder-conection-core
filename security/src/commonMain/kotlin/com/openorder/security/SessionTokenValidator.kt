package com.openorder.security

import com.openorder.core.security.TokenState

class SessionTokenValidator {
    fun isValid(token: String, deviceId: String, tokens: Collection<TokenState>, now: Long): Boolean {
        return tokens.any {
            it.token == token &&
                it.deviceId == deviceId &&
                it.active &&
                it.expiresAt > now
        }
    }
}
