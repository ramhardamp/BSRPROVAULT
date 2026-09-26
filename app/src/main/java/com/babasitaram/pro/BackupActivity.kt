package com.babasitaram.pro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class BackupActivity : AppCompatActivity() {

    private lateinit var btnImport: Button
    private lateinit var btnExportCsv: Button
    private lateinit var btnExportExt: Button
    private lateinit var btnExportCsv2: Button
    private lateinit var btnBack: ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var progressBackup: ProgressBar
    private lateinit var tvInfo: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val REQ_IMPORT = 1001
        private const val REQ_EXPORT_CSV = 1003
        private const val REQ_EXPORT_EXT = 1004
        private const val REQ_EXPORT_CSV2 = 1005
    }

    override fun onResume() {
        super.onResume()
        if (VaultManager.isUnlocked) AppPrefs.setLastActive(this)
        // Session expire hone par sirf unlock flow kholen; current task ko CLEAR_TASK na karein.
        if (!VaultManager.isUnlocked && !isFinishing) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_RETURN_TO_BACKUP, true)
            })
            finish()
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (!VaultManager.isUnlocked) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_RETURN_TO_SETTINGS, true)
            })
            finish()
            return
        }
        setContentView(R.layout.activity_backup)

        btnImport     = findViewById(R.id.btnImport)
        btnExportCsv  = findViewById(R.id.btnExportCsv)
        btnExportExt  = findViewById(R.id.btnExportExt)
        btnExportCsv2 = findViewById(R.id.btnExportCsv2)
        btnExportCsv2.setOnClickListener {
            ExportSecurity.requireMasterPassword(this) {
                showUniversalCsvWarning()
            }
        }
        btnExportExt.setOnClickListener {
            ExportSecurity.requireMasterPassword(this) {
                showExtensionExportOptions()
            }
        }
        btnBack       = findViewById(R.id.btnBack)
        tvStatus      = findViewById(R.id.tvStatus)
        progressBackup = findViewById(R.id.progressBackup)
        tvInfo        = findViewById(R.id.tvInfo)

        tvInfo.text = """📦 BSRPRO.Vault — BSR Pro Backup (V3)

• Naya standard format: "BSRPRO.Vault" — APK, Chrome aur Firefox extension teeno mein import hota hai
• AES-256-GCM + PBKDF2 (600,000 iterations), har backup ka naya salt/IV — Master Password zaroori
• Purani .vaultbak (aur .bsrpro) backups abhi bhi import hoti hain
• CSV (Chrome, Bitwarden, LastPass, 1Password, NordPass) aur KeePass XML bhi import hoti hai
• Har export se pehle Master Password verify hota hai; CSV/XML plain-text hote hain"""

        btnBack.setOnClickListener { finish() }

        btnExportCsv.setOnClickListener {
            ExportSecurity.requireMasterPassword(this) {
                showCsvExportWarning()
            }
        }

        btnImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/octet-stream", "application/json", "text/plain", "*/*"
                ))
            }
            startActivityForResult(intent, REQ_IMPORT)
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (res != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (req) {
            REQ_EXPORT_CSV -> doExportCsv(uri)
            REQ_EXPORT_EXT -> doExportExt(uri)
            REQ_EXPORT_CSV2 -> doExportCsv(uri, true)
            REQ_IMPORT -> promptImportPassword(uri)
        }
    }


    private fun showCsvExportWarning() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠️ CSV export")
            .setMessage("CSV file mein saare passwords PLAIN TEXT (bina encryption) hote hain. Use sirf bharosemand jagah rakhein aur kaam ke baad delete kar dein.\n\nJaari rakhein?")
            .setPositiveButton("Haan, export karo") { _, _ ->
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/csv"
                    putExtra(Intent.EXTRA_TITLE, "BSR-PRO-passwords.csv")
                }
                startActivityForResult(intent, REQ_EXPORT_CSV)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUniversalCsvWarning() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠️ Universal CSV export")
            .setMessage("Is CSV mein passwords PLAIN TEXT hote hain. Sirf login entries jayengi (name,url,username,password,note). Kaam ke baad file delete karein.\n\nJaari rakhein?")
            .setPositiveButton("Haan, export karo") { _, _ ->
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/csv"
                    putExtra(Intent.EXTRA_TITLE, "BSR-universal-passwords.csv")
                }
                startActivityForResult(intent, REQ_EXPORT_CSV2)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExtensionExportOptions() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🧩 Extension ke liye export")
            .setMessage("Extension mein import karte waqt, agar is file mein folders hon, to extension ke apne folders is file ke folders se BADAL jaate hain (extension ka apna niyam).\n\nPehle extension ka backup le lein. Folders ka jhanjhat nahi chahiye to \"Bina folders\" chunein.")
            .setPositiveButton("Folders ke saath") { _, _ -> startExtExport(true) }
            .setNegativeButton("Bina folders (safe)") { _, _ -> startExtExport(false) }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private var extWithFolders = true

    private fun startExtExport(withFolders: Boolean) {
        extWithFolders = withFolders
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "BSRPRO.Vault")
        }
        startActivityForResult(intent, REQ_EXPORT_EXT)
    }


    /**
     * FIX: Android 10+ par openOutputStream(uri) ("w") file ko truncate NAHI karta.
     * Same naam se dobara export karne par purani badi file ki poonchh bachi reh jaati thi
     * aur .vaultbak corrupt ho jaati thi (import nahi hoti thi). Ab "wt" (truncate) + size verify.
     */
    private fun writeUtf8Verified(uri: Uri, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val out = try {
            contentResolver.openOutputStream(uri, "wt")
        } catch (_: Exception) {
            contentResolver.openOutputStream(uri, "w")
        } ?: throw java.io.IOException("Selected destination open nahi hui")
        out.use { it.write(bytes); it.flush() }
        val written = contentResolver.openInputStream(uri)?.use { inp ->
            var n = 0L
            val buf = ByteArray(8192)
            while (true) { val r = inp.read(buf); if (r < 0) break; n += r }
            n
        } ?: -1L
        if (written >= 0 && written != bytes.size.toLong()) {
            throw java.io.IOException("File size match nahi hui (" + written + " != " + bytes.size + "). Dobara export karein ya naya file naam chunein.")
        }
    }

    private fun doExportExt(uri: Uri) {
        showLoading("Extension backup ban raha hai...")
        val snapshot = VaultManager.getPasswords()
        val master = VaultManager.masterForBackup()
        if (master.isEmpty()) { showError("Pehle login karein"); return }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val text = BackupManager.buildBsrProVault(master, snapshot, extWithFolders)
                    writeUtf8Verified(uri, text)
                }
                showSuccess("✓ " + snapshot.size + " entries export hui (BSRPRO.Vault)\nExtension/APK mein: Import → BSRPRO.Vault → yahi Master Password")
            } catch (e: Exception) {
                showError("Export failed: " + e.message)
            } finally {
                ExportGate.revoke()   // ek verification = ek export
            }
        }
    }

    private fun doExportCsv(uri: Uri, universal: Boolean = false) {
        showLoading("CSV export ho raha hai...")
        val snapshot = VaultManager.getPasswords()
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    writeUtf8Verified(uri, if (universal) BackupManager.exportChromeCsv(snapshot) else BackupManager.exportCsv(snapshot))
                }
                showSuccess("✓ " + snapshot.size + " entries CSV mein export hui\n⚠️ File plain text hai — kaam ke baad delete karein")
            } catch (e: Exception) {
                showError("CSV export failed: " + e.message)
            } finally {
                ExportGate.revoke()   // ek verification = ek export
            }
        }
    }

    private fun promptImportPassword(uri: Uri) {
        // Read file first to check if it's BSR format
        val layout = layoutInflater.inflate(R.layout.dialog_import_backup, null)
        val tvFileInfo = layout.findViewById<TextView>(R.id.tvFileInfo)
        val etPassword = layout.findViewById<EditText>(R.id.etImportPassword)
        val tvErr = layout.findViewById<TextView>(R.id.tvImportError)

        // Quick pre-check
        scope.launch {
            try {
                val content = withContext(Dispatchers.IO) { readUri(uri) }
                val preview = content.take(200)

                val looksExtension = (preview.trim().length > 60 &&
                    preview.trim().matches(Regex("[A-Za-z0-9+/=\\s]+"))) || preview.trim().startsWith("<")
                if (!preview.contains("BSR-VAULT") && !preview.contains("VaultX-Proprietary") &&
                    !preview.contains("BabaSitaRam Pro") && !looksExtension) {
                    handler.post {
                        tvFileInfo.text = "⚠️ Yeh BSR Pro ki backup file nahi lagti!\n\nFile check karo."
                        tvFileInfo.setTextColor(0xFFf87171.toInt())
                    }
                } else {
                    handler.post {
                        tvFileInfo.text = "✓ BSR Pro backup file detected"
                        tvFileInfo.setTextColor(0xFF34d399.toInt())
                    }
                }

                handler.post {
                    // SECURITY: every import format (BSR, .vaultbak/.json, CSV, XML)
                    // must pass the same current-master verification gate before parsing
                    // or writing anything into the vault. Do not bypass this for plaintext
                    // formats merely because they do not require decryption.
                    ExportSecurity.requireMasterPasswordForImport(this@BackupActivity) { verifiedMaster ->
                        doImport(uri, content, verifiedMaster)
                    }
                }
            } catch (e: Exception) {
                showError("File read error: ${e.message}")
            }
        }
    }

    private fun doImport(uri: Uri, content: String, masterPassword: String, backupPassword: String = masterPassword) {
        showLoading("Verifying & importing...")
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                BackupManager.importBackup(this@BackupActivity, content, backupPassword)
            }

            when (result) {
                is BackupManager.ImportResult.Success -> {
                    val data = result.data

                    // Clean reinstall/bootstrap case: a valid encrypted BSR/extension
                    // backup must be able to recreate the vault without requiring a
                    // second, manually-created master password.
                    if (!VaultManager.isSetupDone(this@BackupActivity)) {
                        if (masterPassword.isEmpty()) {
                            showError("Fresh vault ke liye pehle Master Password set/unlock karein; CSV/XML import ke liye vault unlock zaroori hai.")
                            return@launch
                        }
                        val setupOk = withContext(Dispatchers.IO) {
                            VaultManager.setupMaster(this@BackupActivity, masterPassword)
                        }
                        if (!setupOk || !VaultManager.unlock(this@BackupActivity, masterPassword)) {
                            showError("Backup verify hua, lekin naya vault initialize nahi ho paya.")
                            return@launch
                        }
                    } else if (!VaultManager.isUnlocked) {
                        showError("Backup verify hua. Pehle current vault unlock karein, phir Import dobara karein.")
                        return@launch
                    }

                    var added = 0
                    var saved = true
                    withContext(Dispatchers.IO) {
                        val fresh = VaultManager.mergeFresh(data.passwords)
                        saved = VaultManager.addAll(this@BackupActivity, fresh)
                        if (saved) added = fresh.size
                    }
                    if (!saved) {
                        showError("Import mila, lekin vault mein save nahi ho paya. Koi partial save nahi kiya gaya.")
                    } else {
                        showSuccess("✓ Import successful!\n$added new passwords added\n(Duplicates skipped)")
                    }
                }
                is BackupManager.ImportResult.Error -> {
                    showError(result.message)
                    // Backup kisi alag Master Password se bani ho (purana master / extension ka master):
                    // us backup ka password puchho aur dobara try karo.
                    if (backupPassword == masterPassword && result.message.contains("Master Password galat")) {
                        askBackupPassword(uri, content, masterPassword)
                    }
                }
            }
        }
    }


    private fun askBackupPassword(uri: Uri, content: String, masterPassword: String) {
        val et = EditText(this)
        et.hint = "Backup ka Master Password"
        et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Backup ka password")
            .setMessage("Yeh backup current Master Password se nahi khuli. Jis Master Password se yeh backup bana tha (purana ya extension wala) wo daalein.")
            .setView(et)
            .setPositiveButton("Import") { _, _ ->
                val pw = et.text.toString()
                et.text.clear()
                if (pw.isNotEmpty()) doImport(uri, content, masterPassword, pw)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun readUri(uri: Uri): String {
        val sb = StringBuilder()
        val input = contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("Selected backup file open nahi hui")
        input.use {
            BufferedReader(InputStreamReader(input)).use { reader ->
                reader.lineSequence().forEach { sb.append(it).append('\n') }
            }
        }
        val result = sb.toString()
        if (result.isBlank()) throw java.io.IOException("Backup file khali hai")
        return result
    }

    private fun showLoading(msg: String) {
        progressBackup.visibility = View.VISIBLE
        tvStatus.text = msg
        tvStatus.setTextColor(0xFF94a3b8.toInt())
        tvStatus.visibility = View.VISIBLE
        btnExportExt.isEnabled = false
        btnImport.isEnabled = false
    }

    private fun showSuccess(msg: String) {
        progressBackup.visibility = View.GONE
        tvStatus.text = msg
        tvStatus.setTextColor(0xFF34d399.toInt())
        tvStatus.visibility = View.VISIBLE
        btnExportExt.isEnabled = true
        btnImport.isEnabled = true
    }

    private fun showError(msg: String) {
        progressBackup.visibility = View.GONE
        tvStatus.text = msg
        tvStatus.setTextColor(0xFFf87171.toInt())
        tvStatus.visibility = View.VISIBLE
        btnExportExt.isEnabled = true
        btnImport.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
