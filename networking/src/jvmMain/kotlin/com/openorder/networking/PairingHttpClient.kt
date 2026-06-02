package com.openorder.networking

import com.openorder.core.models.PairingRequest
import com.openorder.pairing.PairingResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PairingHttpClient(
    private val client: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 75_000
            socketTimeoutMillis = 75_000
            connectTimeoutMillis = 5_000
        }
    },
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    suspend fun requestPairing(hostAddress: String, httpPort: Int, request: PairingRequest): PairingResponse {
        val response = client.post("http://$hostAddress:$httpPort/pair/request") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        return json.decodeFromString<PairingResponse>(response.bodyAsText())
    }
}
