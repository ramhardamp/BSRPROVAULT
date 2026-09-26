import com.babasitaram.pro.*
import com.babasitaram.pro.autofill.*
import android.text.InputType as IT

var failed = 0
fun ok(n: String, c: Boolean) { println((if (c) "PASS " else "FAIL ") + n); if (!c) failed++ }
fun eq(n: String, exp: Any?, got: Any?) { val c = exp == got; println((if (c) "PASS " else "FAIL ") + n + (if (c) "" else "  expected=<$exp> got=<$got>")); if (!c) failed++ }
typealias K = FieldClassifier.Kind
fun d(hints: List<String> = emptyList(), it: Int = 0, id: String = "", hint: String = "", type: String = "", name: String = "", auto: String = "", ph: String = "") =
    FieldClassifier.Desc(hints = hints, inputType = it, idEntry = id, hint = hint, htmlType = type, htmlName = name, htmlAutocomplete = auto, htmlPlaceholder = ph)

fun main() {
    // ---------- eTLD+1 (real Public Suffix List asset) ----------
    java.io.FileInputStream("/home/claude/out/BabaSitaRam-Pro-Android/app/src/main/assets/public_suffix_list.txt").use { Psl.loadFrom(it) }
    for ((h, e) in listOf(
        "mail.google.com" to "google.com", "accounts.google.com" to "google.com", "google.com" to "google.com",
        "www.amazon.co.in" to "amazon.co.in", "evil.co.in" to "evil.co.in", "a.b.bbc.co.uk" to "bbc.co.uk",
        "alice.github.io" to "alice.github.io", "bob.github.io" to "bob.github.io", "github.io" to "",
        "shop.example.com.au" to "example.com.au", "www.ck" to "www.ck", "foo.bar.ck" to "foo.bar.ck",
        "localhost" to "", "192.168.1.5" to "", "netflix.com" to "netflix.com", "login.microsoftonline.com" to "microsoftonline.com"))
        eq("registrable($h)", e, Psl.registrableDomain(h))
    ok("evil.co.in != amazon.co.in (old 'last-2-labels' idea would have matched!)", Psl.registrableDomain("evil.co.in") != Psl.registrableDomain("www.amazon.co.in"))

    // ---------- findMatches end-to-end with the real service code ----------
    val vm = VaultManager::class.java
    val inst = vm.getField("INSTANCE").get(null)
    val f = vm.getDeclaredField("_passwords"); f.isAccessible = true
    f.set(inst, mutableListOf(
        PasswordEntry(site = "Google", url = "https://accounts.google.com", username = "me@gmail.com", password = "g1"),
        PasswordEntry(site = "Amazon IN", url = "https://www.amazon.co.in/ap/signin", username = "me", password = "a1"),
        PasswordEntry(site = "Blog A", url = "https://alice.github.io", username = "alice", password = "b1"),
        PasswordEntry(site = "Instagram", url = "", username = "ig", password = "i1"),
        PasswordEntry(site = "Card", type = "card", cardNumber = "4111111111111111"),
        PasswordEntry(site = "Note", type = "note", notes = "x")))
    fun m(domain: String, app: String = "com.android.chrome") = BSRAutofillService.findMatches(domain, app).map { it.site }
    eq("mail.google.com -> Google (was NO match before)", listOf("Google"), m("mail.google.com"))
    eq("google.com -> Google", listOf("Google"), m("google.com"))
    eq("amazon.co.in login -> Amazon IN", listOf("Amazon IN"), m("smile.amazon.co.in"))
    eq("evil.co.in must NOT match Amazon IN", emptyList<String>(), m("evil.co.in"))
    eq("bob.github.io must NOT match alice.github.io", emptyList<String>(), m("bob.github.io"))
    eq("alice.github.io matches", listOf("Blog A"), m("alice.github.io"))
    eq("google-phish.com no match", emptyList<String>(), m("google.com.evil.com"))
    eq("chrome without domain -> nothing", emptyList<String>(), m("", "com.android.chrome"))
    eq("native app package -> instagram", listOf("Instagram"), m("", "com.instagram.android"))
    ok("cards/notes never offered for autofill", m("", "com.example.card").isEmpty())

    // ---------- FieldClassifier ----------
    eq("EMAIL inputType 0x21 is USERNAME (old code: PASSWORD!)", K.USERNAME, FieldClassifier.classify(d(it = IT.TYPE_CLASS_TEXT or IT.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)))
    eq("WebView WEB_EDIT_TEXT no clue -> TEXT (old: PASSWORD!)", K.TEXT, FieldClassifier.classify(d(it = IT.TYPE_CLASS_TEXT or IT.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT)))
    eq("WEB_EMAIL_ADDRESS -> USERNAME", K.USERNAME, FieldClassifier.classify(d(it = IT.TYPE_CLASS_TEXT or IT.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)))
    eq("TEXT_VARIATION_PASSWORD", K.PASSWORD, FieldClassifier.classify(d(it = IT.TYPE_CLASS_TEXT or IT.TYPE_TEXT_VARIATION_PASSWORD)))
    eq("VISIBLE_PASSWORD", K.PASSWORD, FieldClassifier.classify(d(it = IT.TYPE_CLASS_TEXT or IT.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)))
    eq("WEB_PASSWORD", K.PASSWORD, FieldClassifier.classify(d(it = IT.TYPE_CLASS_TEXT or IT.TYPE_TEXT_VARIATION_WEB_PASSWORD)))
    eq("NUMBER_PASSWORD (PIN style)", K.PASSWORD, FieldClassifier.classify(d(it = IT.TYPE_CLASS_NUMBER or IT.TYPE_NUMBER_VARIATION_PASSWORD)))
    eq("phone class -> USERNAME", K.USERNAME, FieldClassifier.classify(d(it = IT.TYPE_CLASS_PHONE)))
    eq("chrome html type=password", K.PASSWORD, FieldClassifier.classify(d(type = "password")))
    eq("chrome html type=email", K.USERNAME, FieldClassifier.classify(d(type = "email")))
    eq("chrome html type=text name=login", K.USERNAME, FieldClassifier.classify(d(type = "text", name = "login")))
    eq("chrome autocomplete=current-password", K.PASSWORD, FieldClassifier.classify(d(type = "text", auto = "current-password")))
    eq("chrome autocomplete=username", K.USERNAME, FieldClassifier.classify(d(type = "text", auto = "username")))
    eq("chrome type=search ignored", K.IGNORE, FieldClassifier.classify(d(type = "search", name = "q")))
    eq("chrome type=hidden ignored", K.IGNORE, FieldClassifier.classify(d(type = "hidden")))
    eq("otp field ignored", K.IGNORE, FieldClassifier.classify(d(id = "et_otp")))
    eq("autofill hint password", K.PASSWORD, FieldClassifier.classify(d(hints = listOf("password"))))
    eq("autofill hint username", K.USERNAME, FieldClassifier.classify(d(hints = listOf("username"))))
    eq("autofill hint emailAddress", K.USERNAME, FieldClassifier.classify(d(hints = listOf("emailAddress"))))
    eq("native id et_password", K.PASSWORD, FieldClassifier.classify(d(id = "et_password", it = IT.TYPE_CLASS_TEXT)))
    eq("native id inputPassword", K.PASSWORD, FieldClassifier.classify(d(id = "inputPassword")))
    eq("'passenger_count' is NOT password", K.TEXT, FieldClassifier.classify(d(id = "passenger_count", it = IT.TYPE_CLASS_TEXT)))
    eq("'passport_no' is NOT password", K.TEXT, FieldClassifier.classify(d(id = "passport_no", it = IT.TYPE_CLASS_TEXT)))
    eq("native id et_username", K.USERNAME, FieldClassifier.classify(d(id = "et_username")))
    eq("native id etMobile", K.USERNAME, FieldClassifier.classify(d(id = "etMobile")))
    eq("hint 'Enter mobile number'", K.USERNAME, FieldClassifier.classify(d(hint = "Enter mobile number")))
    eq("hint 'Search products' ignored", K.IGNORE, FieldClassifier.classify(d(hint = "Search products")))
    eq("multi-line text is not a candidate", K.IGNORE, FieldClassifier.classify(d(it = IT.TYPE_CLASS_TEXT or IT.TYPE_TEXT_FLAG_MULTI_LINE)))
    println(if (failed == 0) "ALL AUTOFILL TESTS PASSED" else "FAILED: $failed")
    System.exit(if (failed == 0) 0 else 1)
}
