package com.babasitaram.pro

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * BSRPRO.Vault  —  backup format V3  (Android / Chrome / Firefox share this exact spec)
 * See BSRPRO-VAULT-FORMAT-V3.md.
 *
 *   file  = Base64( header(38) || AES-256-GCM ciphertext+tag )
 *   header = "BSRV" | 0x03 (version) | 0x01 (KDF: PBKDF2-HMAC-SHA256) | iterations u32 BE | salt[16] | iv[12]
 *   AAD    = the whole 38-byte header  (version/kdf/iterations/salt/iv are authenticated)
 *   KDF    = PBKDF2-HMAC-SHA256(UTF-8(master), "BSRPRO.Vault.v3.backup\u0000" || salt, 600000) -> 32 bytes
 *
 * V3 is decrypted with V3 rules only. There is NO fallback to weaker/legacy iteration counts.
 * Pure JVM code (no Android / Gson dependency) so it can be unit-tested off-device.
 */
object BsrVault {
    /** base64("BSRV" 0x03 0x01") — every V3 file starts with these 8 characters. */
    const val PREFIX_B64 = "QlNSVgMB"
    const val VERSION = 3
    const val ITERATIONS = 600_000

    private const val KDF_PBKDF2_SHA256 = 1
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val HEADER_LEN = 4 + 1 + 1 + 4 + SALT_LEN + IV_LEN   // 38
    private val MAGIC = byteArrayOf(0x42, 0x53, 0x52, 0x56)              // "BSRV"
    private val KDF_DOMAIN = "BSRPRO.Vault.v3.backup".toByteArray(Charsets.UTF_8) + byteArrayOf(0)

    /** Header/format problem (not a BSRPRO.Vault V3 file, unsupported version/KDF/iterations, truncated). */
    class FormatException(message: String) : Exception(message)
    /** AES-GCM authentication failed: wrong Master Password OR the file was modified/corrupted. */
    class AuthException : Exception("BSRPRO.Vault authentication failed")

    fun isV3(text: String): Boolean =
        text.trimStart('\uFEFF').trimStart().startsWith(PREFIX_B64)

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        // Domain-separated salt: this key can never equal the vault-storage key or a legacy-backup key.
        val kdfSalt = KDF_DOMAIN + salt
        val spec = PBEKeySpec(password.toCharArray(), kdfSalt, iterations, 256)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(password: String, plaintext: ByteArray): String {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }      // fresh random IV for EVERY backup
        val header = ByteArray(HEADER_LEN)
        System.arraycopy(MAGIC, 0, header, 0, 4)
        header[4] = VERSION.toByte()
        header[5] = KDF_PBKDF2_SHA256.toByte()
        header[6] = (ITERATIONS ushr 24).toByte(); header[7] = (ITERATIONS ushr 16).toByte()
        header[8] = (ITERATIONS ushr 8).toByte();  header[9] = ITERATIONS.toByte()
        System.arraycopy(salt, 0, header, 10, SALT_LEN)
        System.arraycopy(iv, 0, header, 10 + SALT_LEN, IV_LEN)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        val ct = cipher.doFinal(plaintext)
        return java.util.Base64.getEncoder().encodeToString(header + ct)
    }

    fun decrypt(password: String, text: String): ByteArray {
        val clean = text.trimStart('\uFEFF').trim().replace(Regex("\\s"), "")
        val bytes = try { java.util.Base64.getDecoder().decode(clean) }
        catch (e: IllegalArgumentException) { throw FormatException("BSRPRO.Vault: base64 invalid") }
        if (bytes.size < HEADER_LEN + 16) throw FormatException("BSRPRO.Vault: file truncated")
        for (i in 0 until 4) if (bytes[i] != MAGIC[i]) throw FormatException("BSRPRO.Vault: bad magic")
        if ((bytes[4].toInt() and 0xFF) != VERSION) throw FormatException("BSRPRO.Vault: unsupported version " + (bytes[4].toInt() and 0xFF))
        if ((bytes[5].toInt() and 0xFF) != KDF_PBKDF2_SHA256) throw FormatException("BSRPRO.Vault: unsupported KDF")
        val iters = ((bytes[6].toInt() and 0xFF) shl 24) or ((bytes[7].toInt() and 0xFF) shl 16) or
                ((bytes[8].toInt() and 0xFF) shl 8) or (bytes[9].toInt() and 0xFF)
        // No downgrade: V3 means exactly 600,000 iterations.
        if (iters != ITERATIONS) throw FormatException("BSRPRO.Vault: iteration count not allowed for V3")
        val header = bytes.copyOfRange(0, HEADER_LEN)
        val salt = bytes.copyOfRange(10, 10 + SALT_LEN)
        val iv = bytes.copyOfRange(10 + SALT_LEN, HEADER_LEN)
        val ct = bytes.copyOfRange(HEADER_LEN, bytes.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iters), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(header)
            cipher.doFinal(ct)
        } catch (e: java.security.GeneralSecurityException) {
            throw AuthException()
        }
    }
}
