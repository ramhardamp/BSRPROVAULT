import com.babasitaram.pro.*
import java.io.File
import kotlin.coroutines.*

fun <T> runSusp(block: suspend () -> T): T {
    var res: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) { res = result }
    })
    return res!!.getOrThrow()
}
var fails = 0
fun check(name: String, ok: Boolean) { println((if (ok) "PASS  " else "FAIL  ") + name); if (!ok) fails++ }
fun sample() = listOf(
    PasswordEntry(site = "GitHub", url = "https://github.com", username = "u1", password = "p1-ünï", folder = "Work", folderId = "f-work"),
    PasswordEntry(site = "Bank", url = "https://bank.example", username = "u2", password = "p2", folder = "Money"),
    PasswordEntry(site = "Blog", url = "https://blog.example", username = "u3", password = "p3")
)
fun imp(text: String, pw: String) = runSusp { BackupManager.importBackup(DummyCtx() as android.content.Context, text, pw) }

fun main(args: Array<String>) {
    val pw = "Master#123"
    when (args.getOrNull(0)) {
        "import" -> {   // import <file> <pw>
            val r = imp(File(args[1]).readText(), args[2])
            if (r is BackupManager.ImportResult.Success) println("IMPORT_OK count=" + r.data.passwords.size + " titles=" + r.data.passwords.map { it.site } + " folders=" + r.data.passwords.map { it.folder })
            else println("IMPORT_ERROR " + (r as BackupManager.ImportResult.Error).message.replace("\n", " | "))
            return
        }
        "export" -> {   // export <file> <pw>
            ExportGate.issue()
            File(args[1]).writeText(BackupManager.buildBsrProVault(args[2], sample(), true)); ExportGate.revoke(); println("EXPORTED"); return
        }
    }
    // 1. guard: nothing sensitive is produced without a fresh grant
    check("builder blocked without verification (BSRPRO.Vault)", try { BackupManager.buildBsrProVault(pw, sample()); false } catch (e: SecurityException) { true })
    check("builder blocked without verification (CSV)", try { BackupManager.exportCsv(sample()); false } catch (e: SecurityException) { true })
    check("builder blocked without verification (Chrome CSV)", try { BackupManager.exportChromeCsv(sample()); false } catch (e: SecurityException) { true })
    check("auto-backup refuses when vault locked", try { BackupManager.buildAutoBackup(pw, sample()); false } catch (e: IllegalStateException) { true })
    // 2. with grant -> works, then revoke -> blocked again
    ExportGate.issue()
    val v3 = BackupManager.buildBsrProVault(pw, sample(), true)
    val csv = BackupManager.exportCsv(sample())
    ExportGate.revoke()
    check("export works with grant", BsrVault.isV3(v3) && csv.startsWith("name,url"))
    check("grant revoked after export", try { BackupManager.exportCsv(sample()); false } catch (e: SecurityException) { true })
    // 3. roundtrip
    val ok = imp(v3, pw)
    check("import roundtrip: 3 entries", ok is BackupManager.ImportResult.Success && ok.data.passwords.size == 3)
    if (ok is BackupManager.ImportResult.Success) {
        check("passwords preserved (unicode)", ok.data.passwords.any { it.password == "p1-ünï" })
        check("folders preserved", ok.data.passwords.first { it.site == "GitHub" }.folder == "Work" && ok.data.passwords.first { it.site == "Bank" }.folder == "Money")
    }
    // 4. wrong password / corrupted / tampered -> whole import fails, nothing returned
    check("wrong password -> Error", imp(v3, "nope") is BackupManager.ImportResult.Error)
    val raw = java.util.Base64.getDecoder().decode(v3)
    fun flip(i: Int): String { val b = raw.copyOf(); b[i] = (b[i].toInt() xor 1).toByte(); return java.util.Base64.getEncoder().encodeToString(b) }
    check("tampered ciphertext -> Error", imp(flip(raw.size - 5), pw) is BackupManager.ImportResult.Error)
    check("tampered salt -> Error", imp(flip(12), pw) is BackupManager.ImportResult.Error)
    check("tampered iteration header -> Error", imp(flip(9), pw) is BackupManager.ImportResult.Error)
    check("truncated file -> Error", imp(v3.substring(0, v3.length / 2), pw) is BackupManager.ImportResult.Error)
    // 5. authenticated-but-inconsistent payloads are rejected as a whole
    fun craft(json: String) = BsrVault.encrypt(pw, json.toByteArray())
    check("count mismatch -> Error", imp(craft("""{"_bsrOrigin":"BABASITARAMPro:x","format":"BSRPRO.Vault","version":3,"count":5,"data":"[{\"title\":\"a\",\"password\":\"b\"}]"}"""), pw) is BackupManager.ImportResult.Error)
    check("malformed entry -> Error", imp(craft("""{"_bsrOrigin":"BABASITARAMPro:x","format":"BSRPRO.Vault","version":3,"count":2,"data":"[{\"title\":\"a\",\"password\":\"b\"}, 7]"}"""), pw) is BackupManager.ImportResult.Error)
    check("foreign origin -> Error", imp(craft("""{"_bsrOrigin":"EvilApp","format":"BSRPRO.Vault","version":3,"count":1,"data":"[{\"title\":\"a\",\"password\":\"b\"}]"}"""), pw) is BackupManager.ImportResult.Error)
    check("wrong format tag -> Error", imp(craft("""{"_bsrOrigin":"BABASITARAMPro:x","format":"Other","version":3,"count":1,"data":"[{\"title\":\"a\",\"password\":\"b\"}]"}"""), pw) is BackupManager.ImportResult.Error)
    check("valid crafted payload accepted", imp(craft("""{"_bsrOrigin":"BABASITARAMPro:chrome","format":"BSRPRO.Vault","version":3,"count":1,"data":"{\"entries\":[{\"title\":\"a\",\"url\":\"u\",\"username\":\"x\",\"password\":\"b\"}],\"folders\":[]}"}"""), pw) is BackupManager.ImportResult.Success)
    println(if (fails == 0) "ALL KOTLIN INTEGRATION CHECKS PASSED" else "FAILURES: $fails")
    System.exit(if (fails == 0) 0 else 1)
}
