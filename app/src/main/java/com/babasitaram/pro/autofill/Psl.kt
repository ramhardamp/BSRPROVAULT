package com.babasitaram.pro.autofill

import android.content.Context
import java.io.InputStream

/**
 * Public Suffix List (publicsuffix.org) — sahi eTLD+1 ("registrable domain") nikalne ke liye.
 * "sirf aakhri 2 hisse" wala tarika co.in / co.uk / github.io par galat (aur khatarnak) hota hai:
 * amazon.co.in aur evil.co.in dono "co.in" ban jaate. Isliye poori PSL (assets/public_suffix_list.txt) use hoti hai.
 */
object Psl {
    private val rules = HashSet<String>()
    private val wildcards = HashSet<String>()      // "*.ck" -> "ck"
    private val exceptions = HashSet<String>()     // "!www.ck" -> "www.ck"
    @Volatile private var loaded = false

    // PSL load na ho paaye to bhi common multi-part suffixes se surakshit rahein
    private val FALLBACK = setOf(
        "co.in", "net.in", "org.in", "gov.in", "ac.in", "edu.in", "firm.in", "gen.in", "ind.in", "nic.in", "res.in",
        "co.uk", "org.uk", "gov.uk", "ac.uk", "com.au", "net.au", "org.au", "co.nz", "co.jp", "or.jp", "ne.jp",
        "com.br", "com.cn", "com.hk", "com.sg", "com.my", "com.pk", "com.bd", "com.np", "com.lk",
        "co.za", "com.mx", "com.tr", "com.sa", "com.eg", "com.ng", "co.id", "co.kr",
        "github.io", "blogspot.com", "herokuapp.com", "vercel.app", "netlify.app", "pages.dev", "web.app", "firebaseapp.com"
    )

    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                ctx.applicationContext.assets.open("public_suffix_list.txt").use { loadFrom(it) }
            } catch (e: Exception) {
                AutofillLogBridge.note(ctx, "PSL load fail: " + e.javaClass.simpleName)
            }
        }
    }

    /** Test/JVM ke liye bhi use hota hai. */
    fun loadFrom(stream: InputStream) {
        val r = HashSet<String>(); val w = HashSet<String>(); val x = HashSet<String>()
        stream.bufferedReader().useLines { lines ->
            for (raw in lines) {
                val l = raw.trim().removePrefix("\uFEFF")
                if (l.isEmpty() || l.startsWith("//") || l.startsWith("#")) continue
                when {
                    l.startsWith("!") -> x.add(l.substring(1))
                    l.startsWith("*.") -> w.add(l.substring(2))
                    else -> r.add(l)
                }
            }
        }
        synchronized(this) {
            rules.clear(); rules.addAll(r)
            wildcards.clear(); wildcards.addAll(w)
            exceptions.clear(); exceptions.addAll(x)
            loaded = r.isNotEmpty()
        }
    }

    fun isLoaded(): Boolean = loaded

    private fun looksLikeIp(h: String): Boolean =
        h.contains(':') || (h.isNotEmpty() && h.all { it.isDigit() || it == '.' })

    /**
     * "mail.google.com" -> "google.com", "a.b.co.uk" -> "b.co.uk", "foo.github.io" -> "foo.github.io".
     * Host khud public suffix ho, IP ho ya single label ho to "" (koi shared/registrable domain nahi).
     */
    fun registrableDomain(host: String): String {
        val h = host.trim().trim('.').lowercase()
        if (h.isEmpty() || looksLikeIp(h)) return ""
        val labels = h.split('.')
        if (labels.size < 2) return ""
        var suffixLen = 1   // implicit "*" rule
        for (i in labels.indices) {
            val cand = labels.subList(i, labels.size).joinToString(".")
            val n = labels.size - i
            if (exceptions.contains(cand)) { suffixLen = n - 1; break }
            var hit = rules.contains(cand) || (!loaded && FALLBACK.contains(cand))
            if (!hit && i + 1 < labels.size) {
                hit = wildcards.contains(labels.subList(i + 1, labels.size).joinToString("."))
            }
            if (hit && n > suffixLen) suffixLen = n
        }
        if (labels.size <= suffixLen) return ""
        return labels.subList(labels.size - suffixLen - 1, labels.size).joinToString(".")
    }
}

/** Psl ko AutofillLog se seedha jodne se bachne ke liye chhota bridge (circular dependency nahi). */
object AutofillLogBridge {
    fun note(ctx: Context, msg: String) = com.babasitaram.pro.AutofillLog.add(ctx, msg)
}
