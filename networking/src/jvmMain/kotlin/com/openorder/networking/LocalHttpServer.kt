package com.openorder.networking

import com.openorder.shared.AppConstants
import com.openorder.core.models.PairingRequest
import com.openorder.pairing.PairingManager
import com.openorder.pairing.PairingResponse
import com.openorder.pairing.PairingStatus
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalHttpServer(
    private val port: Int = AppConstants.HTTP_PORT,
    private val pairingManager: PairingManager? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    private var engine: EmbeddedServer<*, *>? = null

    fun start() {
        if (engine != null) return

        engine = embeddedServer(Netty, host = "0.0.0.0", port = port) {
            routing {
                get("/health") {
                    call.respondText("openorder-ok")
                }

                post("/pair/request") {
                    val manager = pairingManager
                    if (manager == null) {
                        call.respond(HttpStatusCode.ServiceUnavailable, "pairing manager unavailable")
                        return@post
                    }

                    val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    if (contentLength != null && contentLength > MAX_PAIRING_REQUEST_CHARS) {
                        call.respond(HttpStatusCode.PayloadTooLarge, "pairing request too large")
                        return@post
                    }

                    val requestText = call.receiveText()
                    if (requestText.length > MAX_PAIRING_REQUEST_CHARS) {
                        call.respond(HttpStatusCode.PayloadTooLarge, "pairing request too large")
                        return@post
                    }

                    val request = try {
                        json.decodeFromString<PairingRequest>(requestText)
                    } catch (_: SerializationException) {
                        call.respond(HttpStatusCode.BadRequest, "invalid pairing request")
                        return@post
                    }

                    val response = manager.requestPairing(request)
                    call.respondPairing(response)
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1_500)
        engine = null
    }

    private suspend fun io.ktor.server.application.ApplicationCall.respondPairing(response: PairingResponse) {
        val statusCode = when (response.status) {
            PairingStatus.APPROVED -> HttpStatusCode.OK
            PairingStatus.REJECTED -> HttpStatusCode.Forbidden
            PairingStatus.EXPIRED -> HttpStatusCode.RequestTimeout
            PairingStatus.INVALID -> HttpStatusCode.BadRequest
        }
        respondText(
            text = json.encodeToString(response),
            contentType = ContentType.Application.Json,
            status = statusCode,
        )
    }
}

private const val MAX_PAIRING_REQUEST_CHARS = 8_192
