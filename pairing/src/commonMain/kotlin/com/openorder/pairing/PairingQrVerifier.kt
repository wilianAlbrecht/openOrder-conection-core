package com.openorder.pairing

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class PairingQrVerifier(
    private val pairingPolicy: PairingPolicy = PairingPolicy(),
    private val now: () -> Long = { currentTimeMillis() },
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun verify(pendingRequest: PendingPairingRequest, payload: String): PairingQrVerificationResult {
        val qrPayload = try {
            json.decodeFromString<PairingQrPayload>(payload)
        } catch (_: SerializationException) {
            return PairingQrVerificationResult(false, "QRCode invalido")
        }

        val request = pendingRequest.request
        return when {
            qrPayload.deviceId != request.deviceId -> PairingQrVerificationResult(false, "Device ID do QRCode nao confere")
            qrPayload.nonce != request.nonce -> PairingQrVerificationResult(false, "Nonce do QRCode nao confere")
            qrPayload.timestamp != request.timestamp -> PairingQrVerificationResult(false, "Timestamp do QRCode nao confere")
            !pairingPolicy.isTimestampValid(qrPayload.timestamp, now()) -> PairingQrVerificationResult(false, "QRCode expirado")
            else -> PairingQrVerificationResult(true, "QRCode validado", qrPayload.sessionKey)
        }
    }
}

data class PairingQrVerificationResult(
    val valid: Boolean,
    val message: String,
    val sessionKey: String? = null,
)

private fun currentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
