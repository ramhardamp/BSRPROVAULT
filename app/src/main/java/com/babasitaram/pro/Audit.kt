package com.babasitaram.pro

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Password health (weak / reused / purane) + optional leak check (HaveIBeenPwned k-anonymity). */
object Audit {

    class Result(
        val weak: List<PasswordEntry>,
        val reused: List<PasswordEntry>,
        val old: List<PasswordEntry>,
        val total: Int,
        val expiredCards: List<PasswordEntry> = emptyList()
    ) {
        fun score(): Int {
            if (total == 0) return 100
            val bad = HashSet<String>()
            weak.forEach { bad.add(it.id) }
            reused.forEach { bad.add(it.id) }
            old.forEach { bad.add(it.id) }
            return (100 - bad.size * 100 / total).coerceIn(0, 100)
        }
    }

    private fun logins(): List<PasswordEntry> =
        VaultManager.getPasswords().filter { it.type == "login" && it.password.isNotEmpty() }

    /** "MM/YY" ya "MM/YYYY" — beet chuke ya is mahine khatam hone wale cards. */
    private fun expiredCards(): List<PasswordEntry> {
        val cal = java.util.Calendar.getInstance()
        val nowYm = cal.get(java.util.Calendar.YEAR) * 12 + cal.get(java.util.Calendar.MONTH) + 1
        return VaultManager.getPasswords().filter { e ->
            if (e.type != "card") return@filter false
            val p = e.cardExpiry.replace(" ", "").split('/', '-')
            if (p.size != 2) return@filter false
            val mm = p[0].toIntOrNull() ?: return@filter false
            var yy = p[1].toIntOrNull() ?: return@filter false
            if (yy < 100) yy += 2000
            yy * 12 + mm < nowYm
        }
    }

    fun analyze(): Result {
        val l = logins()
        val weak = l.filter { VaultManager.strengthScore(it.password) < 40 }
        val reused = l.groupBy { it.password }.values.filter { it.size > 1 }.flatten()
        val cutoff = System.currentTimeMillis() - 180L * 24 * 3600 * 1000
        val old = l.filter { it.updatedAt in 1 until cutoff }
        return Result(weak, reused, old, l.size, expiredCards())
    }

    private fun names(list: List<PasswordEntry>): String {
        if (list.isEmpty()) return "   ✓ Koi nahi\n"
        val sb = StringBuilder()
        for (e in list.take(6)) sb.append("   • ").append(e.site).append("\n")
        if (list.size > 6) sb.append("   ... aur ").append(list.size - 6).append("\n")
        return sb.toString()
    }

