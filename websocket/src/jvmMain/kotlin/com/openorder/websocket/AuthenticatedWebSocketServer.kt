package com.openorder.websocket

import com.openorder.core.models.SocketEventTypes
import com.openorder.core.models.socketEvent
import com.openorder.core.security.TokenState
import com.openorder.pairing.decryptTokenFromSession
import com.openorder.shared.AppConstants
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.origin
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AuthenticatedWebSocketServer(
    private val port: Int = AppConstants.WEBSOCKET_PORT,
    private val tokenProvider: () -> Collection<TokenState> = { emptyList() },
    private val onClientsChanged: (List<ConnectedClientInfo>) -> Unit = {},
    private val onClientDisconnected: (String) -> Unit = {},
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    private val lock = Any()
    private val connectedClients = mutableMapOf<String, ConnectedClient>()
    private val handshakeAttempts = linkedMapOf<String, MutableList<Long>>()
    private var engine: EmbeddedServer<*, *>? = null

    fun start() {
        if (engine != null) return

        engine = embeddedServer(Netty, host = "0.0.0.0", port = port) {
            install(WebSockets)

            routing {
                webSocket("/connect") {
                    val remoteHost = call.request.origin.remoteHost
                    if (!allowHandshake(remoteHost)) {
                        close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Too many authentication attempts"))
                        return@webSocket
                    }

                    val encryptedToken = call.request.headers["Authorization"]
                        ?.removePrefix("EncryptedBearer ")
                        ?.trim()
                    val deviceId = call.request.headers["Device-Id"]
                    val tokenState = tokenProvider().firstOrNull {
                        it.deviceId == deviceId &&
                            it.active &&
                            it.expiresAt > System.currentTimeMillis() &&
                            encryptedToken.matchesToken(it)
                    }

                    if (tokenState == null || deviceId == null) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid session token"))
                        return@webSocket
                    }

                    val previousClient = synchronized(lock) {
                        connectedClients.remove(deviceId)
                    }
                    previousClient?.session?.close(
                        CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Duplicate device session"),
                    )

                    synchronized(lock) {
                        connectedClients[deviceId] = ConnectedClient(
                            deviceId = deviceId,
                            role = tokenState.role,
                            session = this,
                            connectedAt = System.currentTimeMillis(),
                        )
                    }
                    notifyClientsChanged()
                    send(
                        Frame.Text(
                            json.encodeToString(
                                socketEvent(
                                    type = SocketEventTypes.SESSION_CONNECTED,
                                    payload = deviceId,
                                ),
                            ),
                        ),
                    )

                    try {
                        for (frame in incoming) {
                            frame.toString()
                        }
                    } finally {
                        val removed = synchronized(lock) {
                            connectedClients.remove(deviceId)
                        }
                        if (removed != null) {
                            onClientDisconnected(deviceId)
                        }
                        notifyClientsChanged()
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        val deviceIds = synchronized(lock) { connectedClients.keys.toList() }
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1_500)
        engine = null
        synchronized(lock) {
            connectedClients.clear()
        }
        deviceIds.forEach(onClientDisconnected)
        notifyClientsChanged()
    }

    fun disconnectDevice(deviceId: String): Boolean {
        val client = synchronized(lock) { connectedClients.remove(deviceId) } ?: return false
        runBlocking {
            client.session.close(CloseReason(CloseReason.Codes.NORMAL, "Session revoked by host"))
        }
        notifyClientsChanged()
        return true
    }

    fun updateDeviceRole(deviceId: String, role: String): Boolean {
        val client = synchronized(lock) {
            val client = connectedClients[deviceId] ?: return@synchronized null
            connectedClients[deviceId] = client.copy(role = role)
            client
        } ?: return false
        notifyClientsChanged()
        runBlocking {
            client.session.send(
                Frame.Text(
                    json.encodeToString(
                        socketEvent(
                            type = SocketEventTypes.ROLE_CHANGED,
                            payload = role,
                        ),
                    ),
                ),
            )
        }
        return true
    }

    private fun notifyClientsChanged() {
        val snapshot = synchronized(lock) { connectedClients.values.toList() }
        onClientsChanged(
            snapshot.map { client ->
                ConnectedClientInfo(
                    deviceId = client.deviceId,
                    role = client.role,
                    connectedAt = client.connectedAt,
                )
            },
        )
    }

    private fun String?.matchesToken(tokenState: TokenState): Boolean {
        if (this.isNullOrBlank()) return false
        return runCatching {
            decryptTokenFromSession(this, tokenState.sessionKey) == tokenState.token
        }.getOrDefault(false)
    }

    private fun allowHandshake(remoteHost: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val oldestAllowed = now - HANDSHAKE_RATE_WINDOW_MILLIS
            val attempts = handshakeAttempts.getOrPut(remoteHost) { mutableListOf() }
            attempts.removeAll { timestamp -> timestamp < oldestAllowed }
            if (attempts.size >= MAX_HANDSHAKES_PER_WINDOW) return false
            attempts += now
            return true
        }
    }
}

private const val HANDSHAKE_RATE_WINDOW_MILLIS = 10_000L
private const val MAX_HANDSHAKES_PER_WINDOW = 20
