package com.babasitaram.pro

import android.content.Intent
import android.os.Bundle
import android.content.res.Configuration
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var switchBiometric: Switch
    private lateinit var spinnerAutoLock: Spinner
    private lateinit var spinnerClipClear: Spinner
    private lateinit var btnChangeMaster: Button
    private lateinit var btnResetVault: Button
    private lateinit var btnBack: ImageButton
    private lateinit var tvVersion: TextView

    override fun onResume() {
        super.onResume()
        if (VaultManager.isUnlocked) AppPrefs.setLastActive(this)
        refreshButtons()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Theme change is handled in-place (manifest uiMode) so AppCompat cannot
        // recreate SettingsActivity and accidentally route the user through Login.
        refreshButtons()
    }

    private val REQ_BACKUP_TREE = 2001

    private fun refreshButtons() {
        refreshAutofillButton()
        refreshChromeAutofillStatus()
        findViewById<Button>(R.id.btnSecureScreen)?.text =
            if (AppPrefs.getSecureScreen(this)) "🔒 Screenshot block: ON" else "🔓 Screenshot block: OFF"
        val ab = findViewById<Button>(R.id.btnAutoBackup)
        if (ab != null) {
            if (AutoBackup.isConfigured(this)) {
                val last = AppPrefs.getLastAutoBackup(this)
                val whenTxt = if (last > 0L)
                    java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(last))
                else "abhi tak nahi"
                ab.text = "☁️ Auto-backup: ON (last: " + whenTxt + ")"
            } else {
                ab.text = "☁️ Auto-backup folder chunein"
            }
        }
    }

    private fun autoBackupClicked() {
        if (!AutoBackup.isConfigured(this)) { pickBackupFolder(); return }
        AlertDialog.Builder(this)
            .setTitle("☁️ Auto-backup")
            .setMessage("Har badlav ke baad encrypted backup chune hue folder mein apne aap save hota hai.")
            .setPositiveButton("Abhi backup karo") { _, _ ->
                AutoBackup.runNow(this) { err ->
                    Toast.makeText(this, if (err == null) "✓ Backup ho gaya" else err, Toast.LENGTH_LONG).show()
                    refreshButtons()
                }
            }
            .setNeutralButton("Folder badlein") { _, _ -> pickBackupFolder() }
            .setNegativeButton("Band karo") { _, _ ->
                AppPrefs.setBackupTree(this, null)
                refreshButtons()
            }
            .show()
    }

    private fun pickBackupFolder() {
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            i.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            startActivityForResult(i, REQ_BACKUP_TREE)
        } catch (e: Exception) {
            Toast.makeText(this, "Folder picker nahi khul paya", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_BACKUP_TREE && res == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            val requestedFlags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    if (requestedFlags != 0) requestedFlags else
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                )
            } catch (e: Exception) {
                Log.w("BSR_Backup", "persist tree permission failed: " + e.javaClass.simpleName)
            }
            val root = runCatching { DocumentFile.fromTreeUri(this, uri) }.getOrNull()
            if (root == null || !root.isDirectory) {
                Toast.makeText(this, "Selected item folder nahi hai", Toast.LENGTH_LONG).show()
                return
            }
            if (!root.canWrite()) {
                Toast.makeText(this, "Is folder mein write permission nahi mili — doosra folder chunein", Toast.LENGTH_LONG).show()
                return
            }
            AppPrefs.setBackupTree(this, uri.toString())
            Log.i("BSR_Backup", "tree selected authority=" + (uri.authority ?: "?") + " writable=true")
            SystemLog.add(this, "Auto-backup folder selected")
            lifecycleScope.launch {
                val existing = withContext(Dispatchers.IO) { AutoBackup.readExistingBackup(this@SettingsActivity) }
                if (!existing.isNullOrBlank()) {
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Existing BSR backup mila")
                        .setMessage("Is folder mein pehle se encrypted BSR backup maujood hai.\n\nKya aap is backup ko current vault mein restore/merge karna chahte hain? Duplicate entries skip hongi.")
                        .setPositiveButton("Restore / Merge") { _, _ -> restoreSelectedBackup(existing) }
                        .setNegativeButton("Keep current vault") { _, _ ->
                            AutoBackup.runNow(this@SettingsActivity) { err ->
                                Toast.makeText(this@SettingsActivity, if (err == null) "✓ Current vault ka backup save ho gaya" else err, Toast.LENGTH_LONG).show()
                                refreshButtons()
                            }
                        }
                        .show()
                } else {
                    AutoBackup.runNow(this@SettingsActivity) { err ->
                        Toast.makeText(this@SettingsActivity, if (err == null) "✓ Auto-backup ON — pehla backup ho gaya" else err, Toast.LENGTH_LONG).show()
                        refreshButtons()
                    }
                }
            }
            refreshButtons()
        }
    }

    private fun restoreSelectedBackup(content: String) {
        val master = VaultManager.masterForBackup()
        if (master.isEmpty()) {
            Toast.makeText(this, "Pehle vault unlock karein", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                BackupManager.importBackup(this@SettingsActivity, content, master)
            }
            when (result) {
                is BackupManager.ImportResult.Success -> {
                    val fresh = VaultManager.mergeFresh(result.data.passwords)
                    val ok = withContext(Dispatchers.IO) { VaultManager.addAll(this@SettingsActivity, fresh) }
                    if (ok) {
                        AutoBackup.runNow(this@SettingsActivity) { err ->
                            Toast.makeText(this@SettingsActivity, if (err == null) "✓ Backup restore/merge complete — ${fresh.size} new entries" else "Restore hua, lekin backup update fail: $err", Toast.LENGTH_LONG).show()
                            refreshButtons()
                        }
                    } else {
                        Toast.makeText(this@SettingsActivity, "Backup read hua, lekin vault save nahi ho paya", Toast.LENGTH_LONG).show()
                    }
                }
                is BackupManager.ImportResult.Error -> Toast.makeText(this@SettingsActivity, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun refreshChromeAutofillStatus() {
        val tv = findViewById<TextView>(R.id.tvChromeAutofillStatus) ?: return
        tv.text = when (ChromeAutofill.getStatus(this)) {
            ChromeAutofill.Status.ENABLED -> "Chrome 3rd-party Autofill: ON — BSR chips allowed"
            ChromeAutofill.Status.DISABLED -> "Chrome 3rd-party Autofill: OFF — Chrome will not forward requests to BSR"
            ChromeAutofill.Status.UNAVAILABLE -> "Chrome 3rd-party Autofill status: unavailable on this Chrome version"
        }
    }

    private fun refreshAutofillButton() {
        val b = findViewById<Button>(R.id.btnAutofillSetup) ?: return
        if (AutofillSetup.isEnabled(this)) {
            b.text = "✅ Autofill ON — BSR Pro default hai"
        } else {
            b.text = "⚡ Autofill ON karein (Default set karo)"
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (!VaultManager.isUnlocked) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_RETURN_TO_SETTINGS, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
            return
        }
        setContentView(R.layout.activity_settings)

        switchBiometric   = findViewById(R.id.switchBiometric)
        spinnerAutoLock   = findViewById(R.id.spinnerAutoLock)
        spinnerClipClear  = findViewById(R.id.spinnerClipClear)
        btnChangeMaster   = findViewById(R.id.btnChangeMaster)
        btnResetVault     = findViewById(R.id.btnResetVault)
        btnBack           = findViewById(R.id.btnBack)
        tvVersion         = findViewById(R.id.tvVersion)

        tvVersion.text = "BabaSitaRam Pro v${BuildConfig.VERSION_NAME}"

        // Theme selector: System / Light / Dark. Selection is persisted and applied app-wide.
        findViewById<Button>(R.id.btnTheme)?.apply {
            text = when (AppPrefs.getTheme(this@SettingsActivity)) {
                1 -> "🎨  Theme: Light"
                2 -> "🎨  Theme: Dark"
                else -> "🎨  Theme: System"
            }
            setOnClickListener { showThemeDialog() }
        }
        btnBack.setOnClickListener { finish() }

        // Biometric
        val canBio = BioAuth.canUse(this)

        switchBiometric.isEnabled = canBio
        switchBiometric.isChecked = AppPrefs.getBiometric(this)
        if (!canBio) switchBiometric.text = "Biometric (device pe available nahi)"

        switchBiometric.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!VaultManager.isUnlocked) {
                    switchBiometric.isChecked = false
                    Toast.makeText(this, "Pehle Master Password se login karein", Toast.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }
                AppPrefs.setBiometric(this, true)
                AppPrefs.saveMasterForBio(this, VaultManager.masterForBackup())
                Toast.makeText(this, "Biometric ON", Toast.LENGTH_SHORT).show()
            } else {
                AppPrefs.setBiometric(this, false)
                AppPrefs.clearBioCache(this)
                Toast.makeText(this, "Biometric OFF — cached unlock secret clear kar diya", Toast.LENGTH_SHORT).show()
            }
        }

        // Auto-lock spinner
        val lockOpts = arrayOf("Never", "1 minute", "5 minutes", "15 minutes", "30 minutes", "1 hour")
        val lockVals = intArrayOf(0, 1, 5, 15, 30, 60)
        spinnerAutoLock.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, lockOpts)
        val curLock = AppPrefs.getAutoLock(this)
        spinnerAutoLock.setSelection(lockVals.indexOfFirst { it == curLock }.coerceAtLeast(0))
        spinnerAutoLock.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                AppPrefs.setAutoLock(this@SettingsActivity, lockVals[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Clipboard clear spinner
        val clipOpts = arrayOf("Never", "15 seconds", "30 seconds", "1 minute", "2 minutes")
        val clipVals = intArrayOf(0, 15, 30, 60, 120)
        spinnerClipClear.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, clipOpts)
        val curClip = AppPrefs.getClipClear(this)
        spinnerClipClear.setSelection(clipVals.indexOfFirst { it == curClip }.coerceAtLeast(0))
        spinnerClipClear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                AppPrefs.setClipClear(this@SettingsActivity, clipVals[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Change master password
        btnChangeMaster.setOnClickListener { showChangeMasterDialog() }

        // OTP / 2FA Manager
        findViewById<Button>(R.id.btnOtpManager)?.setOnClickListener {
            startActivity(Intent(this, OtpManagerActivity::class.java))
        }

        // Reset vault
        // Backup button
        findViewById<Button>(R.id.btnAutofillSetup)?.setOnClickListener {
            AutofillSetup.request(this)
        }
        findViewById<Button>(R.id.btnChromeAutofillSettings)?.setOnClickListener {
            ChromeAutofill.openSettings(this)
        }
        // v6.9.1: Autofill Diagnostics + System Diagnostics + Self-Test — teeno ab ek hi
        // DiagnosticsActivity mein hain, ek hi button se khulti hai.
        findViewById<Button>(R.id.btnDiag)?.setOnClickListener {
            SystemLog.add(this, "Diagnostics & Self-Test requested from Settings")
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        findViewById<Button>(R.id.btnAudit)?.setOnClickListener { Audit.show(this) }
        findViewById<Button>(R.id.btnAbout)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("About BSR Vault")
                .setMessage("BabaSitaRam Pro\nv${BuildConfig.VERSION_NAME}\n\nSecure password vault with Android Autofill support.")
                .setPositiveButton("OK", null)
                .show()
        }
        findViewById<Button>(R.id.btnHelp)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Help")
                .setMessage("Enable BSR Pro in Android Autofill settings.\n\nFor Chrome, keep Chrome 3rd-party Autofill enabled.\n\nIf Autofill does not appear, open Settings → 🩺 Diagnostics & Self-Test and check field detection, matching and vault state.")
                .setPositiveButton("OK", null)
                .show()
        }
        findViewById<Button>(R.id.btnTrash)?.setOnClickListener { TrashUi.show(this) }
        findViewById<Button>(R.id.btnSecureScreen)?.setOnClickListener {
            AppPrefs.setSecureScreen(this, !AppPrefs.getSecureScreen(this))
            Toast.makeText(this, "Badlav agli screen se laagu hoga", Toast.LENGTH_SHORT).show()
            refreshButtons()
        }
        findViewById<Button>(R.id.btnAutoBackup)?.setOnClickListener { autoBackupClicked() }
        findViewById<Button>(R.id.btnBackupRestore)?.setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
        }

        btnResetVault.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Reset Vault")
                .setMessage("Saara data permanently delete ho jayega. Koi backup nahi bachega.\n\nKya aap 100% sure hain?")
                .setPositiveButton("RESET KARO") { _, _ ->
                    VaultManager.resetAll(this)
                    AppPrefs.clearBioCache(this)
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        putExtra(LoginActivity.EXTRA_FORCE_SETUP, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    })
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showThemeDialog() {
        val options = arrayOf("System default", "Light", "Dark")
        val current = AppPrefs.getTheme(this)
        AlertDialog.Builder(this)
            .setTitle("Choose Theme")
            .setSingleChoiceItems(options, current) { dialog, which ->
                AppPrefs.setTheme(this, which)
                val mode = when (which) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                Log.i("BSR_Theme", "user selected pref=" + which + " appCompatMode=" + mode)
                SystemLog.add(this@SettingsActivity, "Theme changed to " + when (which) {
                    1 -> "Light"
                    2 -> "Dark"
                    else -> "System"
                })
                AppCompatDelegate.setDefaultNightMode(mode)
                // AppCompat automatically recreates started activities when the
                // night mode changes; avoid a second manual recreation.
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showChangeMasterDialog() {
        val layout = layoutInflater.inflate(R.layout.dialog_change_master, null)
        val etOld  = layout.findViewById<EditText>(R.id.etOldMaster)
        val etNew  = layout.findViewById<EditText>(R.id.etNewMaster)
        val etConf = layout.findViewById<EditText>(R.id.etConfirmMaster)
        val tvErr  = layout.findViewById<TextView>(R.id.tvMasterError)

        AlertDialog.Builder(this)
            .setTitle("Change Master Password")
            .setView(layout)
            .setPositiveButton("Change") { _, _ ->  }
            .setNegativeButton("Cancel", null)
            .create().also { dlg ->
                dlg.show()
                dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val old  = etOld.text.toString()
                    val newP = etNew.text.toString()
                    val conf = etConf.text.toString()
                    val positive = dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                    tvErr.visibility = android.view.View.GONE
                    fun fail(msg: String) { tvErr.text = msg; tvErr.visibility = android.view.View.VISIBLE }
                    when {
                        old.isEmpty()   -> fail("Purana password dalein")
                        newP.length < 6 -> fail("Naya password 6+ chars ka hona chahiye")
                        newP != conf    -> fail("Naya password match nahi kar raha")
                        else -> {
                            // PBKDF2 (verify + naya vault encrypt) bhaari hai — main thread par nahi (ANR se bachne ke liye).
                            positive.isEnabled = false
                            lifecycleScope.launch {
                                val code = withContext(Dispatchers.Default) {
                                    if (!VaultManager.verifyMaster(this@SettingsActivity, old)) 1
                                    else if (VaultManager.changeMaster(this@SettingsActivity, newP)) 0
                                    else 2
                                }
                                positive.isEnabled = true
                                when (code) {
                                    0 -> {
                                        AppPrefs.saveMasterForBio(this@SettingsActivity, newP)
                                        Toast.makeText(this@SettingsActivity, "✓ Master Password change ho gaya", Toast.LENGTH_SHORT).show()
                                        dlg.dismiss()
                                    }
                                    1 -> fail("Purana password galat hai")
                                    else -> fail("Master password change nahi ho paya")
                                }
                            }
                        }
                    }
                }
            }
    }
}
