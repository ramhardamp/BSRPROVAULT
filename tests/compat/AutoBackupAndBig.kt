import com.babasitaram.pro.*
import com.google.gson.*
import kotlin.coroutines.*
fun <T> run(block: suspend () -> T): T { var out: Result<T>? = null; block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it }); return out!!.getOrThrow() }
fun fakeCtx(): android.content.Context {
    val f = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe"); f.isAccessible = true
    return (f.get(null) as sun.misc.Unsafe).allocateInstance(android.content.ContextWrapper::class.java) as android.content.Context
}
fun main() {
    val dir = "/home/claude/xfer/"; val master = "Māster#Pass9"
    var t = System.nanoTime()
    val r = run { BackupManager.importBackup(fakeCtx(), java.io.File(dir + "ext_auto.vaultbak").readText(), master) }
    val list = (r as BackupManager.ImportResult.Success).data.passwords
    println("AUTO-BACKUP (real VaultCrypto wrapper): ${list.size} entries (meta records skipped: " + (list.none { it.site.isEmpty() }) + ")")
    val g = list.find { it.site == "GitHub" }!!
    println("  GitHub folder=" + g.folder + " tags=" + g.tags + " history=" + g.history.size + " starred=" + g.isFavorite + " totp=" + g.totp.isNotEmpty())
    println("  Aadhaar idExpiry=" + list.find { it.site == "Aadhaar" }!!.idExpiry + " | folder=" + list.find { it.site == "Aadhaar" }!!.folder)
    t = System.nanoTime()
    val rb = run { BackupManager.importBackup(fakeCtx(), java.io.File(dir + "ext_big.vaultbak").readText(), master) }
    val big = (rb as BackupManager.ImportResult.Success).data.passwords
    println("BIG import: ${big.size} entries in ${(System.nanoTime() - t) / 1_000_000} ms (JVM; includes PBKDF2 + parse)")
    t = System.nanoTime()
    val out = BackupManager.buildExtensionBackup(master, big)
    println("BIG export to extension format: ${out.length} chars in ${(System.nanoTime() - t) / 1_000_000} ms")
    java.io.File(dir + "android_big.vaultbak").writeText(out)
}
