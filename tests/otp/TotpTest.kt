import com.babasitaram.pro.Totp
import com.babasitaram.pro.OtpDisplay

var fails = 0
fun check(name: String, got: Any?, want: Any?) {
    if (got != want) { fails++; println("FAIL $name: got=$got want=$want") } else println("ok   $name")
}

fun main() {
    val s1 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    val s256 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA===="
    val s512 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA="
    val times = longArrayOf(59, 1111111109, 1111111111, 1234567890, 2000000000, 20000000000)
    val e1 = listOf("94287082","07081804","14050471","89005924","69279037","65353130")
    val e256 = listOf("46119246","68084774","67062674","91819424","90698825","77737706")
    val e512 = listOf("90693936","25091201","99943326","93441116","38618901","47863826")
    for (i in times.indices) {
        val ms = times[i] * 1000
        check("RFC SHA1   t=${times[i]}", Totp.code("otpauth://totp/x?secret=$s1&digits=8", ms), e1[i])
        check("RFC SHA256 t=${times[i]}", Totp.code("otpauth://totp/x?secret=$s256&digits=8&algorithm=SHA256", ms), e256[i])
        check("RFC SHA512 t=${times[i]}", Totp.code("otpauth://totp/x?secret=$s512&digits=8&algorithm=SHA512", ms), e512[i])
    }
    // plain base32 -> 6 digits, SHA1, 30s (last 6 of the 8-digit RFC value)
    check("plain base32 6 digits", Totp.code(s1, 59_000), "287082")
    check("plain base32 spaced/lowercase", Totp.code("gezd gnbv-gy3t qojq gezd gnbv gy3t qojq", 59_000), "287082")
    check("period 60 counter", Totp.code("otpauth://totp/x?secret=$s1&period=60", 59_000), Totp.code(s1, 0))
    check("secondsLeft @59s", Totp.secondsLeft(s1, 59_000), 1)
    check("secondsLeft period60 @59s", Totp.secondsLeft("otpauth://totp/x?secret=$s1&period=60", 59_000), 1)
    // reject
    check("hotp rejected", Totp.parse("otpauth://hotp/x?secret=$s1&counter=1"), null)
    check("garbage rejected", Totp.parse("not a secret!!"), null)
    check("empty rejected", Totp.parse("   "), null)
    check("uri without secret rejected", Totp.parse("otpauth://totp/Netflix:me@x.com?issuer=Netflix"), null)
    // label parsing
    val p = Totp.parse("otpauth://totp/Netflix:me@x.com?secret=GEZDGNBVGY3TQOJQ&issuer=Netflix&digits=6&period=30")!!
    check("uri issuer", p.issuer, "Netflix"); check("uri account", p.account, "me@x.com")
    val p2 = Totp.parse("otpauth://totp/Google%3Ahackpcfake%40gmail.com?secret=GEZDGNBVGY3TQOJQ")!!
    check("encoded label issuer", p2.issuer, "Google"); check("encoded label account", p2.account, "hackpcfake@gmail.com")
    val p3 = Totp.parse("otpauth://totp/Old:me@x.com?secret=GEZDGNBVGY3TQOJQ&issuer=New")!!
    check("issuer param overrides", p3.issuer, "New")
    // format
    check("format 6", Totp.format("294206"), "294 206")
    check("format 8", Totp.format("12345678"), "1234 5678")
    check("format 7", Totp.format("1234567"), "1234 567")
    // Params overload equals String overload
    check("Params overload", Totp.code(p, 59_000), Totp.code("otpauth://totp/Netflix:me@x.com?secret=GEZDGNBVGY3TQOJQ&issuer=Netflix&digits=6&period=30", 59_000))

    // ---- OtpDisplay (matches the screenshots) ----
    fun l(site: String?, url: String?, user: String?, email: String? = null, mobile: String? = null, full: String? = null, totp: String? = "GEZDGNBVGY3TQOJQ") =
        OtpDisplay.label(site, url, user, email, mobile, full, totp).text
    check("google host title", l("accounts.google.com", "https://accounts.google.com", "hackpcfake@gmail.com"), "Google: hackpcfake@gmail.com")
    check("mobile only account", l("accounts.google.com", "https://accounts.google.com", "", mobile = "9200313111"), "Google: 9200313111")
    check("non-email username", l("accounts.google.com", null, "VIKRAM56IN"), "Google: VIKRAM56IN")
    check("email beats username", l("accounts.google.com", null, "VIKRAM56IN", mobile = "x@y.com"), "Google: x@y.com")
    check("github", l("github.com", "https://github.com/login", "ramhardamp"), "GitHub: ramhardamp")
    check("friendly title", l("Mozilla", "https://accounts.firefox.com", "ramhardamp@gmail.com"), "Mozilla: ramhardamp@gmail.com")
    check("eu.org keeps host", l("eu.org", "https://eu.org", "VSR6-FREE"), "eu.org: VSR6-FREE")
    check("uri issuer wins", l("whatever", "https://x.com", "u@x.com", totp = "otpauth://totp/Netflix:me@x.com?secret=GEZDGNBVGY3TQOJQ&issuer=Netflix"), "Netflix: u@x.com")
    check("uri account fallback", l("", "", "", totp = "otpauth://totp/Netflix:me@x.com?secret=GEZDGNBVGY3TQOJQ"), "Netflix: me@x.com")
    check("email-titled entry not issuer", l("me@x.com", "https://www.bbc.co.uk", "me@x.com"), "Bbc: me@x.com")
    check("nothing at all", l(null, null, null, totp = null), "Account")
    check("host brand co.in", OtpDisplay.hostToBrand("sub.example.co.in"), "Example")
    check("host brand ms", OtpDisplay.hostToBrand("login.microsoftonline.com"), "Microsoft")
    println(if (fails == 0) "ALL PASS" else "FAILURES: $fails")
    if (fails != 0) kotlin.system.exitProcess(1)
}
