package com.babasitaram.pro.autofill

import android.text.InputType

/**
 * Field ko PASSWORD / USERNAME / TEXT / IGNORE mein baantta hai.
 * Pure Kotlin (Android objects nahi) — isliye JVM par test hota hai.
 *
 * FIX: pehle `inputType and TYPE_TEXT_VARIATION_WEB_PASSWORD != 0` jaisa bitmask tha, jo har EMAIL (0x21)
 * aur WebView text (0xA1) field ko "password" bana deta tha. Ab class + variation alag-alag mask karke barabar dekhte hain.
 */
object FieldClassifier {

    enum class Kind { PASSWORD, USERNAME, TEXT, IGNORE }

    class Desc(
        val hints: List<String> = emptyList(),
        val inputType: Int = 0,
        val idEntry: String = "",
        val hint: String = "",
        val contentDesc: String = "",
        val htmlType: String = "",
        val htmlName: String = "",
        val htmlId: String = "",
        val htmlAutocomplete: String = "",
        val htmlPlaceholder: String = "",
        val htmlAria: String = ""
    )

    private val IGNORE_TYPES = setOf(
        "hidden", "checkbox", "radio", "submit", "button", "image", "file", "reset", "range", "color", "search"
    )

    private fun tokens(s: String): List<String> = s.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    fun classify(d: Desc): Kind {
        val text = listOf(d.idEntry, d.hint, d.contentDesc, d.htmlName, d.htmlId, d.htmlPlaceholder, d.htmlAria)
            .joinToString(" ").lowercase()
        val tok = tokens(text)
        val hints = d.hints.map { it.lowercase() }
        val ac = d.htmlAutocomplete.lowercase()
        val type = d.htmlType.lowercase().trim()
        val cls = d.inputType and InputType.TYPE_MASK_CLASS
        val variation = d.inputType and InputType.TYPE_MASK_VARIATION

        // ── Ignore: search, OTP, captcha, hidden, checkbox ...
        if (type in IGNORE_TYPES) return Kind.IGNORE
        if (hints.any { it.contains("search") } || tok.contains("search") || text.contains("captcha")) return Kind.IGNORE
        if (tok.contains("otp") || text.contains("one-time") || text.contains("one time") ||
            hints.any { it.contains("otp") || it.contains("smsotp") || it.contains("onetime") } ||
            ac.contains("one-time-code")) return Kind.IGNORE

        // ── Password
        val pwdByInput =
            (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val pwdByText = text.contains("password") || text.contains("passwd") || text.contains("pwd") ||
            text.contains("passcode") || tok.contains("pass") || tok.contains("pw")
        if (type == "password" || ac.contains("password") || hints.any { it.contains("password") } || pwdByInput || pwdByText) {
            return Kind.PASSWORD
        }

        // ── Username (email / phone / user id)
        val userByHint = hints.any {
            it.contains("username") || it.contains("email") || it == "phone" || it.contains("phonenumber") || it == "tel"
        }
        val userByHtml = ac.contains("username") || ac.contains("email") || ac == "tel" || type == "email" || type == "tel"
        val userByInput = cls == InputType.TYPE_CLASS_PHONE ||
            (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))
        val userByText = text.contains("user") || text.contains("email") || text.contains("e-mail") ||
            text.contains("login") || text.contains("mobile") || text.contains("phone") || text.contains("account") ||
            tok.contains("uid") || text.contains("userid")   // camelCase (etMobile) lowercase hone par "etmobile" banta hai — isliye substring
        if (userByHint || userByHtml || userByInput || userByText) return Kind.USERNAME

        // ── Generic single-line text (fallback candidate: password se theek pehle wala username maana jaata hai)
        val isText = (cls == InputType.TYPE_CLASS_TEXT || d.inputType == 0) &&
            (d.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0 &&
            (type.isEmpty() || type == "text")
        return if (isText) Kind.TEXT else Kind.IGNORE
    }
}
