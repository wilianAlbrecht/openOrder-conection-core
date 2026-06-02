package com.openorder.pairing

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual fun secureToken(): String {
    val bytes = ByteArray(TOKEN_BYTES)
    secureRandom.nextBytes(bytes)
    return base64Url(bytes)
}

actual fun encryptTokenForSession(token: String, sessionKeyHex: String): String {
    val iv = ByteArray(GCM_IV_BYTES)
    secureRandom.nextBytes(iv)
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, sessionKey(sessionKeyHex), GCMParameterSpec(GCM_TAG_BITS, iv))
    val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
    return listOf(
        TOKEN_ENCRYPTION_VERSION,
        base64Url(iv),
        base64Url(encrypted),
    ).joinToString(":")
}

actual fun decryptTokenFromSession(encryptedToken: String, sessionKeyHex: String): String {
    val parts = encryptedToken.split(":")
    require(parts.size == 3 && parts[0] == TOKEN_ENCRYPTION_VERSION) { "Token criptografado invalido" }
    val iv = base64UrlDecode(parts[1])
    val encrypted = base64UrlDecode(parts[2])
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, sessionKey(sessionKeyHex), GCMParameterSpec(GCM_TAG_BITS, iv))
    return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
}

private fun sessionKey(sessionKeyHex: String): SecretKeySpec {
    val bytes = sessionKeyHex.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
    require(bytes.size == 32) { "SessionKey invalida" }
    return SecretKeySpec(bytes, "AES")
}

private fun base64Url(bytes: ByteArray): String {
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

private fun base64UrlDecode(value: String): ByteArray {
    return Base64.decode(value, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

private val secureRandom = SecureRandom()
private const val TOKEN_BYTES = 32
private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BITS = 128
private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
private const val TOKEN_ENCRYPTION_VERSION = "v1"
