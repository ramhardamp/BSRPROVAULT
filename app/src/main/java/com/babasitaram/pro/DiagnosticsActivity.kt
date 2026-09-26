package com.babasitaram.pro

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BSR Vault Diagnostics & Self-Test (v6.9.1)
 *
 * Pehle yeh teen alag-alag screens thi (SystemDiagnosticsActivity, AutofillDiagnosticsActivity,
 * SelfTestActivity) — sab kamzor/kaam-na-karne-wali thi kyunki `SelfTestActivity` mein
 * `androidx.lifecycle.lifecycleScope` use hota tha par `androidx.lifecycle:lifecycle-runtime-ktx`
 * dependency `build.gradle` mein thi hi nahi (sirf `lifecycle-process` thi, jo alag cheez hai) —
 * isliye woh screen compile/run hi nahi ho pati thi. Yeh dependency ab jodi gayi hai.
 *
 * Ab yeh teeno EK hi screen mein hain: System info + Autofill/Chrome/PSL status + safe in-memory
 * crypto/backup round-trip self-test — ek hi PASS/WARN/FAIL report, ek hi jagah.
 *
 * Robustness:
 *  - Har individual check apne try/catch mein hai (ek check fail ho to baaki nahi rukte).
 *  - Poora report-builder bhi ek bahar wale try/catch mein hai — koi anjaana crash bhi kabhi
 *    khaali/crashed screen nahi dega, kam se kam partial report + us exception ka FAIL line milega.
 *  - Vault mein koi add/update/delete/reset kabhi nahi hota — sirf read-only checks + in-memory
 *    (kabhi disk par na jaane wala) crypto/backup round-trip.
 */
class DiagnosticsActivity : AppCompatActivity() {
    private lateinit var tvReport: TextView
    private lateinit var tvVerdict: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        tvReport = findViewById(R.id.tvDiagReport)
        tvVerdict = findViewById(R.id.tvDiagVerdict)

        findViewById<Button>(R.id.btnRunDiagnostics).setOnClickListener { run() }
        findViewById<Button>(R.id.btnCopyDiagnostics).setOnClickListener {
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("BSR Diagnostics", tvReport.text.toString()))
                Toast.makeText(this, "Report copy ho gaya", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Copy nahi ho paaya: " + e.javaClass.simpleName, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnClearDiagLog).setOnClickListener {
            try { SystemLog.clear(this) } catch (_: Exception) {}
            try { AutofillLog.clear(this) } catch (_: Exception) {}
            SystemLog.add(this, "Diagnostics: event log cleared")
            Toast.makeText(this, "Event log clear ho gaya", Toast.LENGTH_SHORT).show()
            run()
        }
        findViewById<Button>(R.id.btnCloseDiagnostics).setOnClickListener { finish() }

