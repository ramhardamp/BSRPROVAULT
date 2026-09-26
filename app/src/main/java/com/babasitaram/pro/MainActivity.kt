package com.babasitaram.pro

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var fab: com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
    private lateinit var tvEmpty: TextView
    private lateinit var tvCount: TextView
    private lateinit var chipAll: TextView
    private lateinit var chipFav: TextView
    private lateinit var chipBanking: TextView
    private lateinit var chipSocial: TextView
    private lateinit var chipEmail: TextView
    private lateinit var chipWork: TextView
    private lateinit var chipPersonal: TextView
    private lateinit var chipShop: TextView
    private lateinit var chipNotes: TextView
    private lateinit var chipCards: TextView
    private lateinit var chipIds: TextView
    private lateinit var chipOther: TextView
    private lateinit var bannerAutofill: View
    private var adapter: PwAdapter? = null
    private var currentFilter = "All"
    private val handler = Handler(Looper.getMainLooper())
    private var clipClearRunnable: Runnable? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)

        if (!VaultManager.isUnlocked) { goLogin(); return }

        rv           = findViewById(R.id.rvPasswords)
        etSearch     = findViewById(R.id.etSearch)
        fab          = findViewById(R.id.fabAdd)
        tvEmpty      = findViewById(R.id.tvEmpty)
        tvCount      = findViewById(R.id.tvCount)
        chipAll      = findViewById(R.id.chipAll)
        chipFav      = findViewById(R.id.chipFav)
        chipBanking  = findViewById(R.id.chipBanking)
        chipSocial   = findViewById(R.id.chipSocial)
        chipEmail    = findViewById(R.id.chipEmail)
        chipWork     = findViewById(R.id.chipWork)
        chipPersonal = findViewById(R.id.chipPersonal)
        chipShop     = findViewById(R.id.chipShop)
        chipNotes    = findViewById(R.id.chipNotes)
        chipCards    = findViewById(R.id.chipCards)
        chipIds      = findViewById(R.id.chipIds)
        chipOther    = findViewById(R.id.chipOther)
        bannerAutofill = findViewById(R.id.bannerAutofill)
        findViewById<Button>(R.id.btnBannerAutofill).setOnClickListener { AutofillSetup.request(this) }

        setupRv()
        setupSearch()
        setupChips()
        fab.setOnClickListener { startActivity(Intent(this, AddEditActivity::class.java)) }
        findViewById<ImageButton>(R.id.btnSettings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnOtp)?.setOnClickListener {
            startActivity(Intent(this, OtpManagerActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnGenerator)?.setOnClickListener {
            startActivity(Intent(this, GeneratorActivity::class.java))
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (!VaultManager.isUnlocked) { goLogin(); return }
        bannerAutofill.visibility = if (AutofillSetup.isEnabled(this)) View.GONE else View.VISIBLE
        render()
    }

    private fun setupRv() {
        rv.layoutManager = LinearLayoutManager(this)
        rv.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        adapter = PwAdapter(
            onEdit   = { startActivity(Intent(this, AddEditActivity::class.java).putExtra("id", it.id)) },
            onDelete = { confirmDelete(it) },
            onCopyU  = { copy("Username", it.username.ifEmpty { it.mobile }) },
            onCopyP  = { copy("Password", it.password) },
            onFav    = { VaultManager.toggleFav(this, it.id); render() },
            onShare  = { shareEntry(it) },
            onShow   = { showDetail(it) }
        )
        rv.adapter = adapter
    }

    private fun setupSearch() {
        etSearch.setSingleLine(true)
        etSearch.maxLines = 1
        etSearch.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        etSearch.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { render() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
    }

    private fun setupChips() {
        val chips = listOf(
            chipAll to "All", chipFav to "Favorites",
            chipBanking to "Banking", chipSocial to "Social", chipEmail to "Email",
            chipWork to "Work", chipPersonal to "Personal", chipShop to "Shopping", chipNotes to "Notes", chipCards to "Cards", chipIds to "IDs", chipOther to "Other"
        )
        chips.forEach { (chip, filter) ->
            chip.setOnClickListener {
                currentFilter = filter
                chips.forEach { (c, _) -> c.isSelected = false }
                chip.isSelected = true
                render()
            }
        }
        chipAll.isSelected = true
    }

    private fun render() {
        val q = etSearch.text.toString().trim()
        val list: List<PasswordEntry> = when {
            q.isNotEmpty()               -> VaultManager.search(q)
            currentFilter == "Favorites" -> VaultManager.getFavorites()
            currentFilter == "All"       -> VaultManager.getPasswords()
            currentFilter == "Notes"     -> VaultManager.getPasswords().filter { it.type == "note" }
            currentFilter == "Cards"     -> VaultManager.getPasswords().filter { it.type == "card" }
            currentFilter == "IDs"       -> VaultManager.getPasswords().filter { it.type == "identity" }
            else                         -> VaultManager.getByCategory(currentFilter)
        }
        val sorted = list.sortedWith(
            compareByDescending<PasswordEntry> { it.isFavorite }.thenBy { it.site.lowercase() }
        )
        adapter?.submit(sorted)
        val total = VaultManager.getPasswords().size
        tvCount.text = if (q.isNotEmpty() || currentFilter != "All") "${list.size}/$total" else "$total passwords"
        tvEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDelete(e: PasswordEntry) {
        AlertDialog.Builder(this)
            .setTitle("Trash mein bhejein?")
            .setMessage("\"${e.site}\" Trash mein jayega. 30 din tak Settings → Trash se wapas la sakte hain.")
            .setPositiveButton("Trash mein bhejo") { _, _ ->
                VaultManager.delete(this, e.id)
                render()
                toast("🗑️ Trash mein gaya")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun shareEntry(e: PasswordEntry) {
        val text = buildString {
            append(e.site)
            if (e.username.isNotEmpty()) append("\nUsername: ").append(e.username)
            if (e.url.isNotEmpty()) append("\nURL: ").append(e.url)
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share entry"))
    }

    private fun maskCard(n: String): String {
        val d = n.filter { it.isDigit() }
        return if (d.length <= 4) "••••" else "•••• •••• •••• " + d.takeLast(4)
    }

    private fun showDetail(e: PasswordEntry) {
        try {
            val totpCode = if (e.totp.isNotEmpty()) runCatching { Totp.code(e.totp) }.getOrNull() else null
        val sb = StringBuilder()
        when (e.type) {
            "card" -> {
                if (e.cardholder.isNotEmpty()) sb.append("Cardholder: ").append(e.cardholder).append("\n")
                sb.append("Card: ").append(maskCard(e.cardNumber)).append("\n")
                if (e.cardExpiry.isNotEmpty()) sb.append("Expiry: ").append(e.cardExpiry).append("\n")
                sb.append("CVV: •••\n")
            }
            "identity" -> {
                if (e.fullName.isNotEmpty()) sb.append("Name: ").append(e.fullName).append("\n")
                if (e.email.isNotEmpty()) sb.append("Email: ").append(e.email).append("\n")
                if (e.phone.isNotEmpty()) sb.append("Phone: ").append(e.phone).append("\n")
                if (e.address.isNotEmpty()) sb.append("Address: ").append(e.address).append("\n")
                if (e.idNumber.isNotEmpty()) sb.append("ID: ").append(e.idNumber).append("\n")
                if (e.idExpiry.isNotEmpty()) sb.append("ID expiry: ").append(e.idExpiry).append("\n")
            }
            "note" -> { }
            else -> {
                sb.append("Username: ").append(e.username.ifEmpty { e.mobile }).append("\n")
                sb.append("Password: ••••••••\n")
            }
        }
        if (e.url.isNotEmpty()) sb.append("\nURL: ").append(e.url)
        if (e.mobile.isNotEmpty() && e.type == "login") sb.append("\nMobile: ").append(e.mobile)
        if (e.folder.isNotEmpty()) sb.append("\n📁 ").append(e.folder)
        val tags = e.tags.orEmpty()
        if (tags.isNotEmpty()) sb.append("\n🏷️ ").append(tags.joinToString(", "))
        for (f in e.fields.orEmpty()) {
            if (f != null) sb.append("\n").append(f.k).append(": ").append(f.v)
        }
        if (e.notes.isNotEmpty()) {
            sb.append("\n\n")
            if (e.type != "note") sb.append("Notes: ")
            sb.append(e.notes)
        }
        if (totpCode != null) {
            sb.append("\n\n🔑 2FA Code: ").append(Totp.format(totpCode))
                .append("  (").append(runCatching { Totp.secondsLeft(e.totp) }.getOrDefault(0)).append("s)")
        }
        val prefix = when (e.type) { "note" -> "📝 "; "card" -> "💳 "; "identity" -> "🪪 "; else -> "" }
        val b = AlertDialog.Builder(this).setTitle(prefix + e.site).setMessage(sb.toString())
        when (e.type) {
            "note" -> b.setPositiveButton("Copy Note") { _, _ -> copy("Note", e.notes) }
            "card" -> {
                b.setPositiveButton("Copy Number") { _, _ -> copy("Card", e.cardNumber) }
                b.setNeutralButton("Copy CVV") { _, _ -> copy("CVV", e.cardCvv) }
            }
            "identity" -> {
                b.setPositiveButton("Copy Name") { _, _ -> copy("Name", e.fullName) }
                b.setNeutralButton("Copy ID") { _, _ -> copy("ID", e.idNumber) }
            }
            else -> {
                b.setPositiveButton("Copy Password") { _, _ -> copy("Password", e.password) }
                b.setNeutralButton("Copy Username") { _, _ -> copy("Username", e.username.ifEmpty { e.mobile }) }
            }
        }
        b.setNegativeButton("More…") { _, _ -> showActions(e) }
        b.show()
        } catch (t: Throwable) {
            Log.e("BSR_UI", "entry detail render failed id=" + e.id + " type=" + e.type, t)
            toast("Entry open nahi ho payi — entry data check karein")
        }
    }

    private fun showActions(e: PasswordEntry) {
        val labels = ArrayList<CharSequence>()
        val acts = ArrayList<() -> Unit>()
        fun add(label: String, act: () -> Unit) { labels.add(label); acts.add(act) }

        when (e.type) {
            "login" -> add("👁 Password dikhayein") { revealPassword(e) }
            "card" -> {
                add("👁 Poora card dikhayein") { revealText(e.site, "Number: " + e.cardNumber + "\nExpiry: " + e.cardExpiry + "\nCVV: " + e.cardCvv + "\nHolder: " + e.cardholder) }
                add("📋 Expiry copy") { copy("Expiry", e.cardExpiry) }
                add("📋 Cardholder copy") { copy("Cardholder", e.cardholder) }
            }
            "identity" -> {
                if (e.email.isNotEmpty()) add("📋 Email copy") { copy("Email", e.email) }
                if (e.phone.isNotEmpty()) add("📋 Phone copy") { copy("Phone", e.phone) }
                if (e.address.isNotEmpty()) add("📋 Address copy") { copy("Address", e.address) }
            }
        }
        if (e.totp.isNotEmpty()) {
            add("🔑 2FA code copy") {
                val c = Totp.code(e.totp)
                if (c != null) copy("2FA code", c) else toast("2FA secret galat hai")
            }
        }
        if (e.url.isNotEmpty()) {
            add("🌐 Website kholein") { openUrl(e.url) }
            add("📋 URL copy") { copy("URL", e.url) }
        }
        for (f in e.fields.orEmpty()) {
            if (f != null && f.v.isNotEmpty()) add("📋 " + f.k + " copy") { copy(f.k, f.v) }
        }
        if (e.history.orEmpty().isNotEmpty()) add("🕘 Purane passwords (" + e.history.orEmpty().size + ")") { showHistory(e) }
        add("✏️ Edit") { startActivity(Intent(this, AddEditActivity::class.java).putExtra("id", e.id)) }

        AlertDialog.Builder(this)
            .setTitle(e.site)
            .setItems(labels.toTypedArray()) { _, which -> acts[which]() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun revealText(title: String, text: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(text)
            .setPositiveButton("Close", null).show()
    }

    private fun revealPassword(e: PasswordEntry) {
        AlertDialog.Builder(this)
            .setTitle(e.site)
            .setMessage(e.password)
            .setPositiveButton("Copy") { _, _ -> copy("Password", e.password) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showHistory(e: PasswordEntry) {
        val fmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        val history = e.history.orEmpty()
        val labels = Array<CharSequence>(history.size) { i ->
            fmt.format(java.util.Date(history[i].at)) + "  •  " + history[i].pw
        }
        AlertDialog.Builder(this)
            .setTitle("Purane passwords (tap = copy)")
            .setItems(labels) { _, which -> copy("Purana password", history[which].pw) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openUrl(raw: String) {
        try {
            val u = if (raw.contains("://")) raw else "https://$raw"
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u)))
        } catch (ex: Exception) {
            toast("Website nahi khul payi")
        }
    }

    private fun copy(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= 33) {
            val extras = android.os.PersistableBundle()
            extras.putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
            clip.description.extras = extras
        }
        cm.setPrimaryClip(clip)
        val secs = AppPrefs.getClipClear(this)
        toast("$label copied!" + if (secs > 0) " (${secs}s mein clear hoga)" else "")
        if (secs > 0) {
            clipClearRunnable?.let { handler.removeCallbacks(it) }
            clipClearRunnable = Runnable {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        cm.clearPrimaryClip()
                    } else {
                        cm.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                } catch (ex: Exception) { }
            }
            handler.postDelayed(clipClearRunnable!!, secs * 1000L)
        }
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

// ── Adapter ──
class PwAdapter(
    private val onEdit:   (PasswordEntry) -> Unit,
    private val onDelete: (PasswordEntry) -> Unit,
    private val onCopyU:  (PasswordEntry) -> Unit,
    private val onCopyP:  (PasswordEntry) -> Unit,
    private val onFav:    (PasswordEntry) -> Unit,
    private val onShare:  (PasswordEntry) -> Unit,
    private val onShow:   (PasswordEntry) -> Unit
) : RecyclerView.Adapter<PwAdapter.VH>() {

    private var list: List<PasswordEntry> = emptyList()
    fun submit(l: List<PasswordEntry>) { list = l; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvAvatar:    ImageView    = v.findViewById(R.id.tvAvatar)
        val tvSite:      TextView     = v.findViewById(R.id.tvSite)
        val tvUser:      TextView     = v.findViewById(R.id.tvUser)
        val tvCat:       TextView     = v.findViewById(R.id.tvCategory)
        val tvStrength:  TextView     = v.findViewById(R.id.tvStrength)
        val btnFav:      ImageButton  = v.findViewById(R.id.btnFav)
        val btnCopyU:    ImageButton  = v.findViewById(R.id.btnCopyUser)
        val btnCopyP:    ImageButton  = v.findViewById(R.id.btnCopyPass)
        val btnEdit:     ImageButton  = v.findViewById(R.id.btnEdit)
        val btnDel:      ImageButton  = v.findViewById(R.id.btnDelete)
        val btnMore:     ImageButton  = v.findViewById(R.id.btnMore)
        val strengthBar: ProgressBar  = v.findViewById(R.id.strengthBar)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_password, p, false))

    override fun getItemCount() = list.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val e = list[i]
        val isLogin = e.type == "login"
        h.tvSite.text   = e.site.ifEmpty { "Unknown" }
        h.tvUser.text   = when (e.type) {
            "card" -> {
                val d = e.cardNumber.filter { it.isDigit() }
                if (d.length > 4) "•••• " + d.takeLast(4) else e.cardholder
            }
            "identity" -> e.fullName
            "note" -> e.notes.lineSequence().firstOrNull() ?: ""
            else -> e.username.ifEmpty { e.mobile }
        }
        h.tvCat.text    = when (e.type) {
            "note" -> "📝 Note"
            "card" -> "💳 Card"
            "identity" -> "🪪 ID"
            else -> e.category
        }
        h.tvAvatar.setImageResource(R.drawable.logo_app)

        val sc = VaultManager.strengthScore(e.password)
        h.strengthBar.progress = sc
        val (sLabel, sColor) = when {
            sc >= 80 -> "Strong" to 0xFF34d399.toInt()
            sc >= 60 -> "Good"   to 0xFF4f8ef7.toInt()
            sc >= 40 -> "Fair"   to 0xFFfbbf24.toInt()
            else     -> "Weak"   to 0xFFf87171.toInt()
        }
        h.tvStrength.text = sLabel
        h.tvStrength.setTextColor(sColor)
        h.strengthBar.progressTintList =
            android.content.res.ColorStateList.valueOf(sColor)

        val catColors = mapOf(
            "Banking"  to 0xFF1565C0.toInt(),
            "Social"   to 0xFF6A1B9A.toInt(),
            "Email"    to 0xFFE65100.toInt(),
            "Work"     to 0xFF2E7D32.toInt(),
            "Shopping" to 0xFFC62828.toInt(),
            "Games"    to 0xFF1B5E20.toInt(),
            "Other"    to 0xFF37474F.toInt()
        )
        h.tvAvatar.setBackgroundResource(R.drawable.circle_bg_blue)

        h.btnCopyP.visibility = View.GONE
        h.btnCopyU.visibility = View.GONE
        h.btnEdit.visibility = View.GONE
        h.btnDel.visibility = View.GONE
        h.strengthBar.visibility = if (isLogin) View.VISIBLE else View.GONE
        h.tvStrength.visibility = if (isLogin) View.VISIBLE else View.GONE

        h.btnFav.alpha = if (e.isFavorite) 1f else 0.35f
        h.btnFav.setColorFilter(
            if (e.isFavorite) 0xFFfbbf24.toInt() else 0xFF94a3b8.toInt()
        )

        h.btnFav.setOnClickListener { onFav(e) }
        h.btnMore.setOnClickListener { v ->
            PopupMenu(v.context, v).apply {
                menu.add("Edit")
                menu.add("Delete")
                if (isLogin) {
                    menu.add("Copy Username")
                    menu.add("Copy Password")
                }
                menu.add(if (e.isFavorite) "Remove Favorite" else "Favorite")
                menu.add("Share")
                setOnMenuItemClickListener { item ->
                    when (item.title.toString()) {
                        "Edit" -> onEdit(e)
                        "Delete" -> onDelete(e)
                        "Copy Username" -> onCopyU(e)
                        "Copy Password" -> onCopyP(e)
                        "Favorite" -> onFav(e)
                        "Remove Favorite" -> onFav(e)
                        "Share" -> onShare(e)
                    }
                    true
                }
            }.show()
        }
        h.itemView.setOnClickListener {
            try {
                onShow(e)
            } catch (t: Throwable) {
                android.util.Log.e("BSR_Main", "Entry open failed", t)
                Toast.makeText(it.context, "Entry khol nahi paayi — data check karein", Toast.LENGTH_SHORT).show()
            }
        }
        h.itemView.setOnLongClickListener {
            h.btnMore.performClick()
            true
        }
    }
}
