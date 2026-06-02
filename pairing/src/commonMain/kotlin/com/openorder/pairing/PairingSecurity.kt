package com.openorder.pairing

expect fun secureToken(): String

expect fun encryptTokenForSession(token: String, sessionKeyHex: String): String

expect fun decryptTokenFromSession(encryptedToken: String, sessionKeyHex: String): String
