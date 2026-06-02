package com.openorder.discovery

data class DiscoveredHost(
    val hostName: String,
    val hostAddress: String,
    val httpPort: Int,
    val websocketPort: Int,
    val storeName: String,
    val version: String,
)