    fun show(a: Activity) {
        val r = analyze()
        val msg = StringBuilder()
        msg.append("Health score: ").append(r.score()).append(" / 100\n\n")
        msg.append("⚠️ Weak passwords (").append(r.weak.size).append(")\n").append(names(r.weak)).append("\n")
        msg.append("♻️ Repeat hue passwords (").append(r.reused.size).append(")\n").append(names(r.reused)).append("\n")
        msg.append("🕒 6 mahine se purane (").append(r.old.size).append(")\n").append(names(r.old)).append("\n")
        msg.append("💳 Expire ho chuke cards (").append(r.expiredCards.size).append(")\n").append(names(r.expiredCards))
        AlertDialog.Builder(a)
            .setTitle("🛡️ Security Audit")
            .setMessage(msg.toString())
            .setPositiveButton("Leak check (internet)") { _, _ -> leakCheck(a) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun sha1(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (b in d) sb.append(String.format("%02X", b))
        return sb.toString()
    }

    private fun fetchRange(prefix: String): String? {
        return try {
            val c = URL("https://api.pwnedpasswords.com/range/" + prefix).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("Add-Padding", "true")
            c.setRequestProperty("User-Agent", "BabaSitaRamPro-Android")
            val text = if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
            c.disconnect()
            text
        } catch (e: Exception) { null }
    }

    private fun isActivityAlive(a: Activity): Boolean =
        !a.isFinishing && (Build.VERSION.SDK_INT < 17 || !a.isDestroyed)

    private fun leakCheck(a: Activity) {
        val list = logins()
        if (list.isEmpty()) return

        val handler = Handler(Looper.getMainLooper())
        val cancelled = AtomicBoolean(false)
        val activityRef = WeakReference(a)

        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
        }

        val status = TextView(a).apply {
            text = "0% — 0/${list.size} checked"
            gravity = Gravity.CENTER_VERTICAL
        }
        val spinner = ProgressBar(a).apply { isIndeterminate = true }
        val progress = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        val info = TextView(a).apply {
            text = "Sirf password ke hash ke pehle 5 characters bheje jaate hain — poora password kabhi nahi."
            setPadding(0, 8, 0, 0)
        }

        root.addView(spinner, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(progress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(info, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val dlg = AlertDialog.Builder(a)
            .setTitle("Leak check")
            .setView(root)
            .setNegativeButton("Cancel") { _, _ -> cancelled.set(true) }
            .create()

        dlg.setOnDismissListener { cancelled.set(true) }
        dlg.show()
        val dialogRef = WeakReference(dlg)
        val statusRef = WeakReference(status)
        val progressRef = WeakReference(progress)

        Thread {
            val hits = ArrayList<PasswordEntry>()
            val counts = ArrayList<Int>()
            val cache = HashMap<String, String>()
            var failed = 0
            var checked = 0
            var completedNormally = false

            try {
                for (e in list) {
                    if (cancelled.get()) return@Thread

                    val sha = sha1(e.password)
                    val prefix = sha.substring(0, 5)
                    val suffix = sha.substring(5)
                    var body = cache[prefix]
                    if (body == null) {
                        body = fetchRange(prefix)
                        if (body != null) cache[prefix] = body
                    }
                    if (body == null) {
                        failed++
                    } else {
                        for (line in body.lineSequence()) {
                            if (line.startsWith(suffix, ignoreCase = true)) {
                                val c = line.substringAfter(':').trim().toIntOrNull() ?: 1
                                if (c > 0) { hits.add(e); counts.add(c) }
                                break
                            }
                        }
                    }

                    checked++
                    val percent = (checked * 100 / list.size).coerceIn(0, 100)
                    handler.post {
                        if (cancelled.get()) return@post
                        val current = activityRef.get() ?: return@post
                        val dialog = dialogRef.get()
                        val statusView = statusRef.get()
                        val progressView = progressRef.get()
                        if (!isActivityAlive(current) || dialog == null || !dialog.isShowing || statusView == null || progressView == null) {
                            cancelled.set(true)
                            return@post
                        }
                        statusView.text = "$percent% — $checked/${list.size} checked"
                        progressView.progress = percent
                    }
                }
                completedNormally = !cancelled.get()
            } catch (_: Throwable) {
                // Never let a worker exception take down the app.
            }

            if (!completedNormally || cancelled.get()) return@Thread

            handler.post {
                val current = activityRef.get() ?: return@post
                val dialog = dialogRef.get()
                if (!isActivityAlive(current) || dialog == null || !dialog.isShowing) return@post
                dialog.dismiss()
                showLeakResult(current, hits, counts, failed, list.size)
            }
        }.start()
    }

    private fun showLeakResult(a: Activity, hits: List<PasswordEntry>, counts: List<Int>, failed: Int, total: Int) {
        if (!isActivityAlive(a)) return
        val sb = StringBuilder()
        if (failed == total) {
            sb.append("Internet ya server se connect nahi ho paya. Baad mein try karein.")
        } else {
            if (hits.isEmpty()) {
                sb.append("✅ Koi bhi password known leaks mein nahi mila.")
            } else {
                sb.append("🚨 ").append(hits.size).append(" password data-breach mein mile — turant badlein:\n\n")
                for (i in hits.indices) {
                    sb.append("• ").append(hits[i].site).append("  (").append(counts[i]).append(" baar leak)\n")
                }
            }
            if (failed > 0) sb.append("\n\n(").append(failed).append(" check nahi ho paye)")
        }
        AlertDialog.Builder(a).setTitle("Leak check result").setMessage(sb.toString())
            .setPositiveButton("OK", null).show()
    }
}
