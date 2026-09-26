package com.babasitaram.pro

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.babasitaram.pro.autofill.BSRAutofillService
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RETURN_TO_SETTINGS = "return_to_settings"
    }

    // Setup
    private lateinit var layoutSetup: LinearLayout
    private lateinit var etPhone: EditText
    private lateinit var etMasterSetup: EditText
    private lateinit var etMasterConfirm: EditText
    private lateinit var btnSetup: Button
    private lateinit var tvSetupError: TextView
    private lateinit var progressSetup: ProgressBar

    // Login
    private lateinit var layoutLogin: LinearLayout
    private lateinit var etMasterLogin: EditText
    private lateinit var btnLogin: Button
    private lateinit var layoutBiometric: LinearLayout
    private lateinit var btnBiometric: Button
    private lateinit var tvBioHint: TextView
    private lateinit var tvLoginError: TextView
    private lateinit var progressLogin: ProgressBar
    private lateinit var tvForgot: TextView

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Setup views
        layoutSetup     = findViewById(R.id.layoutSetup)
        etPhone         = findViewById(R.id.etPhone)
        etMasterSetup   = findViewById(R.id.etMasterSetup)
        etMasterConfirm = findViewById(R.id.etMasterConfirm)
        btnSetup        = findViewById(R.id.btnSetup)
        tvSetupError    = findViewById(R.id.tvSetupError)
        progressSetup   = findViewById(R.id.progressSetup)

        // Login views
        layoutLogin     = findViewById(R.id.layoutLogin)
        etMasterLogin   = findViewById(R.id.etMasterLogin)
        btnLogin        = findViewById(R.id.btnLogin)
        layoutBiometric = findViewById(R.id.layoutBiometric)
        btnBiometric    = findViewById(R.id.btnBiometric)
        tvBioHint       = findViewById(R.id.tvBioHint)
        tvLoginError    = findViewById(R.id.tvLoginError)
        progressLogin   = findViewById(R.id.progressLogin)
        tvForgot        = findViewById(R.id.tvForgot)

        btnSetup.setOnClickListener { doSetup() }
        btnLogin.setOnClickListener { doLogin() }
        btnBiometric.setOnClickListener { showBiometric() }
        tvForgot.setOnClickListener { showForgotDialog() }

        etMasterLogin.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { doLogin(); true } else false
        }
        etMasterConfirm.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { doSetup(); true } else false
        }

        if (VaultManager.isSetupDone(this)) showLoginScreen()
        else showSetupScreen()
    }

    // ── SETUP ──
    private fun showSetupScreen() {
        layoutSetup.visibility = View.VISIBLE
        layoutLogin.visibility = View.GONE
    }

    private fun doSetup() {
        val phone   = etPhone.text.toString().trim()
        val master  = etMasterSetup.text.toString()
        val confirm = etMasterConfirm.text.toString()
        tvSetupError.visibility = View.GONE

        when {
            phone.isNotEmpty() && phone.length < 10 -> showErr(tvSetupError, "Mobile number 10 digit ka hona chahiye (ya khali chhodein)")
            master.length < 6          -> showErr(tvSetupError, "Master Password 6+ characters ka hona chahiye")
            master != confirm          -> showErr(tvSetupError, "Dono passwords match nahi kar rahe")
            else -> {
                setSetupUI(false)
                Executors.newSingleThreadExecutor().execute {
                    val ok = VaultManager.setupMaster(this, master)
                    val unlocked = if (ok) VaultManager.unlock(this, master) else false
                    handler.post {
                        setSetupUI(true)
                        if (ok && unlocked) {
                            AppPrefs.saveMasterForBio(this, master)
                            AppPrefs.setLastActive(this)
                            goMain()
                        } else showErr(tvSetupError, "Setup failed — dobara try karein")
                    }
                }
            }
        }
    }

    private fun setSetupUI(enabled: Boolean) {
        btnSetup.isEnabled = enabled
        btnSetup.text = if (enabled) "CREATE VAULT" else "Creating..."
        progressSetup.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    // ── LOGIN ──
    private fun showLoginScreen() {
        layoutSetup.visibility = View.GONE
        layoutLogin.visibility = View.VISIBLE
        tvLoginError.visibility = View.GONE

        val bioEnabled = AppPrefs.getBiometric(this)
        val hasCached  = AppPrefs.getMasterForBio(this) != null
        val canBio     = canUseBiometric()

        if (bioEnabled && hasCached && canBio) {
            layoutBiometric.visibility = View.VISIBLE
            tvBioHint.text = "Fingerprint ya Face se unlock karein"
            handler.postDelayed({ showBiometric() }, 300)
        } else {
            layoutBiometric.visibility = View.GONE
        }
    }

    private fun doLogin() {
        val master = etMasterLogin.text.toString()
        if (master.isEmpty()) { showErr(tvLoginError, "Master Password dalein"); return }
        val lockMs = AppPrefs.lockRemainingMs(this)
        if (lockMs > 0L) {
            showErr(tvLoginError, "🔒 Bahut galat koshishein. " + ((lockMs + 999L) / 1000L) + " second baad try karein.")
            return
        }
        hideKeyboard()
        setLoginUI(false)

        Executors.newSingleThreadExecutor().execute {
            val ok = try { VaultManager.unlock(this, master) } catch (e: Exception) { false }
            handler.post {
                setLoginUI(true)
                if (ok) {
                    AppPrefs.clearFailures(this)
                    AppPrefs.saveMasterForBio(this, master)
                    AppPrefs.setLastActive(this)
                    goMain()
                } else {
                    etMasterLogin.text.clear()
                    val waitMs = AppPrefs.recordFailure(this)
                    if (waitMs > 0L) {
                        showErr(tvLoginError, "❌ Galat password. " + (waitMs / 1000L) + " second ka lock lag gaya.")
                    } else {
                        showErr(tvLoginError, "❌ Wrong Master Password — dobara try karein (" + AppPrefs.failCount(this) + " galat)")
                    }
                    etMasterLogin.requestFocus()
                    // Shake animation
                    etMasterLogin.animate().translationX(12f).setDuration(50)
                        .withEndAction { etMasterLogin.animate().translationX(-12f).setDuration(50)
                            .withEndAction { etMasterLogin.animate().translationX(0f).setDuration(50).start() }.start() }.start()
                }
            }
        }
    }

    private fun setLoginUI(enabled: Boolean) {
        btnLogin.isEnabled = enabled
        btnLogin.text = if (enabled) "UNLOCK VAULT" else "Verifying..."
        etMasterLogin.isEnabled = enabled
        progressLogin.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    // ── BIOMETRIC ──
    private fun canUseBiometric(): Boolean = BioAuth.canUse(this)

    private fun showBiometric() {
        if (isFinishing || isDestroyed) return
        val cached = AppPrefs.getMasterForBio(this) ?: run {
            layoutBiometric.visibility = View.GONE; return
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                setLoginUI(false)
                Executors.newSingleThreadExecutor().execute {
                    val ok = try { VaultManager.unlock(this@LoginActivity, cached) } catch (e: Exception) { false }
                    handler.post {
                        setLoginUI(true)
                        if (ok) { AppPrefs.setLastActive(this@LoginActivity); goMain() }
                        else { layoutBiometric.visibility = View.GONE; showErr(tvLoginError, "Biometric failed — Master Password dalein") }
                    }
                }
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                if (code != BiometricPrompt.ERROR_USER_CANCELED &&
                    code != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    code != BiometricPrompt.ERROR_CANCELED)
                    showErr(tvLoginError, "Biometric error: $msg")
            }
            override fun onAuthenticationFailed() {}
        }
        try {
            BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
                .authenticate(BioAuth.promptInfo("BabaSitaRam Pro", "Vault unlock karein"))
        } catch (e: Exception) { layoutBiometric.visibility = View.GONE }
    }

    // ── FORGOT ──
    private fun showForgotDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Master Password bhool gaye?")
            .setMessage("Master Password reset karne ke liye vault erase karni hogi. Saara data delete ho jayega.\n\nKya aap sure hain?")
            .setPositiveButton("Vault Reset Karo") { _, _ ->
                VaultManager.resetAll(this)
                AppPrefs.clearBioCache(this)
                showSetupScreen()
                Toast.makeText(this, "Vault reset ho gaya", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun goMain() {
        // Autofill se aaye hain? To MainActivity nahi, seedha fill result wapas do
        if (intent.getBooleanExtra(BSRAutofillService.EXTRA_FROM_AUTOFILL, false)) {
            returnAutofillResult()
            return
        }
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_SETTINGS, false)) {
            startActivity(Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
            return
        }
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun returnAutofillResult() {
        val uIds: ArrayList<AutofillId> =
            intent.getParcelableArrayListExtra<AutofillId>(BSRAutofillService.EXTRA_USERNAME_IDS)
                ?: ArrayList()
        val pIds: ArrayList<AutofillId> =
            intent.getParcelableArrayListExtra<AutofillId>(BSRAutofillService.EXTRA_PASSWORD_IDS)
                ?: ArrayList()
        val webDomain = intent.getStringExtra(BSRAutofillService.EXTRA_WEB_DOMAIN) ?: ""
        val appId = intent.getStringExtra(BSRAutofillService.EXTRA_APP_ID) ?: ""

        val inlineReq: Any? =
            if (android.os.Build.VERSION.SDK_INT >= 30)
                intent.getParcelableExtra<android.os.Parcelable>(BSRAutofillService.EXTRA_INLINE_REQ)
            else null
        val response = BSRAutofillService.buildResponse(this, uIds, pIds, webDomain, appId, inlineReq)
        AutofillLog.add(this, "unlock ke baad: response bheja=" + (response != null) + " inline=" + (inlineReq != null))
        if (response != null) {
            val result = Intent()
            result.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)
            setResult(RESULT_OK, result)
            if (BSRAutofillService.findMatches(webDomain, appId).isEmpty()) {
                Toast.makeText(this, "Match nahi mila — \"Vault se chunein\" chip use karein", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Match nahi mila — \"Vault se chunein\" chip use karein", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    private fun showErr(tv: TextView, msg: String) {
        tv.text = msg; tv.visibility = View.VISIBLE
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }
}
