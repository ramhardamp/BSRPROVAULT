import com.babasitaram.pro.*
import kotlin.coroutines.*

fun ok(name: String, cond: Boolean) { println((if (cond) "PASS " else "FAIL ") + name); if (!cond) { failed++ } }
var failed = 0

fun <T> run(block: suspend () -> T): T {
    var out: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
    return out!!.getOrThrow()
}

fun fakeCtx(): android.content.Context {
    val f = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe"); f.isAccessible = true
    val u = f.get(null) as sun.misc.Unsafe
    return u.allocateInstance(android.content.ContextWrapper::class.java) as android.content.Context
}

fun imp(text: String, pw: String): List<PasswordEntry> {
    val r = run { BackupManager.importBackup(fakeCtx(), text, pw) }
    return when (r) {
        is BackupManager.ImportResult.Success -> r.data.passwords
        is BackupManager.ImportResult.Error -> { println("   (error: " + r.message + ")"); emptyList() }
    }
}

fun main() {
    val M = "Māster#7"
    for (file in listOf("auto540.vaultbak", "manual540.vaultbak")) {
        val list = imp(java.io.File(file).readText(), M)
        ok("$file: 4 entries (activityLog skipped)", list.size == 4)
        val g = list.find { it.site == "Gmail" }
        ok("$file: login mapped", g != null && g.type == "login" && g.username == "" && g.mobile == "9876543210" && g.folder == "Office" &&
            g.tags == listOf("work", "mail") && g.fields.size == 1 && g.fields[0].k == "PIN" && g.totp.isNotEmpty() &&
            g.isFavorite && g.history.size == 2 && g.history[0].pw == "old1" && g.category == "Email")
        val c = list.find { it.site == "HDFC Visa" }
        ok("$file: card mapped", c != null && c.type == "card" && c.cardNumber == "4111111111111111" &&
            c.cardholder == "Baba Sita Ram" && c.cardExpiry == "08/24" && c.cardCvv == "123" && c.category == "Banking")
        val i = list.find { it.site == "Aadhaar" }
        ok("$file: identity mapped", i != null && i.type == "identity" && i.fullName == "Baba Sita Ram" &&
            i.idNumber == "1234 5678 9012" && i.folder == "Govt IDs" && i.address == "Indore, MP")
        val n = list.find { it.site == "Wifi note" }
        ok("$file: note mapped", n != null && n.type == "note" && n.notes.contains("हिन्दी"))
    }
    ok("wrong password rejected", imp(java.io.File("auto540.vaultbak").readText(), "nope").isEmpty())

    // Android own format roundtrip (regression: vault_backup marker + sig)
    val src = imp(java.io.File("auto540.vaultbak").readText(), M)
    val own = BackupManager.buildBackupJson("MyPass#1", src)
    val back = imp(own, "MyPass#1")
    ok("own .bsrpro roundtrip count", back.size == src.size)
    ok("own .bsrpro keeps card+identity+folder+history", back.any { it.type == "card" && it.cardNumber == "4111111111111111" } &&
        back.any { it.type == "identity" && it.folder == "Govt IDs" } && back.any { it.history.size == 2 })
    ok("own .bsrpro wrong password rejected", imp(own, "bad").isEmpty())

    // Android -> extension format -> our importer (self-check) + file for Python verification
    val extText = BackupManager.buildExtensionBackup("ExtPass#9", src)
    java.io.File("android_to_ext.vaultbak").writeText(extText)
    val back2 = imp(extText, "ExtPass#9")
    ok("android->ext->android roundtrip", back2.size == src.size && back2.any { it.site == "Gmail" && it.folder == "Office" && it.tags.contains("work") && it.tags.contains("mail") && it.category == "Email" })

    // KeePass
    val kp = imp(java.io.File("keepass_std.xml").readText(), "")
    ok("keepass std: 2 entries, history ignored", kp.size == 2 && kp.none { it.site == "OLDVERSION" })
    val insta = kp.find { it.site == "Insta" }
    ok("keepass std: fields+folder+otp", insta != null && insta.username == "ig_user" && insta.password == "pw&1" && insta.folder == "Social" && insta.totp.isNotEmpty())
    val kp2 = imp(java.io.File("keepass_ext.xml").readText(), "")
    ok("keepass extension-simple xml", kp2.size == 1 && kp2[0].username == "u" && kp2[0].url == "https://s.com")

    // NordPass CSV
    val np = imp(java.io.File("nordpass.csv").readText(), "")
    ok("nordpass: 4 rows", np.size == 4)
    ok("nordpass login", np.any { it.site == "Netflix" && it.type == "login" && it.folder == "Fun" && it.password == "pw9" })
    ok("nordpass card", np.any { it.type == "card" && it.cardNumber == "5555444433331111" && it.cardCvv == "999" && it.cardExpiry == "12/30" })
    ok("nordpass identity", np.any { it.type == "identity" && it.fullName == "Ram Sita" && it.email == "ram@x.com" && it.address == "12, MG Road" })
    ok("nordpass note", np.any { it.type == "note" && it.notes == "secret note" })

    // Vault crypto: cached key, old-format compat (via reflection on private members)
    val vm = VaultManager::class.java
    val inst = vm.getField("INSTANCE").get(null)
    fun setF(n: String, v: Any?) { val f = vm.getDeclaredField(n); f.isAccessible = true; f.set(inst, v) }
    fun getF(n: String): Any? { val f = vm.getDeclaredField(n); f.isAccessible = true; return f.get(inst) }
    setF("_master", "VaultPw#1"); setF("_salt", null); setF("_key", null)
    val encM = vm.getDeclaredMethod("encryptVault", String::class.java); encM.isAccessible = true
    val json = com.google.gson.Gson().toJson(src)
    val b1 = encM.invoke(inst, json) as String
    val b2 = encM.invoke(inst, json) as String
    val salt1 = java.util.Base64.getDecoder().decode(b1).copyOfRange(0, 16)
    val salt2 = java.util.Base64.getDecoder().decode(b2).copyOfRange(0, 16)
    ok("cached key: same salt, different IV/ciphertext", salt1.contentEquals(salt2) && b1 != b2)
    // old-format decrypt (dec()) must read new saves
    val decM = vm.getDeclaredMethod("dec", String::class.java, String::class.java); decM.isAccessible = true
    ok("old dec() reads new blob", (decM.invoke(inst, b2, "VaultPw#1") as String) == json)
    // old enc() blob must be loadable by new loader
    val oldEnc = vm.getDeclaredMethod("enc", String::class.java, String::class.java); oldEnc.isAccessible = true
    val legacyBlob = oldEnc.invoke(inst, json, "VaultPw#1") as String
    val tl = vm.getDeclaredMethod("tryLoad", String::class.java, String::class.java); tl.isAccessible = true
    setF("_passwords", mutableListOf<PasswordEntry>())
    ok("legacy blob loads with new loader", tl.invoke(inst, legacyBlob, "VaultPw#1") as Boolean && VaultManager.getPasswords().size == src.size)
    ok("wrong password fails to load (no silent empty)", !(tl.invoke(inst, legacyBlob, "wrong") as Boolean))
    ok("history/card survive vault reload", VaultManager.getPasswords().any { it.type == "card" && it.cardNumber == "4111111111111111" } && VaultManager.getPasswords().any { it.history.size == 2 })

    println(if (failed == 0) "ALL TESTS PASSED" else "FAILED: $failed")
    System.exit(if (failed == 0) 0 else 1)
}
