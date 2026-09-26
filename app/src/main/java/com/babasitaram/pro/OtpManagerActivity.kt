package com.babasitaram.pro

import android.content.res.ColorStateList
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * OTP / 2FA Manager (v6.8.1) — Google Authenticator jaisa: sab 2FA accounts ek list mein,
 * "Issuer: account" label, badi 3+3 digit code, live countdown, tap = copy.
 *
 * Vault ka koi data/format nahi badla: yahan sirf PasswordEntry.totp padha jaata hai
 * (aur "2FA jodein" se VaultManager.update() ke through likha jaata hai).
 */
class OtpManagerActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var tvEmpty: TextView
    private lateinit var tvCount: TextView
    private lateinit var adapter: OtpAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false
    private val tick = object : Runnable {
        override fun run() {
            if (!ticking) return
            if (!VaultManager.isUnlocked) { ticking = false; goLogin(); return }
            adapter.refreshTime()
            // Har second ki boundary par update — code/timer Authenticator ke saath sync rahe.
            val now = System.currentTimeMillis()
            handler.postDelayed(this, 1000L - (now % 1000L) + 5L)
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_otp)
        if (!VaultManager.isUnlocked) { goLogin(); return }

        rv       = findViewById(R.id.rvOtp)
        etSearch = findViewById(R.id.etOtpSearch)
        tvEmpty  = findViewById(R.id.tvOtpEmpty)
        tvCount  = findViewById(R.id.tvOtpCount)

        adapter = OtpAdapter(
            onCopy = { item -> copyCode(item) },
            onEdit = { item ->
                startActivity(Intent(this, AddEditActivity::class.java).putExtra("id", item.id))
            }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.itemAnimator = null            // har second update par flicker/fade nahi
        rv.adapter = adapter

        etSearch.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { reload() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        findViewById<ImageButton>(R.id.btnOtpBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnOtpAdd).setOnClickListener { pickEntryForTotp() }
    }

    override fun onResume() {
        super.onResume()
        if (!VaultManager.isUnlocked) { goLogin(); return }
        reload()
        if (!ticking) {
            ticking = true
            handler.post(tick)
        }
    }

    override fun onPause() {
        super.onPause()
        ticking = false
        handler.removeCallbacks(tick)
    }

    /** Vault se saari valid 2FA entries (naye/purane dono format) nikaal kar list refresh. */
    private fun reload() {
        val q = etSearch.text.toString().trim().lowercase()
        val all = ArrayList<OtpItem>()
        for (e in VaultManager.getPasswords()) {
            val secret: String? = e.totp            // purani/malformed entry mein null ho sakta hai
            if (secret.isNullOrBlank()) continue
            val params = Totp.parse(secret) ?: continue
            val label = OtpDisplay.label(e.site, e.url, e.username, e.email, e.mobile, e.fullName, secret)
            all.add(OtpItem(e.id, label.issuer, label.account, label.text, params))
        }
        val shown = all
            .filter { q.isEmpty() || it.label.lowercase().contains(q) }
            .sortedWith(compareBy<OtpItem>({ it.issuer.lowercase() }, { it.account.lowercase() }))
        adapter.submit(shown)
        tvCount.text = if (q.isEmpty()) "${all.size} accounts" else "${shown.size}/${all.size}"
        tvEmpty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun copyCode(item: OtpItem) {
        val code = item.currentCode()
        if (code.isEmpty()) { toast("Code ban nahi paaya — secret check karein"); return }
        SecureClip.copy(this, "2FA code", code)      // spaces ke bina, sensitive flag + auto-clear
        toast("2FA code copied")
    }

    // ── "2FA jodein": login entry chuno → secret / otpauth link daalo → VaultManager.update ──
    private fun pickEntryForTotp() {
        val candidates = VaultManager.getPasswords()
            .filter { val t: String? = it.totp; it.type == "login" && t.isNullOrBlank() }
            .sortedBy { it.site.lowercase() }
        if (candidates.isEmpty()) {
            toast("Sabhi login entries mein 2FA pehle se hai")
            return
        }
        val labels = Array<CharSequence>(candidates.size) { i ->
            val e = candidates[i]
            OtpDisplay.label(e.site, e.url, e.username, e.email, e.mobile, e.fullName, null).text
        }
        AlertDialog.Builder(this)
            .setTitle("Kis entry mein 2FA jodna hai?")
            .setItems(labels) { _, which -> askSecret(candidates[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askSecret(e: PasswordEntry) {
        val input = EditText(this).apply {
            hint = "Base32 secret ya otpauth:// link"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            setSingleLine(true)
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(OtpDisplay.label(e.site, e.url, e.username, e.email, e.mobile, e.fullName, null).text)
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val txt = input.text.toString().trim()
                if (Totp.parse(txt) == null) {
                    toast("2FA secret galat hai (base32 key ya otpauth:// link chahiye)")
                    return@setPositiveButton
                }
                // otpauth link jaisa hai waisa; plain base32 ke spaces/dash hatakar UPPERCASE (extension jaisa).
                val stored = if (txt.startsWith("otpauth://", ignoreCase = true)) txt
                             else txt.replace(" ", "").replace("-", "").uppercase()
                if (VaultManager.update(this, e.copy(totp = stored))) {
                    toast("2FA jud gaya")
                    reload()
                } else {
                    toast("Save nahi ho paaya")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

/** Ek 2FA row. Secret parse ek baar hota hai; code sirf tab dobara banta hai jab 30s ka step badle. */
class OtpItem(
    val id: String,
    val issuer: String,
    val account: String,
    val label: String,
    val params: Totp.Params
) {
    private var step = -1L
    private var code = ""

    fun currentCode(nowMs: Long = System.currentTimeMillis()): String {
        val s = nowMs / 1000L / params.period
        if (s != step) {
            code = Totp.code(params, nowMs) ?: ""
            step = s
        }
        return code
    }
}

class OtpAdapter(
    private val onCopy: (OtpItem) -> Unit,
    private val onEdit: (OtpItem) -> Unit
) : RecyclerView.Adapter<OtpAdapter.VH>() {

    private var list: List<OtpItem> = emptyList()

    fun submit(l: List<OtpItem>) { list = l; notifyDataSetChanged() }

    /** Har second: sirf code/timer dobara bind (payload = poora row recreate nahi). */
    fun refreshTime() {
        if (list.isNotEmpty()) notifyItemRangeChanged(0, list.size, "t")
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvLabel: TextView = v.findViewById(R.id.tvOtpLabel)
        val tvCode: TextView = v.findViewById(R.id.tvOtpCode)
        val tvSecs: TextView = v.findViewById(R.id.tvOtpSecs)
        val pb: ProgressBar = v.findViewById(R.id.pbOtp)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_otp, p, false))

    override fun getItemCount() = list.size

    override fun onBindViewHolder(h: VH, i: Int) = bind(h, list[i])

    override fun onBindViewHolder(h: VH, i: Int, payloads: MutableList<Any>) = bind(h, list[i])

    private fun bind(h: VH, item: OtpItem) {
        val ctx = h.itemView.context
        val now = System.currentTimeMillis()
        val period = item.params.period
        val left = Totp.secondsLeft(item.params, now)
        val code = item.currentCode(now)

        h.tvLabel.text = item.label
        h.tvCode.text = if (code.isEmpty()) "------" else Totp.format(code)
        h.tvSecs.text = left.toString()

        val periodMs = period * 1000L
        h.pb.progress = (((periodMs - (now % periodMs)) * 1000L) / periodMs).toInt()

        val color = ContextCompat.getColor(ctx, if (left <= 5) R.color.danger else R.color.accent_blue)
        h.tvCode.setTextColor(color)
        h.tvSecs.setTextColor(color)
        h.pb.progressTintList = ColorStateList.valueOf(color)

        h.itemView.setOnClickListener { onCopy(item) }
        h.itemView.setOnLongClickListener { onEdit(item); true }
    }
}
