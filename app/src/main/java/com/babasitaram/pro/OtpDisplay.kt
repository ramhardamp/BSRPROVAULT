package com.babasitaram.pro

/**
 * OTP / 2FA Manager ka label — Google Authenticator jaisa "Issuer: account".
 * Pure Kotlin (Android dependency nahi) taaki JVM par test ho sake. Extension (passwords.js) ka
 * otpIssuerName()/otpAccountIdentity() same rules follow karta hai.
 */
object OtpDisplay {

    class Label(val issuer: String, val account: String) {
        /** "Google: user@gmail.com" — issuer ya account khali ho to sirf jo hai wahi. */
        val text: String
            get() = when {
                issuer.isEmpty() && account.isEmpty() -> "Account"
                issuer.isEmpty() -> account
                account.isEmpty() || account.equals(issuer, ignoreCase = true) -> issuer
                else -> "$issuer: $account"
            }
    }

    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val HOST = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$", RegexOption.IGNORE_CASE)
    private val PREFIXES = setOf(
        "www", "accounts", "account", "login", "signin", "auth", "sso", "id",
        "myaccount", "secure", "m", "app", "web", "mail", "open"
    )
    private val SLD = setOf("co", "com", "org", "net", "gov", "edu", "ac")
    private val BRANDS = mapOf(
        "github" to "GitHub", "gitlab" to "GitLab", "linkedin" to "LinkedIn",
        "paypal" to "PayPal", "youtube" to "YouTube", "whatsapp" to "WhatsApp",
        "openai" to "OpenAI", "microsoftonline" to "Microsoft", "icloud" to "iCloud"
    )

    private fun clean(s: String?): String = (s ?: "").trim()

    /** "accounts.google.com" -> "Google", "eu.org" -> "eu.org" (bahut chhota naam ho to poora host). */
    fun hostToBrand(host: String): String {
        val parts = host.trim().lowercase().removePrefix("www.").split('.').toMutableList()
        while (parts.size > 2 && parts.first() in PREFIXES) parts.removeAt(0)
        if (parts.size < 2) return host.trim()
        val idx = if (parts.size >= 3 && parts[parts.size - 2] in SLD) parts.size - 3 else parts.size - 2
        val name = parts[idx]
        if (name.length < 3) return parts.joinToString(".")
        return BRANDS[name] ?: name.replaceFirstChar { it.uppercase() }
    }

    private fun hostOf(raw: String?): String? {
        var u = clean(raw)
        if (u.isEmpty()) return null
        if (!u.contains("://")) u = "https://$u"
        return try {
            java.net.URI(u).host?.takeIf { it.isNotBlank() }
        } catch (e: Exception) { null }
    }

    /** Entry ka naam issuer ban sakta hai sirf tab jab wo asli naam ho (URL / host / email nahi). */
    private fun friendlySite(site: String?): String? {
        val s = clean(site)
        if (s.isEmpty() || s.contains("://") || EMAIL.matches(s) || HOST.matches(s)) return null
        return s
    }

    fun label(
        site: String?, url: String?,
        username: String?, email: String?, mobile: String?, fullName: String?,
        totp: String?
    ): Label {
        val p = try { totp?.let { Totp.parse(it) } } catch (e: Exception) { null }

        val issuer = clean(p?.issuer).ifEmpty {
            friendlySite(site)
                ?: hostOf(url)?.let { hostToBrand(it) }
                ?: clean(site).takeIf { it.isNotEmpty() && HOST.matches(it) }?.let { hostToBrand(it) }
                ?: ""
        }

        val cands = listOf(username, email, mobile, fullName).map { clean(it) }.filter { it.isNotEmpty() }
        val uriAccount = clean(p?.account)
        val account = cands.firstOrNull { EMAIL.matches(it) }
            ?: uriAccount.takeIf { EMAIL.matches(it) }
            ?: cands.firstOrNull()
            ?: uriAccount

        return Label(issuer, account)
    }
}
