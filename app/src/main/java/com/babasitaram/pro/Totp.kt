package com.babasitaram.pro

import java.net.URLDecoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 TOTP (Google Authenticator jaisa). Secret base32 ya otpauth:// link dono chalte hain.
 *
 * v6.8.1: otpauth label se issuer/account bhi nikalte hain (OTP / 2FA Manager "Issuer: account" dikhata hai),
 * otpauth://hotp/ reject hota hai (counter-based code galat aata), aur Params se seedha code nikal sakte hain
 * (manager har second parse na kare). Purane API (parse / code(String) / secondsLeft(String)) waise hi hain.
 */
object Totp {

    class Params(
        val secret: ByteArray,
        val digits: Int,
        val period: Int,
        val algo: String,
        val issuer: String = "",
        val account: String = ""
    )

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun base32(input: String): ByteArray? {
        val clean = input.uppercase().replace(" ", "").replace("-", "").trimEnd('=')
        if (clean.isEmpty()) return null
        var buffer = 0
        var bits = 0
        val out = java.io.ByteArrayOutputStream()
        for (ch in clean) {
            val v = ALPHABET.indexOf(ch)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }

    private fun decode(s: String): String =
        try { URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }

    fun parse(input: String): Params? {
        val t = input.trim()
        if (t.isEmpty()) return null
        var secretStr = t
        var digits = 6
        var period = 30
        var algo = "HmacSHA1"
        var issuer = ""
        var account = ""
        if (t.startsWith("otpauth://", ignoreCase = true)) {
            val afterScheme = t.substring("otpauth://".length)
            // otpauth://<type>/<label>?<query>
            val type = afterScheme.substringBefore('/').lowercase()
            if (type == "hotp") return null          // counter-based; TOTP code galat hota
            val label = decode(afterScheme.substringAfter('/', "").substringBefore('?')).trim()
            val colon = label.indexOf(':')
            if (colon >= 0) {
                issuer = label.substring(0, colon).trim()
                account = label.substring(colon + 1).trim()
            } else {
                account = label
            }
            val q = t.substringAfter('?', "")
            for (pair in q.split('&')) {
                val k = pair.substringBefore('=').lowercase()
                val v = decode(pair.substringAfter('=', ""))
                when (k) {
                    "secret" -> secretStr = v
                    "digits" -> digits = v.toIntOrNull() ?: 6
                    "period" -> period = v.toIntOrNull() ?: 30
                    "issuer" -> if (v.isNotBlank()) issuer = v.trim()
                    "algorithm" -> {
                        algo = when (v.uppercase()) {
                            "SHA256" -> "HmacSHA256"
                            "SHA512" -> "HmacSHA512"
                            else -> "HmacSHA1"
                        }
                    }
                }
            }
        }
        val key = base32(secretStr) ?: return null
        if (key.isEmpty()) return null
        if (digits < 6 || digits > 8) digits = 6
        if (period <= 0) period = 30
        return Params(key, digits, period, algo, issuer, account)
    }

    fun code(input: String, nowMs: Long = System.currentTimeMillis()): String? {
        val p = parse(input) ?: return null
        return code(p, nowMs)
    }

    fun code(p: Params, nowMs: Long = System.currentTimeMillis()): String? {
        return try {
            var counter = nowMs / 1000L / p.period
            val msg = ByteArray(8)
            for (i in 7 downTo 0) {
                msg[i] = (counter and 0xFFL).toByte()
                counter = counter shr 8
            }
            val mac = Mac.getInstance(p.algo)
            mac.init(SecretKeySpec(p.secret, p.algo))
            val h = mac.doFinal(msg)
            val off = h[h.size - 1].toInt() and 0x0F
            val bin = ((h[off].toInt() and 0x7F) shl 24) or
                ((h[off + 1].toInt() and 0xFF) shl 16) or
                ((h[off + 2].toInt() and 0xFF) shl 8) or
                (h[off + 3].toInt() and 0xFF)
            var mod = 1
            repeat(p.digits) { mod *= 10 }
            (bin % mod).toString().padStart(p.digits, '0')
        } catch (e: Exception) { null }
    }

    fun secondsLeft(input: String, nowMs: Long = System.currentTimeMillis()): Int {
        val period = parse(input)?.period ?: 30
        return period - ((nowMs / 1000L) % period).toInt()
    }

    fun secondsLeft(p: Params, nowMs: Long = System.currentTimeMillis()): Int =
        p.period - ((nowMs / 1000L) % p.period).toInt()

    /** Google Authenticator jaisa grouping: 6 digit -> "123 456", 8 digit -> "1234 5678". */
    fun format(code: String): String {
        if (code.length < 4) return code
        val h = (code.length + 1) / 2
        return code.substring(0, h) + " " + code.substring(h)
    }
}
