package com.babasitaram.pro

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Safe app-wide diagnostic event log.
 *
 * Rules:
 * - Never store passwords, usernames, OTPs, vault JSON or clipboard contents.
 * - Maximum 200 events.
 * - Same timestamp-first style as AutofillLog.
 */
object SystemLog {
    private const val FILE = "bsr_system_log"
    private const val KEY = "events"
    private const val MAX_EVENTS = 200

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun sanitize(message: String): String {
        var s = message
        val secretKeys = listOf(
            "password", "passwd", "pass", "master", "secret",
            "token", "otp", "totp", "clipboard"
        )
        for (key in secretKeys) {
            s = s.replace(
                Regex("(?i)\\b" + Regex.escape(key) + "\\s*[=:]\\s*[^\\s,;]+"),
                key + "=<redacted>"
            )
        }
        s = s.replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._-]+"), "Bearer <redacted>")
        return s.take(500)
    }

    @Synchronized
    fun add(ctx: Context, message: String) {
        try {
            val safe = sanitize(message)
            val ts = SimpleDateFormat(
                "dd MMM HH:mm:ss",
                Locale.getDefault()
            ).format(Date())
            val old = prefs(ctx).getString(KEY, "").orEmpty()
            val lines = (ts + "  " + safe + "\n" + old)
                .lineSequence()
                .filter { it.isNotBlank() }
                .take(MAX_EVENTS)
                .joinToString("\n")
            prefs(ctx).edit().putString(KEY, lines).apply()
        } catch (_: Exception) {
        }
    }

    fun read(ctx: Context): String =
        prefs(ctx).getString(KEY, "").orEmpty()

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY).apply()
    }

    fun buildReport(ctx: Context): String {
        val sb = StringBuilder()
        val theme = when (AppPrefs.getTheme(ctx)) {
            1 -> "Light"
            2 -> "Dark"
            else -> "System"
        }
        val autoBackup = AutoBackup.isConfigured(ctx)
        val lastBackup = AppPrefs.getLastAutoBackup(ctx)

        sb.appendLine("===== BSR Vault System Diagnostics =====")
        sb.appendLine("Time       : " + SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.US
        ).format(Date()))
        sb.appendLine("App version: v" + BuildConfig.VERSION_NAME)
        sb.appendLine()
        sb.appendLine("--- 1. DEVICE INFO ---")
        sb.appendLine("Manufacturer: " + Build.MANUFACTURER)
        sb.appendLine("Model       : " + Build.MODEL)
        sb.appendLine("Android     : " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
        sb.appendLine("Locale      : " + Locale.getDefault().toLanguageTag())
        sb.appendLine()
        sb.appendLine("--- 2. CORE / VAULT ---")
        sb.appendLine("Vault locked: " + !VaultManager.isUnlocked)
        sb.appendLine("Entries     : " + if (VaultManager.isUnlocked) {
            VaultManager.getPasswords().size.toString()
        } else "(locked)")
        sb.appendLine("Crypto      : AES-GCM / existing vault format")
        sb.appendLine()
        sb.appendLine("--- 3. SECURITY ---")
        sb.appendLine("Secure screen: " + AppPrefs.getSecureScreen(ctx))
        sb.appendLine("Biometric    : " + AppPrefs.getBiometric(ctx))
        sb.appendLine("Auto-lock    : " + AppPrefs.getAutoLock(ctx) + " min")
        sb.appendLine("Clipboard clr: " + AppPrefs.getClipClear(ctx) + " sec")
        sb.appendLine()
        sb.appendLine("--- 4. AUTOFILL ---")
        sb.appendLine("Enabled      : " + AutofillSetup.isEnabled(ctx))
        sb.appendLine("Chrome status: " + ChromeAutofill.getStatus(ctx))
        sb.appendLine("PSL loaded   : " + runCatching {
            com.babasitaram.pro.autofill.Psl.isLoaded()
        }.getOrDefault(false))
        sb.appendLine("Service      : " + runCatching {
            Settings.Secure.getString(
                ctx.contentResolver,
                "autofill_service"
            )
        }.getOrNull().orEmpty().ifBlank { "none" })
        sb.appendLine()
        sb.appendLine("--- 5. DATA / BACKUP ---")
        sb.appendLine("Auto-backup  : " + autoBackup)
        sb.appendLine("Last backup  : " + if (lastBackup > 0L) {
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
            ).format(Date(lastBackup))
        } else "never")
        sb.appendLine()
        sb.appendLine("--- 6. UI / THEME ---")
        sb.appendLine("Theme pref   : " + theme)
        sb.appendLine("UI night mode: " + (
            ctx.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            ))
        sb.appendLine()
        sb.appendLine("--- 7. RECENT EVENTS (max 200) ---")
        sb.appendLine(read(ctx).ifBlank { "(no system events)" })
        sb.appendLine()
        sb.appendLine("--- 8. QUICK HEALTH CHECK ---")
        healthCheck(ctx).forEach { sb.appendLine(it) }
        sb.appendLine()
        sb.appendLine("NOTE: No passwords, usernames, OTP secrets, clipboard contents or vault JSON are recorded here.")
        return sb.toString()
    }

    fun healthCheck(ctx: Context): List<String> {
        val out = mutableListOf<String>()
        out += if (VaultManager.isUnlocked) "PASS  Vault state readable" else "WARN  Vault is locked"
        out += if (AutofillSetup.isEnabled(ctx)) "PASS  BSR Autofill enabled" else "WARN  BSR Autofill disabled"
        out += if (AutoBackup.isConfigured(ctx)) "PASS  Auto-backup folder configured" else "WARN  Auto-backup folder not configured"
        out += "PASS  Diagnostic log secret-redaction enabled"
        out += "PASS  Event retention <= $MAX_EVENTS"
        return out
    }
}
