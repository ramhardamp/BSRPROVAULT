package com.babasitaram.pro

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.documentfile.provider.DocumentFile

/** Har badlav ke baad chune hue folder mein encrypted BSRPRO.Vault backup apne aap likhta hai. */
object AutoBackup {
    // Current standard: BSRPRO.Vault (V3) — APK, Chrome aur Firefox teeno mein import hoti hai.
    private const val FILE_NAME = "BSRPRO.Vault"
    // Purani auto-backup files: sirf restore ke liye padhi jaati hain (import compatibility).
    private const val LEGACY_VAULTBAK = "BabaSitaRam Pro Password ManagerPRO.vaultbak"
    // Purani .bsrpro auto-backup: naya backup safely likhne ke baad delete hoti hai. Naya .bsrpro kabhi nahi banta.
    private const val LEGACY_FILE_NAME = "BabaSitaRamPro-AutoBackup.bsrpro"
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun isConfigured(ctx: Context): Boolean {
        val tree = AppPrefs.getBackupTree(ctx) ?: return false
        return try {
            val root = DocumentFile.fromTreeUri(ctx, Uri.parse(tree))
            root?.isDirectory == true && root.canWrite()
        } catch (e: Exception) {
            Log.w("BSR_Backup", "configured tree invalid: " + e.javaClass.simpleName)
            false
        }
    }

    /** Main thread se call karein. 3 second ruk kar (debounce) background mein likhta hai. */
    fun schedule(ctx: Context) {
        val app = ctx.applicationContext
        if (!isConfigured(app)) return
        pending?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            val master = VaultManager.masterForBackup()
            val snapshot = VaultManager.getPasswords()
            if (master.isNotEmpty()) {
                Thread { write(app, master, snapshot) }.start()
            }
        }
        pending = r
        handler.postDelayed(r, 3000L)
    }



    /** Existing encrypted backup in the selected folder, if any. */
    fun hasExistingBackup(ctx: Context): Boolean {
        val tree = AppPrefs.getBackupTree(ctx) ?: return false
        return try {
            val root = DocumentFile.fromTreeUri(ctx, Uri.parse(tree))
            root?.findFile(FILE_NAME)?.isFile == true || root?.findFile(LEGACY_VAULTBAK)?.isFile == true ||
                root?.findFile(LEGACY_FILE_NAME)?.isFile == true
        } catch (_: Exception) { false }
    }

    /** Reads the existing encrypted backup without modifying the current vault. */
    fun readExistingBackup(ctx: Context): String? {
        val tree = AppPrefs.getBackupTree(ctx) ?: return null
        return try {
            val root = DocumentFile.fromTreeUri(ctx, Uri.parse(tree)) ?: return null
            val file = root.findFile(FILE_NAME)?.takeIf { it.isFile }
                ?: root.findFile(LEGACY_VAULTBAK)?.takeIf { it.isFile }
                ?: root.findFile(LEGACY_FILE_NAME)?.takeIf { it.isFile }
                ?: return null
            ctx.contentResolver.openInputStream(file.uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (_: Exception) { null }
    }

    /** Turant backup. Result main thread par: null = success, warna error message. */
    fun runNow(ctx: Context, onDone: (String?) -> Unit) {
        val app = ctx.applicationContext
        val master = VaultManager.masterForBackup()
        val snapshot = VaultManager.getPasswords()
        Thread {
            val err = if (master.isEmpty()) "Vault locked hai" else write(app, master, snapshot)
            handler.post { onDone(err) }
        }.start()
    }

    private val writeLock = Any()

    private fun write(ctx: Context, master: String, list: List<PasswordEntry>): String? {
        synchronized(writeLock) { return writeLocked(ctx, master, list) }
    }

    private fun writeLocked(ctx: Context, master: String, list: List<PasswordEntry>): String? {
        val tree = AppPrefs.getBackupTree(ctx) ?: return "Folder chuna nahi gaya"
        return try {
            val json = BackupManager.buildAutoBackup(master, list)
            val root = DocumentFile.fromTreeUri(ctx, Uri.parse(tree))
                ?: return "Folder access nahi mila — dobara folder chunein"
            if (!root.isDirectory) return "Saved backup location folder nahi hai — dobara folder chunein"
            if (!root.canWrite()) return "Folder mein write permission nahi — dobara folder chunein"
            var file = root.findFile(FILE_NAME)
            if (file != null && !file.isFile) {
                runCatching { file!!.delete() }
                file = null
            }
            val outputFile = if (file != null) file else root.createFile("application/octet-stream", FILE_NAME)
                ?: return "Backup file nahi ban payi"
            // Kuch providers naam ke peeche ".bin" jod dete hain — tab agli baar file mil nahi paati aur duplicate ban-te hain.
            if (outputFile.name != FILE_NAME) runCatching { outputFile.renameTo(FILE_NAME) }
            val stream = try {
                ctx.contentResolver.openOutputStream(outputFile.uri, "wt")
                    ?: ctx.contentResolver.openOutputStream(outputFile.uri, "w")
            } catch (_: Exception) {
                ctx.contentResolver.openOutputStream(outputFile.uri, "w")
            } ?: return "File open nahi hui"
            stream.use { out -> out.write(json.toByteArray(Charsets.UTF_8)) }
            // Naya BSRPRO.Vault safely likha ja chuka — ab purani .bsrpro auto-backup delete karo.
            runCatching { root.findFile(LEGACY_FILE_NAME)?.takeIf { it.isFile }?.delete() }
            AppPrefs.setLastAutoBackup(ctx, System.currentTimeMillis())
            null
        } catch (e: Exception) {
            Log.e("BSR_Backup", "auto-backup write failed: " + e.javaClass.simpleName)
            "Backup error: " + (e.message ?: "unknown error")
        }
    }
}
