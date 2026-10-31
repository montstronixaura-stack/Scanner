package com.example.scanner

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class KeyResult { OK, INVALID, EXPIRED, CLOCK }

object KeyAuth {

    private const val SECRET = "MONTSTRONIXAURA"

    private const val MAX_AHEAD_SECONDS = 5 * 60L

    fun normalize(raw: String): String =
        raw.uppercase().filter { it.isLetterOrDigit() }

    private fun sign(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(), "HmacSHA256"))
        val bytes = mac.doFinal(payload.toByteArray())
        return bytes.joinToString("") { "%02X".format(it) }.take(8)
    }

    fun check(raw: String): KeyResult {
        val k = normalize(raw)
        if (k.length != 14) return KeyResult.INVALID
        val expStr = k.substring(0, 6)
        val sig = k.substring(6)
        val exp = expStr.toLongOrNull(36) ?: return KeyResult.INVALID
        if (sign(expStr) != sig) return KeyResult.INVALID

        val now = System.currentTimeMillis() / 1000
        if (now > exp) return KeyResult.EXPIRED
        if (exp - now > MAX_AHEAD_SECONDS) return KeyResult.CLOCK
        return KeyResult.OK
    }
}
