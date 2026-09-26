package com.babasitaram.pro

import android.content.Intent
import android.os.Bundle
import android.text.*
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.security.SecureRandom

class AddEditActivity : AppCompatActivity() {
    companion object {
        private const val STATE_APP_PACKAGE = "state_app_package"
        private const val STATE_APP_NAME = "state_app_name"
    }

    private var editId: String? = null
    private var existing: PasswordEntry? = null

    private lateinit var etSite: EditText
    private lateinit var etUrl: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etNotes: EditText
    private lateinit var etTotp: EditText
    private lateinit var etFolder: EditText
    private lateinit var etTags: EditText
    private lateinit var etCardholder: EditText
    private lateinit var etCardNumber: EditText
    private lateinit var etCardExpiry: EditText
    private lateinit var etCardCvv: EditText
    private lateinit var etFullName: EditText
    private lateinit var etIdEmail: EditText
    private lateinit var etIdPhone: EditText
    private lateinit var etIdAddress: EditText
    private lateinit var etIdNumber: EditText
    private lateinit var etIdExpiry: EditText
    private lateinit var spinType: Spinner
    private lateinit var spinCat: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnGen: Button
    private lateinit var btnBack: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var strengthBar: ProgressBar
    private lateinit var tvStrength: TextView
    private lateinit var btnSelectApp: Button
    private lateinit var tvSelectedApp: TextView
    private lateinit var cbPrimaryApp: CheckBox

    private var appPackage: String? = null
    private var appName: String? = null

    private val cats = arrayOf("Banking", "Social", "Email", "Work", "Personal", "Shopping", "Games", "Other")
    private val types = arrayOf("login", "note", "card", "identity")
    private val typeLabels = arrayOf("🔑 Login", "📝 Secure Note", "💳 Card", "🪪 Identity")

