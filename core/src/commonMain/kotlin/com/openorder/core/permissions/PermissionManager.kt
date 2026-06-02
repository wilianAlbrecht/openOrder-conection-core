package com.openorder.core.permissions

class PermissionManager {
    private val permissions = mapOf(
        Role.ADMIN to setOf("*"),
        Role.CAIXA to setOf("payments", "closing", "tables.view"),
        Role.GARCOM to setOf("orders.open", "orders.create", "tables.view"),
        Role.COZINHA to setOf("kitchen.queue.view", "orders.status.update"),
        Role.VISUALIZACAO to setOf("tables.view", "orders.view"),
    )

    fun canAccess(role: Role, permission: String): Boolean {
        val allowed = permissions[role].orEmpty()
        return "*" in allowed || permission in allowed
    }
}
