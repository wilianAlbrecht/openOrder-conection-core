package com.openorder.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import com.openorder.pairing.encryptTokenForSession

class AuthenticatedWebSocketClient(
    private val client: HttpClient = HttpClient(CIO) {
        install(WebSockets)
    },
) {
    suspend fun connect(
        hostAddress: String,
        websocketPort: Int,
        deviceId: String,
        token: String,
        sessionKey: String,
        onMessage: (String) -> Unit = {},
    ) {
        val encryptedToken = encryptTokenForSession(token, sessionKey)
        client.webSocket(
            urlString = "ws://$hostAddress:$websocketPort/connect",
            request = {
                header("Authorization", "EncryptedBearer $encryptedToken")
                header("Device-Id", deviceId)
            },
        ) {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    onMessage(frame.readText())
                }
            }
        }
    }
}
