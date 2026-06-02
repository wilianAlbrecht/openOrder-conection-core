package com.openorder.pairing

class PairingPolicy(
    val maxTimestampDriftMillis: Long = 60_000,
) {
    fun isTimestampValid(timestamp: Long, now: Long): Boolean {
        return timestamp in (now - maxTimestampDriftMillis)..(now + maxTimestampDriftMillis)
    }
}