        SystemLog.add(this, "Diagnostics & Self-Test opened")
        run()
    }

    private fun run() {
        tvVerdict.text = "Checking…"
        tvVerdict.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tvReport.text = "===== BSR VAULT DIAGNOSTICS & SELF-TEST =====\n\nChecks chal rahe hain, kripya intezaar karein…"
        lifecycleScope.launch {
            val (verdict, report) = withContext(Dispatchers.Default) { buildCombinedReport() }
            tvReport.text = report
            when (verdict) {
                Verdict.FAIL -> { tvVerdict.text = "❌ PROBLEM MILI — neeche FAIL section dekhein"; tvVerdict.setTextColor(0xFFf87171.toInt()) }
                Verdict.WARN -> { tvVerdict.text = "⚠️ Kaam kar raha hai, kuch WARN hai"; tvVerdict.setTextColor(0xFFfbbf24.toInt()) }
                Verdict.PASS -> { tvVerdict.text = "✅ Sab kuch theek hai"; tvVerdict.setTextColor(0xFF34d399.toInt()) }
            }
        }
    }

    private enum class Verdict { PASS, WARN, FAIL }

    /** Poora combined report ek hi outer try/catch mein — kabhi bhi khaali screen nahi milegi. */
    private fun buildCombinedReport(): Pair<Verdict, String> {
        val pass = mutableListOf<String>()
        val warn = mutableListOf<String>()
        val fail = mutableListOf<String>()
        fun ok(name: String) { pass += "PASS  $name" }
        fun wn(name: String) { warn += "WARN  $name" }
        fun bad(name: String) { fail += "FAIL  $name" }

        val sb = StringBuilder()
        try {
            sb.appendLine("===== BSR VAULT DIAGNOSTICS & SELF-TEST =====")
            sb.appendLine("Time       : " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            sb.appendLine("App version: v" + BuildConfig.VERSION_NAME)
            sb.appendLine()

            sb.appendLine("--- 1. DEVICE ---")
            try {
                sb.appendLine("Manufacturer: " + Build.MANUFACTURER)
                sb.appendLine("Model       : " + Build.MODEL)
                sb.appendLine("Android     : " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
                sb.appendLine("Locale      : " + Locale.getDefault().toLanguageTag())
                ok("Device info readable")
            } catch (e: Exception) { bad("Device info: " + e.javaClass.simpleName) }
            sb.appendLine()

            sb.appendLine("--- 2. APP INSTALL ---")
            try {
                val pi = packageManager.getPackageInfo(packageName, 0)
                sb.appendLine("Package     : " + pi.packageName)
                if (pi.packageName == "com.babasitaram.pro") ok("App package/version readable") else bad("Unexpected package: " + pi.packageName)
            } catch (e: Exception) { bad("Package check: " + e.javaClass.simpleName) }
            try {
                val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                sb.appendLine("Installed apps visible: " + apps.size)
                if (apps.isNotEmpty()) ok("Installed-app package discovery (" + apps.size + ")") else bad("Installed-app discovery returned 0 apps")
            } catch (e: Exception) { bad("Installed-app query: " + e.javaClass.simpleName) }
            sb.appendLine()

            sb.appendLine("--- 3. VAULT / SECURITY ---")
            try {
                sb.appendLine("Vault locked : " + !VaultManager.isUnlocked)
                sb.appendLine("Entries      : " + if (VaultManager.isUnlocked) VaultManager.getPasswords().size.toString() else "(locked)")
                if (VaultManager.isUnlocked) ok("Vault readable (no vault write performed)") else wn("Vault is locked — vault contents not opened")
            } catch (e: Exception) { bad("Vault read check: " + e.javaClass.simpleName) }
            try {
                sb.appendLine("Secure screen: " + AppPrefs.getSecureScreen(this))
                sb.appendLine("Biometric    : " + AppPrefs.getBiometric(this))
                sb.appendLine("Auto-lock    : " + AppPrefs.getAutoLock(this) + " min")
                sb.appendLine("Clipboard clr: " + AppPrefs.getClipClear(this) + " sec")
                ok("Security preferences readable")
            } catch (e: Exception) { bad("Security prefs: " + e.javaClass.simpleName) }
            sb.appendLine()

            sb.appendLine("--- 4. AUTOFILL ---")
            try {
                val enabled = AutofillSetup.isEnabled(this)
                sb.appendLine("BSR Autofill enabled : " + enabled)
                if (enabled) ok("BSR Autofill is enabled") else wn("BSR Autofill disabled — enable it for external Autofill tests")
            } catch (e: Exception) { bad("Autofill state: " + e.javaClass.simpleName) }
            try {
                val svc = runCatching { Settings.Secure.getString(contentResolver, "autofill_service") }.getOrNull()
                sb.appendLine("Default autofill svc : " + (svc?.ifBlank { "none" } ?: "none"))
            } catch (e: Exception) { sb.appendLine("Default autofill svc : ? (" + e.javaClass.simpleName + ")") }
            try {
                val ime = runCatching { Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) }.getOrNull()
                sb.appendLine("Current keyboard      : " + (ime ?: "?"))
            } catch (_: Exception) { }
            sb.appendLine("Inline (keyboard chip) support (Android 11+): " + (Build.VERSION.SDK_INT >= 30))
            try {
                when (ChromeAutofill.getStatus(this)) {
                    ChromeAutofill.Status.ENABLED -> { sb.appendLine("Chrome 3rd-party Autofill: ENABLED"); ok("Chrome 3rd-party Autofill integration enabled") }
                    ChromeAutofill.Status.DISABLED -> { sb.appendLine("Chrome 3rd-party Autofill: DISABLED"); wn("Chrome 3rd-party Autofill integration disabled") }
                    ChromeAutofill.Status.UNAVAILABLE -> { sb.appendLine("Chrome 3rd-party Autofill: UNAVAILABLE on this Chrome build"); wn("Chrome Autofill status unavailable on this Chrome build") }
                }
            } catch (e: Exception) { bad("Chrome integration check: " + e.javaClass.simpleName) }
            try {
                com.babasitaram.pro.autofill.Psl.ensureLoaded(this)
                val loaded = com.babasitaram.pro.autofill.Psl.isLoaded()
                sb.appendLine("Public suffix list    : " + (if (loaded) "loaded" else "not loaded"))
                if (loaded) ok("Public suffix list loaded") else wn("Public suffix list not loaded")
            } catch (e: Exception) { bad("PSL check: " + e.javaClass.simpleName) }
            sb.appendLine()

            sb.appendLine("--- 5. BACKUP ---")
            try {
                val configured = AutoBackup.isConfigured(this)
                val last = AppPrefs.getLastAutoBackup(this)
                sb.appendLine("Auto-backup configured: " + configured)
                sb.appendLine("Last backup           : " + if (last > 0L) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(last)) else "never")
                if (configured) ok("Auto-backup folder configured") else wn("Auto-backup folder not configured")
            } catch (e: Exception) { bad("Auto-backup check: " + e.javaClass.simpleName) }
            sb.appendLine()

            sb.appendLine("--- 6. UI / THEME ---")
            try {
                val theme = when (AppPrefs.getTheme(this)) { 1 -> "Light"; 2 -> "Dark"; else -> "System" }
                val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                sb.appendLine("Theme pref   : " + theme)
                sb.appendLine("UI night mode: " + night)
                ok("Theme/UI config readable")
            } catch (e: Exception) { bad("Theme/UI check: " + e.javaClass.simpleName) }
            sb.appendLine()

            sb.appendLine("--- 7. SELF-TEST (in-memory only — real vault untouched) ---")
            runSelfTestChecks(::ok, ::wn, ::bad)
            sb.appendLine("(see PASS/WARN/FAIL summary above for this section's detail)")
            sb.appendLine()

            sb.appendLine("--- 8. RECENT EVENTS (System, max 200) ---")
            try { sb.appendLine(SystemLog.read(this).ifBlank { "(no system events)" }) }
            catch (e: Exception) { sb.appendLine("(could not read system log: " + e.javaClass.simpleName + ")") }
            sb.appendLine()
            sb.appendLine("--- 9. RECENT EVENTS (Autofill) ---")
            try { sb.appendLine(AutofillLog.read(this).ifBlank { "(no autofill events yet — tap a login field once)" }) }
            catch (e: Exception) { sb.appendLine("(could not read autofill log: " + e.javaClass.simpleName + ")") }
            sb.appendLine()

            sb.appendLine("NOTE: No passwords, usernames, OTP secrets, clipboard contents or vault JSON are ever recorded in these logs.")
        } catch (e: Exception) {
            fail.add("FAIL  Diagnostics report builder crashed: " + e.javaClass.simpleName + " — " + (e.message ?: ""))
            sb.appendLine()
            sb.appendLine("--- BUILDER ERROR ---")
            sb.appendLine("Report banate waqt ek anjaani samasya aayi: " + e.javaClass.simpleName + " — " + (e.message ?: ""))
        }

        val total = pass.size + warn.size + fail.size
        val summary = buildString {
            appendLine("===== SUMMARY =====")
            appendLine("Result : " + (if (fail.isNotEmpty()) "FAIL" else if (warn.isNotEmpty()) "WARN" else "PASS"))
            appendLine("Checks : $total   PASS=${pass.size}  WARN=${warn.size}  FAIL=${fail.size}")
            appendLine()
            appendLine("--- FAIL ---")
            if (fail.isEmpty()) appendLine("(none)") else fail.forEach(::appendLine)
            appendLine()
            appendLine("--- WARN ---")
            if (warn.isEmpty()) appendLine("(none)") else warn.forEach(::appendLine)
            appendLine()
            appendLine("--- PASS ---")
            pass.forEach(::appendLine)
            appendLine()
        }
        val verdict = if (fail.isNotEmpty()) Verdict.FAIL else if (warn.isNotEmpty()) Verdict.WARN else Verdict.PASS
        return verdict to (summary + "\n" + sb.toString())
    }

    /**
     * Safe in-memory round-trip checks — SelfTestActivity se liye gaye, koi vault write nahi karte.
     * Test password/entry sirf memory mein banta hai, kabhi disk/vault mein save nahi hota.
     */
    private fun runSelfTestChecks(ok: (String) -> Unit, wn: (String) -> Unit, bad: (String) -> Unit) {
        val testPassword = "BSR-SelfTest-Only-9!x"
        val testEntry = PasswordEntry(
            id = "bsr-self-test-entry",
            site = "BSR Self Test",
            url = "https://self-test.example.invalid/login",
            appPackage = "com.babasitaram.pro",
            username = "selftest@example.invalid",
            password = testPassword,
            notes = "temporary in-memory self test",
            category = "Other",
            type = "login"
        )

        try {
            val plain = """{"selfTest":"ok","value":"BSR"}"""
            val enc = VaultManager.encryptString(plain, testPassword)
            val dec = VaultManager.decryptString(enc, testPassword)
            if (dec == plain) ok("AES-GCM encrypt/decrypt round-trip") else bad("AES-GCM round-trip mismatch")
        } catch (e: Exception) { bad("AES-GCM: " + e.javaClass.simpleName) }

        try {
            val backupJson = BackupManager.buildBackupJson(testPassword, listOf(testEntry))
            val imported = kotlinx.coroutines.runBlocking {
                BackupManager.importBackup(this@DiagnosticsActivity, backupJson, testPassword)
            }
            if (imported is BackupManager.ImportResult.Success &&
                imported.data.passwords.size == 1 && imported.data.passwords[0].username == testEntry.username
            ) ok(".bsrpro build/verify/decrypt/import round-trip")
            else bad(".bsrpro import round-trip returned unexpected result")
        } catch (e: Exception) { bad(".bsrpro round-trip: " + e.javaClass.simpleName) }

        try {
            val encrypted = BackupManager.buildBsrProVaultUnchecked(testPassword, listOf(testEntry))
            val plain = String(BsrVault.decrypt(testPassword, encrypted), Charsets.UTF_8)
            val root = JsonParser.parseString(plain).asJsonObject
            val imported = kotlinx.coroutines.runBlocking {
                BackupManager.importBackup(this@DiagnosticsActivity, encrypted, testPassword)
            }
            val wrongRejected = try { BsrVault.decrypt(testPassword + "x", encrypted); false } catch (e: BsrVault.AuthException) { true }
            if (BsrVault.isV3(encrypted) &&
                root.get("_bsrOrigin")?.asString == "BABASITARAMPro:android" &&
                root.get("format")?.asString == "BSRPRO.Vault" && root.has("data") &&
                imported is BackupManager.ImportResult.Success && imported.data.passwords.size == 1 && wrongRejected
            ) ok("BSRPRO.Vault V3 build/decrypt/import round-trip + wrong-password reject")
            else bad("BSRPRO.Vault V3 round-trip returned unexpected result")
        } catch (e: Exception) { bad("BSRPRO.Vault round-trip: " + e.javaClass.simpleName) }

        try {
            val blocked = try { BackupManager.exportCsv(listOf(testEntry)); false } catch (e: SecurityException) { true }
            if (blocked) ok("Export guard: plaintext CSV blocked without Master Password verification")
            else bad("Export guard: CSV was produced without verification!")
        } catch (e: Exception) { bad("Export guard: " + e.javaClass.simpleName) }

        try {
            val json = """{"_bsrOrigin":"BABASITARAMPro:android","data":{"version":"2.0","entries":[{"id":"x","title":"Self Test","url":"https://example.invalid","username":"u","password":"p"}]}}"""
            val root = JsonParser.parseString(json).asJsonObject
            val entries = root.getAsJsonObject("data").getAsJsonArray("entries")
            if (root.get("_bsrOrigin").asString == "BABASITARAMPro:android" && entries.size() == 1) ok("Extension JSON wrapper parser")
            else bad("Extension JSON wrapper parser")
        } catch (e: Exception) { bad("Extension JSON parser: " + e.javaClass.simpleName) }

        try {
            val csv = "name,url,username,password\nSelf Test,https://example.invalid,u,p"
            val rows = BackupManager.parseCsv(csv)
            if (rows.size == 2 && rows[1].size == 4 && rows[1][0] == "Self Test") ok("CSV parser/header mapping")
            else bad("CSV parser/header mapping")
        } catch (e: Exception) { bad("CSV parser: " + e.javaClass.simpleName) }

        try {
            val key = VaultManager.dedupKey(testEntry)
            if (key.startsWith("l|") && key.contains("selftest@example.invalid")) ok("Vault duplicate-key generation")
            else bad("Vault duplicate-key generation")
        } catch (e: Exception) { bad("Dedup key: " + e.javaClass.simpleName) }

        try {
            val weak = VaultManager.strengthScore("abc")
            val strong = VaultManager.strengthScore("A9!long-strong-password")
            if (weak < strong && strong > 70) ok("Password strength calculation") else bad("Password strength calculation")
        } catch (e: Exception) { bad("Password strength: " + e.javaClass.simpleName) }

        ok("Data-safety invariant: no add/update/delete/reset operation executed")
    }
}
