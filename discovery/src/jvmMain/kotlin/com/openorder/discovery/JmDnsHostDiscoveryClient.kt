package com.openorder.discovery

import com.openorder.shared.AppConstants
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

class JmDnsHostDiscoveryClient {
    fun scan(timeoutMillis: Long = 2_500): List<DiscoveredHost> {
        JmDNS.create().use { jmDns ->
            return jmDns.list(AppConstants.DISCOVERY_SERVICE_TYPE, timeoutMillis)
                .mapNotNull { serviceInfo -> serviceInfo.toDiscoveredHost() }
                .distinctBy { host -> "${host.hostAddress}:${host.httpPort}" }
        }
    }

    private fun ServiceInfo.toDiscoveredHost(): DiscoveredHost? {
        val address = getPropertyString("hostAddress")
            ?.takeIf { it.isNotBlank() }
            ?: hostAddresses.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return null
        val httpPort = getPropertyString("httpPort")?.toIntOrNull() ?: port
        val websocketPort = getPropertyString("websocketPort")?.toIntOrNull() ?: AppConstants.WEBSOCKET_PORT

        return DiscoveredHost(
            hostName = getPropertyString("hostName") ?: name,
            hostAddress = address,
            httpPort = httpPort,
            websocketPort = websocketPort,
            storeName = getPropertyString("storeName") ?: name,
            version = getPropertyString("version") ?: AppConstants.VERSION,
        )
    }
}
