package com.openorder.discovery

import com.openorder.shared.AppConstants
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

class JmDnsDiscoveryService {
    private val registrations = mutableListOf<JmDnsRegistration>()

    fun announce(storeName: String, hostName: String = "OpenOrder Host") {
        if (registrations.isNotEmpty()) return

        lanAddresses().forEach { address ->
            val jmDns = JmDNS.create(address, hostName)
            val serviceInfo = ServiceInfo.create(
                AppConstants.DISCOVERY_SERVICE_TYPE,
                hostName,
                AppConstants.HTTP_PORT,
                0,
                0,
                properties(
                    hostName = hostName,
                    storeName = storeName,
                    hostAddress = address.hostAddress,
                ),
            )
            jmDns.registerService(serviceInfo)
            registrations += JmDnsRegistration(jmDns, serviceInfo)
        }

        if (registrations.isEmpty()) {
            val jmDns = JmDNS.create()
            val serviceInfo = ServiceInfo.create(
                AppConstants.DISCOVERY_SERVICE_TYPE,
                hostName,
                AppConstants.HTTP_PORT,
                0,
                0,
                properties(hostName = hostName, storeName = storeName),
            )
            jmDns.registerService(serviceInfo)
            registrations += JmDnsRegistration(jmDns, serviceInfo)
        }
    }

    fun stop() {
        registrations.forEach { registration ->
            registration.jmDns.unregisterService(registration.serviceInfo)
            registration.jmDns.close()
        }
        registrations.clear()
    }

    private fun properties(
        hostName: String,
        storeName: String,
        hostAddress: String? = null,
    ): Map<String, String> {
        return buildMap {
            put("hostName", hostName)
            put("httpPort", AppConstants.HTTP_PORT.toString())
            put("websocketPort", AppConstants.WEBSOCKET_PORT.toString())
            put("storeName", storeName)
            put("version", AppConstants.VERSION)
            if (hostAddress != null) {
                put("hostAddress", hostAddress)
            }
        }
    }

    private fun lanAddresses(): List<InetAddress> {
        return NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { networkInterface ->
                networkInterface.isUp &&
                    !networkInterface.isLoopback &&
                    !networkInterface.isVirtual &&
                    networkInterface.supportsMulticast()
            }
            .flatMap { networkInterface -> networkInterface.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { address ->
                !address.isLoopbackAddress &&
                    !address.isAnyLocalAddress &&
                    !address.isLinkLocalAddress
            }
            .toList()
    }
}

private data class JmDnsRegistration(
    val jmDns: JmDNS,
    val serviceInfo: ServiceInfo,
)
