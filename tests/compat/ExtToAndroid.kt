import com.babasitaram.pro.*
import com.google.gson.*
import kotlin.coroutines.*

fun <T> run(block: suspend () -> T): T { var out: Result<T>? = null; block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it }); return out!!.getOrThrow() }
fun fakeCtx(): android.content.Context {
    val f = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe"); f.isAccessible = true
    return (f.get(null) as sun.misc.Unsafe).allocateInstance(android.content.ContextWrapper::class.java) as android.content.Context
}
fun imp(text: String, pw: String): List<PasswordEntry>? {
    val r = run { BackupManager.importBackup(fakeCtx(), text, pw) }
    return when (r) { is BackupManager.ImportResult.Success -> r.data.passwords; is BackupManager.ImportResult.Error -> { println("   IMPORT ERROR: " + r.message.replace("\n"," ")); null } }
}
var fails = 0
fun s(o: JsonObject, k: String) = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""
fun chk(fmt: String, what: String, exp: Any?, got: Any?) { if (exp != got) { fails++; println("  MISMATCH [$fmt] $what: expected=<$exp> got=<$got>") } }

fun main(args: Array<String>) {
    val dir = "/home/claude/xfer/"
    val src = JsonParser.parseString(java.io.File(dir + "source.json").readText()).asJsonObject
    val master = src.get("master").asString
    val folderName = HashMap<String, String>()
    for (f in src.getAsJsonArray("folders")) folderName[s(f.asJsonObject, "id")] = s(f.asJsonObject, "name")
    val catMap = mapOf("work" to "Work", "personal" to "Personal", "banking" to "Banking", "social" to "Social", "other" to "Other")

    // ---- full fidelity formats ----
    for (file in listOf("ext_vaultbak.vaultbak", "ext_json.json", "ext_plain-json.json")) {
        println("== $file")
        val list = imp(java.io.File(dir + file).readText(), master) ?: continue
        chk(file, "count", 7, list.size)
        for (el in src.getAsJsonArray("entries")) {
            val o = el.asJsonObject; val t = s(o, "title")
            val a = list.find { it.site == t }
            if (a == null) { fails++; println("  MISSING entry $t"); continue }
            chk(file, "$t.id preserved", s(o, "id"), a.id)
            chk(file, "$t.url", s(o, "url"), a.url)
            chk(file, "$t.username", s(o, "username"), a.username)
            chk(file, "$t.mobile", s(o, "mobile"), a.mobile)
            chk(file, "$t.password", s(o, "password"), a.password)
            chk(file, "$t.notes", s(o, "notes"), a.notes)
            chk(file, "$t.type", s(o, "recordType"), a.type)
            chk(file, "$t.category", catMap[s(o, "category")], a.category)
            // folders sirf .vaultbak mein aate hain (extension ka apna niyam) — json export mein folder list nahi hoti
            if (file.endsWith(".vaultbak")) chk(file, "$t.folder", folderName[s(o, "folderId")] ?: "", a.folder)
            chk(file, "$t.totp", s(o, "totp"), a.totp)
            chk(file, "$t.starred", o.get("starred").asBoolean, a.isFavorite)
            chk(file, "$t.tags", o.getAsJsonArray("tags").map { it.asString }, a.tags)
            chk(file, "$t.customFields", o.getAsJsonArray("customFields").map { it.asJsonObject.let { f -> s(f, "k") to s(f, "v") } }, a.fields.map { it.k to it.v })
            chk(file, "$t.history", o.getAsJsonArray("passwordHistory").map { it.asJsonObject.let { h -> s(h, "pw") to s(h, "changedAt").toLong() } }, a.history.map { it.pw to it.at })
            for ((k, g) in listOf("cardNumber" to a.cardNumber, "cardholder" to a.cardholder, "cardExpiry" to a.cardExpiry, "cardCvv" to a.cardCvv,
                "fullName" to a.fullName, "email" to a.email, "phone" to a.phone, "address" to a.address, "idNumber" to a.idNumber)) chk(file, "$t.$k", s(o, k), g)
            chk(file, "$t.createdAt", s(o, "createdAt").toLong(), a.createdAt)
            chk(file, "$t.idExpiry", s(o, "idExpiry"), a.idExpiry)
        }
    }
    // ---- lossy-by-design formats: check only what the format carries ----
    for ((file, cols) in listOf(
        "ext_csv.csv" to listOf("title", "url", "username", "mobile", "password", "notes", "starred"),
        "ext_vault.csv" to listOf("title", "url", "username", "mobile", "password", "notes", "starred"),
        "ext_chrome.csv" to listOf("title", "url", "username", "password"),
        "ext_bitwarden.csv" to listOf("title", "url", "username", "password", "notes", "starred"),
        "ext_lastpass.csv" to listOf("title", "url", "username", "password", "notes", "starred"),
        "ext_keepass.xml" to listOf("title", "url", "username", "password", "notes"))) {
        println("== $file")
        val list = imp(java.io.File(dir + file).readText(), master) ?: continue
        for (el in src.getAsJsonArray("entries")) {
            val o = el.asJsonObject; val t = s(o, "title")
            val a = list.find { it.site == t } ?: run { fails++; println("  MISSING entry $t"); null } ?: continue
            for (c in cols) {
                val exp: Any? = if (c == "starred") o.get("starred").asBoolean else s(o, c)
                val got: Any? = when (c) { "title" -> a.site; "url" -> a.url; "username" -> a.username; "mobile" -> a.mobile; "password" -> a.password; "notes" -> a.notes; else -> a.isFavorite }
                chk(file, "$t.$c", exp, got)
            }
        }
    }
    println(if (fails == 0) "ALL EXT->ANDROID CHECKS PASSED" else "MISMATCHES: $fails")
}
