package com.openorder.database

import com.openorder.core.security.TokenState
import com.openorder.pairing.PairedDeviceRecord
import com.openorder.pairing.PairingPersistence
import java.security.SecureRandom
import java.sql.DriverManager
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SqlitePairingPersistence(
    private val jdbcUrl: String = DEFAULT_JDBC_URL,
) : PairingPersistence {
    init {
        ensureRestrictedDatabasePath(jdbcUrl)
        DatabaseInitializer(jdbcUrl).initialize()
        restrictDatabaseFile(jdbcUrl)
    }

    override fun loadBlockedDeviceIds(): Set<String> {
        return connection().use { connection ->
            connection.prepareStatement(
                "SELECT device_id FROM paired_devices WHERE blocked = 1",
            ).use { statement ->
                statement.executeQuery().use { result ->
                    buildSet {
                        while (result.next()) {
                            add(result.getString("device_id"))
                        }
                    }
                }
            }
        }
    }

    override fun savePairedDevice(device: PairedDeviceRecord) {
        connection().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO paired_devices(device_id, device_name, role, blocked)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(device_id) DO UPDATE SET
                    device_name = excluded.device_name,
                    role = excluded.role,
                    blocked = excluded.blocked
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, device.deviceId)
                statement.setString(2, device.deviceName)
                statement.setString(3, device.role)
                statement.setInt(4, if (device.blocked) 1 else 0)
                statement.executeUpdate()
            }
        }
    }

    override fun saveActiveToken(token: TokenState) {
        connection().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO active_tokens(token, device_id, expires_at, active)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(token) DO UPDATE SET
                    device_id = excluded.device_id,
                    expires_at = excluded.expires_at,
                    active = excluded.active
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, token.token.hmacSha256Hex(loadOrCreateHmacKey()))
                statement.setString(2, token.deviceId)
                statement.setLong(3, token.expiresAt)
                statement.setInt(4, if (token.active) 1 else 0)
                statement.executeUpdate()
            }
        }
    }

    override fun updateDeviceRole(deviceId: String, role: String) {
        connection().use { connection ->
            connection.prepareStatement(
                "UPDATE paired_devices SET role = ? WHERE device_id = ?",
            ).use { statement ->
                statement.setString(1, role)
                statement.setString(2, deviceId)
                statement.executeUpdate()
            }
        }
    }

    override fun updateDeviceBlocked(deviceId: String, blocked: Boolean) {
        connection().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO paired_devices(device_id, device_name, role, blocked)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(device_id) DO UPDATE SET blocked = excluded.blocked
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, deviceId)
                statement.setString(2, deviceId)
                statement.setString(3, "")
                statement.setInt(4, if (blocked) 1 else 0)
                statement.executeUpdate()
            }
        }
    }

    override fun revokeDeviceTokens(deviceId: String): Boolean {
        return connection().use { connection ->
            connection.prepareStatement(
                "UPDATE active_tokens SET active = 0 WHERE device_id = ? AND active = 1",
            ).use { statement ->
                statement.setString(1, deviceId)
                statement.executeUpdate() > 0
            }
        }
    }

    private fun connection() = DriverManager.getConnection(jdbcUrl)
}

val DEFAULT_JDBC_URL: String = "jdbc:sqlite:${defaultDatabasePath()}"

private fun defaultDatabasePath(): String {
    val home = System.getProperty("user.home", ".")
    return Path.of(home, ".openorder", "openorder.db").toAbsolutePath().toString()
}

private fun ensureRestrictedDatabasePath(jdbcUrl: String) {
    val prefix = "jdbc:sqlite:"
    if (!jdbcUrl.startsWith(prefix)) return
    val path = Path.of(jdbcUrl.removePrefix(prefix))
    val parent = path.parent ?: return
    Files.createDirectories(parent)
    runCatching {
        parent.toFile().setReadable(false, false)
        parent.toFile().setWritable(false, false)
        parent.toFile().setExecutable(false, false)
        parent.toFile().setReadable(true, true)
        parent.toFile().setWritable(true, true)
        parent.toFile().setExecutable(true, true)
    }
}

private fun restrictDatabaseFile(jdbcUrl: String) {
    val prefix = "jdbc:sqlite:"
    if (!jdbcUrl.startsWith(prefix)) return
    val file = Path.of(jdbcUrl.removePrefix(prefix)).toFile()
    if (!file.exists()) return
    runCatching {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }
}

private fun loadOrCreateHmacKey(): ByteArray {
    val path = defaultHmacKeyPath()
    Files.createDirectories(path.parent)
    if (Files.exists(path)) {
        restrictOwnerOnly(path)
        return Base64.getDecoder().decode(Files.readString(path).trim())
    }

    val key = ByteArray(32)
    SecureRandom().nextBytes(key)
    Files.writeString(path, Base64.getEncoder().encodeToString(key))
    restrictOwnerOnly(path)
    return key
}

private fun defaultHmacKeyPath(): Path {
    val home = System.getProperty("user.home", ".")
    return Path.of(home, ".openorder", "token-hmac.key").toAbsolutePath()
}

private fun restrictOwnerOnly(path: Path) {
    val file = path.toFile()
    runCatching {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }
}

private fun String.hmacSha256Hex(key: ByteArray): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    val digest = mac.doFinal(toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
