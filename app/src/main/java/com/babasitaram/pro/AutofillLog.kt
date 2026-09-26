package com.babasitaram.pro

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Autofill ka chhota diagnostic log (sirf phone mein, koi password/username nahi).
 * Settings → "Autofill Diagnostics" mein dikhta hai.
 */
object AutofillLog {
    private const val FILE = "bsr_aflog"
    private const val KEY = "log"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    fun add(ctx: Context, msg: String) {
        try {
            val ts = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date())
            val old = p(ctx).getString(KEY, "") ?: ""
            val lines = (ts + "  " + msg + "\n" + old).lines().filter { it.isNotBlank() }.take(30)
            p(ctx).edit().putString(KEY, lines.joinToString("\n")).apply()
        } catch (e: Exception) { }
    }

    fun read(ctx: Context): String = p(ctx).getString(KEY, "") ?: ""

    fun clear(ctx: Context) {
        p(ctx).edit().clear().apply()
    }

    /** Poora diagnostics report (Diagnostics screen aur Copy ke liye). Isme koi password/username nahi hota. */
    fun buildDiagnostics(ctx: Context): String {
        // Diagnostics must initialize PSL before reporting its state; otherwise a fresh
        // process can incorrectly show "PSL loaded: false" before the first fill request.
        try { com.babasitaram.pro.autofill.Psl.ensureLoaded(ctx) } catch (_: Exception) { }
        val sb = StringBuilder()
        sb.appendLine("===== BSR Pro Autofill Diagnostics =====")
        sb.appendLine("Time       : " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        sb.appendLine("App version: v" + BuildConfig.VERSION_NAME)
        sb.appendLine()
        sb.appendLine("--- DEVICE ---")
        sb.appendLine("Model      : " + Build.MANUFACTURER + " " + Build.MODEL)
        sb.appendLine("Android    : " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
        sb.appendLine()
        sb.appendLine("--- AUTOFILL ---")
        sb.appendLine("Enabled    : " + AutofillSetup.isEnabled(ctx))
        sb.appendLine("Inline OK  : " + (Build.VERSION.SDK_INT >= 30) + " (Android 11+)")
        sb.appendLine("Service    : " + (try { Settings.Secure.getString(ctx.contentResolver, "autofill_service") } catch (e: Exception) { null } ?: "koi nahi"))
        sb.appendLine("PSL loaded : " + com.babasitaram.pro.autofill.Psl.isLoaded())
        sb.appendLine()
        sb.appendLine("--- KEYBOARD ---")
        sb.appendLine("Current    : " + (try { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) } catch (e: Exception) { null } ?: "?"))
        sb.appendLine()
        sb.appendLine("--- VAULT ---")
        sb.appendLine("Locked     : " + !VaultManager.isUnlocked)
        sb.appendLine("Entries    : " + (if (VaultManager.isUnlocked) VaultManager.getPasswords().size.toString() else "(locked)"))
        sb.appendLine()
        sb.appendLine("--- LOG (recent first) ---")
        sb.appendLine(read(ctx).ifBlank { "(koi log nahi — kisi login field par tap karke dobara aayein)" })
        return sb.toString()
    }

    fun deviceInfo(ctx: Context): String {
        val ime = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        } catch (e: Exception) { null }
        val svc = try {
            Settings.Secure.getString(ctx.contentResolver, "autofill_service")
        } catch (e: Exception) { null }
        val sb = StringBuilder()
        sb.append("App: v").append(BuildConfig.VERSION_NAME).append("\n")
        sb.append("Phone: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("Keyboard: ").append(ime ?: "?").append("\n")
        sb.append("Default autofill: ").append(svc ?: "koi nahi").append("\n")
        sb.append("BSR Pro default hai: ").append(if (AutofillSetup.isEnabled(ctx)) "HAAN" else "NAHI").append("\n")
        sb.append("Inline (keyboard chips) ke liye Android 11+: ")
            .append(if (Build.VERSION.SDK_INT >= 30) "HAAN" else "NAHI")
        return sb.toString()
    }
}