    private fun show(id: Int, on: Boolean) {
        findViewById<View>(id)?.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun abortInit(message: String) {
        // Never close the Activity just because a defensive view check failed.
        // Keep the screen open so the user can go back normally and the failure
        // remains visible instead of looking like an unexplained app crash.
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        findViewById<TextView>(R.id.tvTitle)?.text = "Add / Edit"
    }

    override fun onResume() {
        super.onResume()
        if (VaultManager.isUnlocked) AppPrefs.setLastActive(this)
        // Session expire hone par unlock flow kholen, lekin poora task CLEAR_TASK na karein.
        if (!VaultManager.isUnlocked && !isFinishing) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_RETURN_TO_EDIT, true)
                putExtra("id", intent.getStringExtra("id"))
            })
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // appPackage/appName kisi View se bandhe nahi hain, isliye rotation/config-change par
        // khud save karna zaroori hai (EditText/Spinner apna state apne aap save kar lete hain).
        appPackage?.let { outState.putString(STATE_APP_PACKAGE, it) }
        appName?.let { outState.putString(STATE_APP_NAME, it) }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_add_edit)
        // Defensive view lookup: if the layout is stale/mismatched, never crash the Edit screen.
        etSite = findViewById(R.id.etSite) ?: run { abortInit("Edit screen load failed: etSite missing"); return }
        etUrl = findViewById(R.id.etUrl) ?: run { abortInit("Edit screen load failed: etUrl missing"); return }
        etUser = findViewById(R.id.etUsername) ?: run { abortInit("Edit screen load failed: username field missing"); return }
        etPass = findViewById(R.id.etPassword) ?: run { abortInit("Edit screen load failed: password field missing"); return }
        etNotes = findViewById(R.id.etNotes) ?: run { abortInit("Edit screen load failed: notes field missing"); return }
        etTotp = findViewById(R.id.etTotp) ?: run { abortInit("Edit screen load failed: TOTP field missing"); return }
        etFolder = findViewById(R.id.etFolder) ?: run { abortInit("Edit screen load failed: folder field missing"); return }
        etTags = findViewById(R.id.etTags) ?: run { abortInit("Edit screen load failed: tags field missing"); return }
        etCardholder = findViewById(R.id.etCardholder) ?: run { abortInit("Edit screen load failed: cardholder field missing"); return }
        etCardNumber = findViewById(R.id.etCardNumber) ?: run { abortInit("Edit screen load failed: card number field missing"); return }
        etCardExpiry = findViewById(R.id.etCardExpiry) ?: run { abortInit("Edit screen load failed: card expiry field missing"); return }
        etCardCvv = findViewById(R.id.etCardCvv) ?: run { abortInit("Edit screen load failed: card CVV field missing"); return }
        etFullName = findViewById(R.id.etFullName) ?: run { abortInit("Edit screen load failed: full name field missing"); return }
        etIdEmail = findViewById(R.id.etIdEmail) ?: run { abortInit("Edit screen load failed: identity email field missing"); return }
        etIdPhone = findViewById(R.id.etIdPhone) ?: run { abortInit("Edit screen load failed: identity phone field missing"); return }
        etIdAddress = findViewById(R.id.etIdAddress) ?: run { abortInit("Edit screen load failed: identity address field missing"); return }
        etIdNumber = findViewById(R.id.etIdNumber) ?: run { abortInit("Edit screen load failed: identity number field missing"); return }
        etIdExpiry = findViewById(R.id.etIdExpiry) ?: run { abortInit("Edit screen load failed: identity expiry field missing"); return }
        spinType = findViewById(R.id.spinnerType) ?: run { abortInit("Edit screen load failed: type spinner missing"); return }
        spinCat = findViewById(R.id.spinnerCategory) ?: run { abortInit("Edit screen load failed: category spinner missing"); return }
        btnSave = findViewById(R.id.btnSave) ?: run { abortInit("Edit screen load failed: save button missing"); return }
        btnGen = findViewById(R.id.btnGenerate) ?: run { abortInit("Edit screen load failed: generate button missing"); return }
        btnBack = findViewById(R.id.btnBack) ?: run { abortInit("Edit screen load failed: back button missing"); return }
        tvTitle = findViewById(R.id.tvTitle) ?: run { abortInit("Edit screen load failed: title missing"); return }
        strengthBar = findViewById(R.id.strengthBar) ?: run { abortInit("Edit screen load failed: strength bar missing"); return }
        tvStrength = findViewById(R.id.tvStrength) ?: run { abortInit("Edit screen load failed: strength label missing"); return }
        btnSelectApp = findViewById(R.id.btnSelectApp) ?: run { abortInit("Edit screen load failed: app picker button missing"); return }
        tvSelectedApp = findViewById(R.id.tvSelectedApp) ?: run { abortInit("Edit screen load failed: selected app label missing"); return }
        cbPrimaryApp = findViewById(R.id.cbPrimaryApp) ?: run { abortInit("Edit screen load failed: primary app option missing"); return }

        spinCat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cats)
        spinType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeLabels)
        spinType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { applyType(types.getOrElse(pos) { "login" }) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val hasEntryId = intent.hasExtra("id")
        editId = intent.getStringExtra("id")
        if (hasEntryId && editId.isNullOrBlank()) {
            abortInit("Edit entry ID missing")
            return
        }
        if (hasEntryId) {
            val id = editId!!
            val loaded = try {
                VaultManager.getById(id)
            } catch (e: Exception) {
                null
            }
            if (loaded == null) {
                abortInit("Entry nahi mila — edit screen band kiya gaya")
                return
            }
            existing = loaded
            tvTitle.text = "Edit"
            etSite.setText(loaded.site); etUrl.setText(loaded.url)
            appPackage = loaded.appPackage
            appName = loaded.appName
            cbPrimaryApp.isChecked = loaded.isPrimaryAppLogin
            updateSelectedAppText()
            etUser.setText(loaded.username); etPass.setText(loaded.password)
            etNotes.setText(loaded.notes); etTotp.setText(loaded.totp)
            etFolder.setText(loaded.folder)
            val safeTags = (loaded.tags as? List<String>).orEmpty()
            etTags.setText(safeTags.joinToString(", "))
            etCardholder.setText(loaded.cardholder); etCardNumber.setText(loaded.cardNumber)
            etCardExpiry.setText(loaded.cardExpiry); etCardCvv.setText(loaded.cardCvv)
            etFullName.setText(loaded.fullName); etIdEmail.setText(loaded.email)
            etIdPhone.setText(loaded.phone); etIdAddress.setText(loaded.address)
            etIdNumber.setText(loaded.idNumber)
            etIdExpiry.setText(loaded.idExpiry)
            spinType.setSelection(types.indexOf(loaded.type).coerceAtLeast(0))
            spinCat.setSelection(cats.indexOf(loaded.category).coerceAtLeast(0))
        }

        // Rotation/config-change recreation: appPackage/appName EditText jaisi View state mein save
        // nahi hote (koi View unse bandhi nahi hai), isliye yahan wapas restore karte hain. Warna
        // "new login" mein app select karke phone ghumane par selected app gayab ho jaata tha.
        if (s != null) {
            s.getString(STATE_APP_PACKAGE)?.let { appPackage = it }
            s.getString(STATE_APP_NAME)?.let { appName = it }
        }
        updateSelectedAppText()

        intent.getStringExtra("generated_pw")?.let { etPass.setText(it) }

        etPass.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { updateStrength(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        btnGen.setOnClickListener {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
            val rnd = SecureRandom()
            etPass.setText((1..16).map { chars[rnd.nextInt(chars.length)] }.joinToString(""))
        }

        btnSelectApp.setOnClickListener {
            AppPickerDialog(this, appPackage) { pkg, name ->
                appPackage = pkg
                appName = name
                cbPrimaryApp.isChecked = false
                updateSelectedAppText()
            }.show()
        }

        cbPrimaryApp.setOnCheckedChangeListener { _, checked ->
            if (checked && appPackage.isNullOrBlank()) {
                cbPrimaryApp.isChecked = false
                Toast.makeText(this, "Pehle app select karein", Toast.LENGTH_SHORT).show()
            }
        }

        btnSave.setOnClickListener { save() }
        btnBack.setOnClickListener { finish() }
        applyType(types.getOrElse(spinType.selectedItemPosition) { "login" })
    }

    private fun updateSelectedAppText() {
        val pkg = appPackage?.trim().orEmpty()
        val selected = pkg.isNotEmpty()
        if (selected) {
            val resolvedName = try {
                packageManager.getApplicationInfo(pkg, 0).let {
                    packageManager.getApplicationLabel(it).toString().trim()
                }.ifEmpty { null }
            } catch (_: Exception) { null }
            val displayName = appName?.trim().takeUnless { it.isNullOrEmpty() || it == pkg }
                ?: resolvedName
                ?: pkg.substringAfterLast('.').ifEmpty { pkg }
            appName = displayName
            tvSelectedApp.text = "Selected App: $displayName\n$pkg"
        } else {
            tvSelectedApp.text = "कोई app नहीं चुना"
        }
        val selectedType = types.getOrElse(spinType.selectedItemPosition) { "login" }
        cbPrimaryApp.visibility =
            if (selectedType == "login" && selected) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }
    private fun updateStrength(pw: String) {
        val sc = VaultManager.strengthScore(pw)
        strengthBar.progress = sc
        val (label, color) = when {
            sc >= 80 -> "Strong 💪" to 0xFF34d399.toInt()
            sc >= 60 -> "Good 👍" to 0xFF4f8ef7.toInt()
            sc >= 40 -> "Fair ⚠️" to 0xFFfbbf24.toInt()
            else -> "Weak ❌" to 0xFFf87171.toInt()
        }
        tvStrength.text = label; tvStrength.setTextColor(color)
        strengthBar.progressTintList = android.content.res.ColorStateList.valueOf(color)
    }

    private fun applyType(type: String) {
        val isLogin = type == "login"
        findViewById<View>(R.id.appPickerBlock)?.visibility =
            if (isLogin) View.VISIBLE else View.GONE
        cbPrimaryApp.visibility =
            if (isLogin && !appPackage.isNullOrBlank()) View.VISIBLE else View.GONE
        val login = isLogin
        show(R.id.tilUser, login)
        show(R.id.tilPass, login)
        show(R.id.btnGenerate, login)
        show(R.id.strengthBar, login)
        show(R.id.tvStrength, login)
        show(R.id.tilTotp, login)
        show(R.id.blockCard, type == "card")
        show(R.id.blockId, type == "identity")
    }

    private fun save() {
        val type = types.getOrElse(spinType.selectedItemPosition) { "login" }
        val login = type == "login"
        if (!login) cbPrimaryApp.isChecked = false
        if (cbPrimaryApp.isChecked && appPackage.isNullOrBlank()) {
            Toast.makeText(this, "Primary login ke liye app select karein", Toast.LENGTH_SHORT).show()
            return
        }
        val site = etSite.text.toString().trim()
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString()

        if (site.isEmpty()) {
            AutofillLog.add(this, "edit: entryId=${editId ?: "NEW"}, save=FAIL reason=site empty")
            Toast.makeText(this, "Naam zaroori hai", Toast.LENGTH_SHORT).show()
            return
        }
        if (login && user.isEmpty()) {
            AutofillLog.add(this, "edit: entryId=${editId ?: "NEW"}, save=FAIL reason=username empty")
            Toast.makeText(this, "Username zaroori hai", Toast.LENGTH_SHORT).show()
            return
        }
        if (login && pass.isEmpty()) {
            AutofillLog.add(this, "edit: entryId=${editId ?: "NEW"}, save=FAIL reason=password empty")
            Toast.makeText(this, "Password zaroori hai", Toast.LENGTH_SHORT).show()
            return
        }

        val totpText = if (login) etTotp.text.toString().trim() else ""
        if (totpText.isNotEmpty() && Totp.parse(totpText) == null) {
            AutofillLog.add(this, "edit: entryId=${editId ?: "NEW"}, save=FAIL reason=invalid totp")
            Toast.makeText(this, "2FA secret galat hai (base32 key ya otpauth:// link chahiye)", Toast.LENGTH_LONG).show()
            return
        }

        val base = if (editId != null) {
            VaultManager.getById(editId!!) ?: run {
                AutofillLog.add(this, "edit: entryId=$editId, save=FAIL reason=entry not found")
                Toast.makeText(this, "Entry nahi mili", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        } else {
            PasswordEntry(id = java.util.UUID.randomUUID().toString())
        }

        val card = type == "card"
        val ident = type == "identity"
        val entry = base.copy(
            site = site,
            url = etUrl.text.toString().trim(),
            appPackage = if (login) appPackage else null,
            appName = if (login) appName else null,
            isPrimaryAppLogin = login && cbPrimaryApp.isChecked,
            username = if (login) user else "",
            password = if (login) pass else "",
            notes = etNotes.text.toString().trim(),
            category = spinCat.selectedItem.toString(),
            totp = totpText,
            type = type,
            folder = etFolder.text.toString().trim(),
            folderId = if (etFolder.text.toString().trim() == base.folder) base.folderId else "",
            tags = etTags.text.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() },
            cardholder = if (card) etCardholder.text.toString().trim() else "",
            cardNumber = if (card) etCardNumber.text.toString().trim() else "",
            cardExpiry = if (card) etCardExpiry.text.toString().trim() else "",
            cardCvv = if (card) etCardCvv.text.toString().trim() else "",
            fullName = if (ident) etFullName.text.toString().trim() else "",
            email = if (ident) etIdEmail.text.toString().trim() else "",
            phone = if (ident) etIdPhone.text.toString().trim() else "",
            address = if (ident) etIdAddress.text.toString().trim() else "",
            idNumber = if (ident) etIdNumber.text.toString().trim() else "",
            idExpiry = if (ident) etIdExpiry.text.toString().trim() else ""
        )

        val isEdit = editId != null
        AutofillLog.add(this, "edit: entryId=${entry.id}, save=START mode=${if (isEdit) "UPDATE" else "ADD"}")
        val success = try {
            if (isEdit) VaultManager.update(this, entry) else VaultManager.add(this, entry)
        } catch (e: Exception) {
            AutofillLog.add(this, "edit: entryId=${entry.id}, save=FAIL exception=${e.javaClass.simpleName}")
            Toast.makeText(this, "Save failed: " + (e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
            false
        }
        if (success) {
            if (entry.isPrimaryAppLogin && !entry.appPackage.isNullOrBlank()) {
                val primaryOk = VaultManager.setPrimaryAppLogin(this, entry.id, entry.appPackage)
                if (!primaryOk) {
                    AutofillLog.add(this, "edit: entryId=${entry.id}, primary=FAIL")
                    Toast.makeText(this, "Saved, but primary login set nahi ho paya", Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
            }
            AutofillLog.add(this, "edit: entryId=${entry.id}, save=SUCCESS")
            Toast.makeText(
                this,
                if (isEdit) "Entry updated successfully" else "Password saved successfully",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        } else {
            AutofillLog.add(this, "edit: entryId=${entry.id}, save=FAIL reason=vault write failed")
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
        }
    }
}
