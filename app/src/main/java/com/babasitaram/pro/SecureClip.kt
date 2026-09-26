package com.babasitaram.pro

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/** Passwords copy karne ka ek hi tarika: "sensitive" flag + timer par auto-clear (Activity band ho jaaye tab bhi). */
object SecureClip {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun copy(ctx: Context, label: String, text: String) {
        val app = ctx.applicationContext
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= 33) {
            val extras = PersistableBundle()
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            clip.description.extras = extras
        }
        cm.setPrimaryClip(clip)
        pending?.let { handler.removeCallbacks(it) }
        val secs = AppPrefs.getClipClear(app)
        if (secs <= 0) return
        val r = Runnable {
            try {
                // Agar user ne beech mein kuch aur copy kar liya ho to use mat mitao.
                val cur = try { cm.primaryClip?.getItemAt(0)?.text?.toString() } catch (_: Exception) { null }
                if (cur == null || cur == text) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
                    else cm.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            } catch (_: Exception) { }
        }
        pending = r
        handler.postDelayed(r, secs * 1000L)
    }
}
