package com.openorder.pairing

import com.openorder.core.models.PairingRequest
import com.openorder.core.permissions.Role
import com.openorder.core.security.TokenState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
class InMemoryPairingManager(
    private val pairingPolicy: PairingPolicy = PairingPolicy(),
    private val persistence: PairingPersistence = NoOpPairingPersistence,
    private val tokenTtlMillis: Long = 30 * 60 * 1_000,
    private val approvalTimeoutMillis: Long = 60_000,
    private val maxPendingPairingRequests: Int = 16,
    private val now: () -> Long = { currentTimeMillis() },
    private val tokenGenerator: () -> String = { secureToken() },
) : PairingManager {
    private val lock = Any()
    private val pending = linkedMapOf<String, PendingDecision>()
    private val usedNonces = linkedMapOf<String, Long>()
    private val tokens = linkedMapOf<String, TokenState>()
    private val blockedDevices = persistence.loadBlockedDeviceIds().toCollection(linkedSetOf())
    private val pendingListeners = mutableSetOf<(List<PendingPairingRequest>) -> Unit>()

    override val pendingRequests: List<PendingPairingRequest>
        get() = synchronized(lock) { pending.values.map { it.pendingRequest } }

    override val activeTokens: Collection<TokenState>
        get() = synchronized(lock) {
            val currentTime = now()
            tokens.values
                .filter { it.active && it.expiresAt > currentTime }
                .toList()
        }

    override val blockedDeviceIds: Set<String>
        get() = synchronized(lock) { blockedDevices.toSet() }

    override suspend fun requestPairing(request: PairingRequest): PairingResponse {
        val currentTime = now()
        pruneUsedNonces(currentTime)
        if (request.deviceId in blockedDeviceIds) {
            return PairingResponse(
                status = PairingStatus.REJECTED,
                message = "Dispositivo bloqueado pelo host",
            )
        }

        if (!pairingPolicy.isTimestampValid(request.timestamp, currentTime)) {
            return PairingResponse(
                status = PairingStatus.INVALID,
                message = "Timestamp fora da janela permitida",
            )
        }

        val requestId = request.requestId()
        val decision = PendingDecision(
            pendingRequest = PendingPairingRequest(
                requestId = requestId,
                request = request,
                receivedAt = currentTime,
            ),
        )

        synchronized(lock) {
            if (request.nonce in usedNonces || requestId in pending) {
                return PairingResponse(
                    status = PairingStatus.INVALID,
                    message = "Nonce ja utilizado",
                )
            }
            if (pending.size >= maxPendingPairingRequests) {
                return PairingResponse(
                    status = PairingStatus.REJECTED,
                    message = "Fila de pareamento cheia",
                )
            }

            usedNonces[request.nonce] = currentTime
            pending[requestId] = decision
        }
        notifyPendingListeners()

        val response = withTimeoutOrNull(approvalTimeoutMillis) {
            decision.response.await()
        } ?: PairingResponse(
            status = PairingStatus.EXPIRED,
            message = "Tempo de aprovacao expirado",
        )

        synchronized(lock) {
            pending.remove(requestId)
        }
        notifyPendingListeners()

        return response
    }

    override fun approvePairing(requestId: String, role: Role, verifiedSessionKey: String): Boolean {
        val decision = synchronized(lock) { pending.remove(requestId) } ?: return false
        val currentTime = now()
        val token = tokenGenerator()
        val request = decision.pendingRequest.request
        if (verifiedSessionKey.isBlank()) return false

        val tokenState = TokenState(
            token = token,
            sessionKey = verifiedSessionKey,
            deviceId = request.deviceId,
            role = role.name,
            createdAt = currentTime,
            expiresAt = currentTime + tokenTtlMillis,
            active = true,
        )

        synchronized(lock) {
            tokens[token] = tokenState
        }
        persistence.savePairedDevice(
            PairedDeviceRecord(
                deviceId = request.deviceId,
                deviceName = request.deviceName,
                role = role.name,
                blocked = false,
            ),
        )
        persistence.saveActiveToken(tokenState)

        val responseCompleted = decision.response.complete(
            PairingResponse(
                status = PairingStatus.APPROVED,
                encryptedToken = encryptTokenForSession(token, verifiedSessionKey),
                message = "Pareamento aprovado",
            ),
        )
        if (!responseCompleted) {
            synchronized(lock) {
                tokens[token] = tokenState.copy(active = false)
            }
            persistence.revokeDeviceTokens(request.deviceId)
        }
        return responseCompleted
    }

    override fun rejectPairing(requestId: String): Boolean {
        val decision = synchronized(lock) { pending[requestId] } ?: return false
        return decision.response.complete(
            PairingResponse(
                status = PairingStatus.REJECTED,
                message = "Pareamento rejeitado pelo host",
            ),
        )
    }

    override fun updateDeviceRole(deviceId: String, role: Role): Boolean {
        var updated = false
        synchronized(lock) {
            tokens.entries.forEach { (token, state) ->
                if (state.deviceId == deviceId && state.active) {
                    tokens[token] = state.copy(role = role.name)
                    updated = true
                }
            }
        }
        if (updated) {
            persistence.updateDeviceRole(deviceId, role.name)
        }
        return updated
    }

    override fun revokeDeviceTokens(deviceId: String): Boolean {
        var revoked = false
        synchronized(lock) {
            tokens.entries.forEach { (token, state) ->
                if (state.deviceId == deviceId && state.active) {
                    tokens[token] = state.copy(active = false)
                    revoked = true
                }
            }
        }
        val persistedRevoked = persistence.revokeDeviceTokens(deviceId)
        revoked = revoked || persistedRevoked
        return revoked
    }

    override fun blockDevice(deviceId: String): Boolean {
        synchronized(lock) {
            blockedDevices += deviceId
        }
        persistence.updateDeviceBlocked(deviceId, blocked = true)
        revokeDeviceTokens(deviceId)
        rejectPendingRequestsForDevice(deviceId)
        return true
    }

    override fun unblockDevice(deviceId: String): Boolean {
        val unblocked = synchronized(lock) { blockedDevices.remove(deviceId) }
        if (unblocked) {
            persistence.updateDeviceBlocked(deviceId, blocked = false)
        }
        return unblocked
    }

    override fun observePendingRequests(listener: (List<PendingPairingRequest>) -> Unit): () -> Unit {
        synchronized(lock) {
            pendingListeners += listener
        }
        listener(pendingRequests)
        return {
            synchronized(lock) {
                pendingListeners -= listener
            }
        }
    }

    private fun PairingRequest.requestId(): String = "$deviceId:$nonce"

    private fun pruneUsedNonces(currentTime: Long) {
        val oldestAllowed = currentTime - pairingPolicy.maxTimestampDriftMillis
        synchronized(lock) {
            val expiredNonces = usedNonces
                .filterValues { timestamp -> timestamp < oldestAllowed }
                .keys
                .toList()
            expiredNonces.forEach { nonce -> usedNonces.remove(nonce) }
        }
    }

    private fun notifyPendingListeners() {
        val snapshot = pendingRequests
        val listeners = synchronized(lock) { pendingListeners.toList() }
        listeners.forEach { listener -> listener(snapshot) }
    }

    private fun rejectPendingRequestsForDevice(deviceId: String) {
        val decisions = synchronized(lock) {
            pending.values.filter { it.pendingRequest.request.deviceId == deviceId }
        }
        decisions.forEach { decision ->
            decision.response.complete(
                PairingResponse(
                    status = PairingStatus.REJECTED,
                    message = "Dispositivo bloqueado pelo host",
                ),
            )
        }
    }

    private data class PendingDecision(
        val pendingRequest: PendingPairingRequest,
        val response: CompletableDeferred<PairingResponse> = CompletableDeferred(),
    )
}

private fun currentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
