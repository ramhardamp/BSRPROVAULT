import com.babasitaram.pro.*
import kotlin.coroutines.*
fun <T> run(block: suspend () -> T): T { var out: Result<T>? = null; block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it }); return out!!.getOrThrow() }
fun fakeCtx(): android.content.Context {
    val f = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe"); f.isAccessible = true
    return (f.get(null) as sun.misc.Unsafe).allocateInstance(android.content.ContextWrapper::class.java) as android.content.Context
}
fun main() {
    val dir = "/home/claude/xfer/"
    val master = "Māster#Pass9"
    val r = run { BackupManager.importBackup(fakeCtx(), java.io.File(dir + "ext_vaultbak.vaultbak").readText(), master) }
    val list = (r as BackupManager.ImportResult.Success).data.passwords
    // Android me user ne kuch edit kiya: ek nayi Android-only category wali entry + ek password badla
    val edited = list.map { if (it.site == "SBI NetBanking") it.copy(password = "New#Sbi_2027", history = listOf(PwOld("S#bi_2026", 1800000000000L)) + it.history) else it } +
        PasswordEntry(site = "Amazon", url = "https://amazon.in", username = "shop@x.com", password = "Amz#1234", category = "Shopping", tags = listOf("prime"), folder = "Shopping stuff")
    java.io.File(dir + "android_ext.vaultbak").writeText(BackupManager.buildExtensionBackup(master, edited))
    java.io.File(dir + "android_ext_nofolders.vaultbak").writeText(BackupManager.buildExtensionBackup(master, edited.map { it.copy(folder = "", folderId = "") }))
    java.io.File(dir + "android_vault.csv").writeText(BackupManager.exportCsv(edited))
    java.io.File(dir + "android_chrome.csv").writeText(BackupManager.exportChromeCsv(edited))
    java.io.File(dir + "android_edited.json").writeText(com.google.gson.Gson().toJson(edited))
    println("android files written: " + edited.size + " entries")
}
