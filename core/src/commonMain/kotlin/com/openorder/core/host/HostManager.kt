package com.openorder.core.host

interface HostManager {
    fun startHttpServer()
    fun startWebSocketServer()
    fun startDiscovery()
    fun stopAll()
}
