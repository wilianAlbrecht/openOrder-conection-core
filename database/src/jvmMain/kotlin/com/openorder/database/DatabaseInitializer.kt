package com.openorder.database

import java.sql.DriverManager

class DatabaseInitializer(
    private val jdbcUrl: String = DEFAULT_JDBC_URL,
) {
    fun initialize() {
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS paired_devices (
                        device_id TEXT PRIMARY KEY,
                        device_name TEXT,
                        role TEXT,
                        blocked INTEGER
                    );
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS active_tokens (
                        token TEXT PRIMARY KEY,
                        device_id TEXT,
                        expires_at INTEGER,
                        active INTEGER
                    );
                    """.trimIndent(),
                )
            }
        }
    }
}
