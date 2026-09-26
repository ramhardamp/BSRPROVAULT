package com.babasitaram.pro

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AppPrefs {
    private const val FILE = "bsr_prefs"

    private fun p(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // Screenshot / recent-apps preview block
    fun getSecureScreen(ctx: Context): Boolean = p(ctx).getBoolean("secure", true)
    fun setSecureScreen(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("secure", v).apply()

    // Galat master password par lockout
    fun lockRemainingMs(ctx: Context): Long {
        val until = p(ctx).getLong("lockUntil", 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }
    fun recordFailure(ctx: Context): Long {
        val n = p(ctx).getInt("fails", 0) + 1
        val wait = when {
            n >= 10 -> 3600000L
            n >= 8 -> 900000L
            n >= 7 -> 300000L
            n >= 6 -> 60000L
            n >= 5 -> 30000L
            else -> 0L
        }
        p(ctx).edit().putInt("fails", n).putLong("lockUntil", System.currentTimeMillis() + wait).apply()
        return wait
    }
    fun failCount(ctx: Context): Int = p(ctx).getInt("fails", 0)
    fun clearFailures(ctx: Context) = p(ctx).edit().putInt("fails", 0).putLong("lockUntil", 0L).apply()

    // Auto-backup folder (SAF tree uri)
    fun getBackupTree(ctx: Context): String? = p(ctx).getString("bkTree", null)
    fun setBackupTree(ctx: Context, v: String?) {
        if (v == null) p(ctx).edit().remove("bkTree").apply() else p(ctx).edit().putString("bkTree", v).apply()
    }
    fun getLastAutoBackup(ctx: Context): Long = p(ctx).getLong("bkLast", 0L)
    fun setLastAutoBackup(ctx: Context, t: Long) = p(ctx).edit().putLong("bkLast", t).apply()

    // Biometric
    var biometricEnabled: Boolean = false
    fun setBiometric(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("bio", v).apply()
        biometricEnabled = v
        if (!v) clearBioCache(ctx)
    }
    fun getBiometric(ctx: Context): Boolean { biometricEnabled = p(ctx).getBoolean("bio", false); return biometricEnabled }

    // Theme: 0=system, 1=light, 2=dark
    fun setTheme(ctx: Context, mode: Int) = p(ctx).edit().putInt("theme_mode", mode.coerceIn(0, 2)).apply()
    fun getTheme(ctx: Context): Int = p(ctx).getInt("theme_mode", 0).coerceIn(0, 2)

    // Auto-lock timeout (minutes, 0=never)
    fun setAutoLock(ctx: Context, mins: Int) = p(ctx).edit().putInt("al", mins).apply()
    fun getAutoLock(ctx: Context): Int = p(ctx).getInt("al", 5)

    // Clipboard clear (seconds, 0=never)
    fun setClipClear(ctx: Context, secs: Int) = p(ctx).edit().putInt("cc", secs).apply()
    fun getClipClear(ctx: Context): Int = p(ctx).getInt("cc", 30)

    // Last active time
    fun setLastActive(ctx: Context) = p(ctx).edit().putLong("la", System.currentTimeMillis()).apply()
    fun isSessionExpired(ctx: Context): Boolean {
        val mins = getAutoLock(ctx)
        if (mins == 0) return false
        val last = p(ctx).getLong("la", 0L)
        return System.currentTimeMillis() - last > mins * 60 * 1000L
    }

    // Master password cache (biometric unlock + export ke liye).
    // Android Keystore ki non-exportable AES-GCM key se encrypt hota hai — plain/obfuscated nahi.
    private const val KS_ALIAS = "bsr_master_wrap_v1"
    private const val CACHE_FILE = "bsr_bio_cache"

    private fun wrapKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(KS_ALIAS, null)
        if (existing is SecretKey) return existing
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                KS_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    fun saveMasterForBio(ctx: Context, master: String) {
        if (!getBiometric(ctx)) {
            clearBioCache(ctx)
            return
        }
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, wrapKey())
            val blob = c.iv + c.doFinal(master.toByteArray(Charsets.UTF_8))
            ctx.getSharedPreferences(CACHE_FILE, Context.MODE_PRIVATE).edit()
                .clear()
                .putString("cm2", Base64.encodeToString(blob, Base64.NO_WRAP))
                .commit()
        } catch (e: Exception) {
            clearBioCache(ctx)
        }
    }

    fun getMasterForBio(ctx: Context): String? {
        val enc = ctx.getSharedPreferences(CACHE_FILE, Context.MODE_PRIVATE)
            .getString("cm2", null) ?: return null
        return try {
            val b = Base64.decode(enc, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, b.copyOfRange(0, 12)))
            String(c.doFinal(b, 12, b.size - 12), Charsets.UTF_8)
        } catch (e: Exception) { null }
    }

    fun clearBioCache(ctx: Context) {
        ctx.getSharedPreferences(CACHE_FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
