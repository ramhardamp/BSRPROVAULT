package com.babasitaram.pro

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class CustomField(val k: String = "", val v: String = "")

data class PwOld(val pw: String = "", val at: Long = 0L)

data class PasswordEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val site: String = "",
    val url: String = "",
    // v6.6 — optional app link; missing fields in old vault JSON deserialize as null.
    val appPackage: String? = null,
    val appName: String? = null,
    // v6.7.5 — preferred/default login for the linked native app. Old vault JSON remains compatible.
    val isPrimaryAppLogin: Boolean = false,
    val username: String = "",
    val password: String = "",
    val notes: String = "",
    val category: String = "Other",
    val totp: String = "",
    val type: String = "login",
    val deletedAt: Long = 0L,
    val history: List<PwOld> = emptyList(),
    // v6.3 — extension v5.40 ke Cards / Identity / Folders / Tags / Custom fields
    val mobile: String = "",
    val folder: String = "",
    val folderId: String = "",   // extension ke folder ka id (round-trip mein folder bane rahein)
    val tags: List<String> = emptyList(),
    val fields: List<CustomField> = emptyList(),
    val cardNumber: String = "",
    val cardholder: String = "",
    val cardExpiry: String = "",
    val cardCvv: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val idNumber: String = "",
    val idExpiry: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

object VaultManager {
    private const val TAG = "BSR_Vault"
    private val gson = Gson()
    private var _master = ""
    private var _salt: ByteArray? = null      // vault ka salt (blob se)
    private var _key: SecretKeySpec? = null    // PBKDF2 sirf ek baar — har save par nahi
    private var _passwords: MutableList<PasswordEntry> = mutableListOf()
    val isUnlocked get() = _master.isNotEmpty()

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("bsr_v3", Context.MODE_PRIVATE)

    private fun hash(pw: String): String =
        MessageDigest.getInstance("SHA-256").digest(pw.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun key(pw: String, salt: ByteArray, iterations: Int = 65536): SecretKeySpec {
        val spec = PBEKeySpec(pw.toCharArray(), salt, iterations, 256)
        return SecretKeySpec(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded, "AES"
        )
    }

    private fun enc(data: String, pw: String): String {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pw, salt), GCMParameterSpec(128, iv))
        val ct = c.doFinal(data.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(salt + iv + ct, android.util.Base64.NO_WRAP)
    }

    private fun dec(data: String, pw: String): String {
        val b = android.util.Base64.decode(data, android.util.Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(pw, b.sliceArray(0..15)), GCMParameterSpec(128, b.sliceArray(16..27)))
        return String(c.doFinal(b.sliceArray(28 until b.size)), Charsets.UTF_8)
    }

    fun isSetupDone(ctx: Context): Boolean {
        return try { prefs(ctx).getBoolean("sd", false) } catch (e: Exception) { false }
    }

    private fun verifier(pw: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pw.toCharArray(), salt, 210000, 256)
        val h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return android.util.Base64.encodeToString(h, android.util.Base64.NO_WRAP)
    }

    private fun writeVerifier(ctx: Context, pw: String): Boolean {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        return prefs(ctx).edit()
            .putString("ms", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .putString("mh2", verifier(pw, salt))
            .remove("mh")
            .putBoolean("sd", true)
            .commit()
    }

    fun setupMaster(ctx: Context, pw: String): Boolean {
        return try { writeVerifier(ctx, pw) } catch (e: Exception) { false }
    }

    fun verifyMaster(ctx: Context, pw: String): Boolean {
        return try {
            val p = prefs(ctx)
            val ms = p.getString("ms", null)
            val mh2 = p.getString("mh2", null)
            if (ms != null && mh2 != null) {
                val salt = android.util.Base64.decode(ms, android.util.Base64.NO_WRAP)
                MessageDigest.isEqual(
                    verifier(pw, salt).toByteArray(Charsets.UTF_8),
                    mh2.toByteArray(Charsets.UTF_8)
                )
            } else {
                // Purana (v5.4) format — sahi password par naye format mein upgrade kar do
                val legacy = p.getString("mh", null) ?: return false
                val ok = MessageDigest.isEqual(
                    legacy.toByteArray(Charsets.UTF_8),
                    hash(pw).toByteArray(Charsets.UTF_8)
                )
                if (ok) writeVerifier(ctx, pw)
                ok
            }
        } catch (e: Exception) { false }
    }

    private fun randomBytes(n: Int): ByteArray =
        ByteArray(n).also { java.security.SecureRandom().nextBytes(it) }

    /** Vault ko cached key se encrypt karta hai (format wahi: salt16 + iv12 + ciphertext). */
    private fun encryptVault(data: String): String {
        var salt = _salt
        var k = _key
        if (salt == null || k == null) {
            salt = randomBytes(16)
            k = key(_master, salt)
            _salt = salt
            _key = k
        }
        val iv = randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, iv))
        val ct = c.doFinal(data.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(salt + iv + ct, android.util.Base64.NO_WRAP)
    }

    private fun tryLoad(raw: String, pw: String): Boolean {
        return try {
            val b = android.util.Base64.decode(raw, android.util.Base64.NO_WRAP)
            val salt = b.copyOfRange(0, 16)
            val k = key(pw, salt)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, b.copyOfRange(16, 28)))
            val json = String(c.doFinal(b, 28, b.size - 28), Charsets.UTF_8)
            val list: List<PasswordEntry>? =
                gson.fromJson(json, object : TypeToken<List<PasswordEntry>>() {}.type)
            _passwords = (list ?: emptyList()).toMutableList()
            _salt = salt
            _key = k
            true
        } catch (e: Exception) { false }
    }

    /** true = vault load ho gaya (ya vault khali hai). false = file kharab — us par kabhi overwrite nahi. */
    private fun loadInto(ctx: Context, pw: String): Boolean {
        val raw = prefs(ctx).getString("pw", null)
        if (raw == null) {
            _passwords = mutableListOf()
            _salt = null
            _key = null
            return true
        }
        if (tryLoad(raw, pw)) return true
        val prev = prefs(ctx).getString("pw_prev", null)
        return prev != null && tryLoad(prev, pw)
    }

    @Synchronized
    fun unlock(ctx: Context, pw: String): Boolean {
        if (!verifyMaster(ctx, pw)) return false
        _master = pw
        if (!loadInto(ctx, pw)) {
            // Vault file padh nahi payi — khali vault se overwrite na ho, isliye unlock nahi
            _master = ""
            _passwords = mutableListOf()
            Log.e(TAG, "vault load failed")
            return false
        }
        val cutoff = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        _passwords.removeAll { it.deletedAt in 1 until cutoff }
        return true
    }

    @Synchronized
    fun lock() {
        _master = ""
        _salt = null
        _key = null
        _passwords.clear()
    }

    /** Master password badalna: naya verifier + naye key se poora vault dobara encrypt. */
    @Synchronized
    fun changeMaster(ctx: Context, newPw: String): Boolean {
        if (!isUnlocked || newPw.isEmpty()) return false
        return try {
            // Build the new verifier and encrypted vault first, then commit both
            // in one SharedPreferences transaction to prevent a verifier/vault mismatch.
            val oldRaw = prefs(ctx).getString("pw", null)
            val newVerifierSalt = randomBytes(16)
            val newVerifier = verifier(newPw, newVerifierSalt)

            val newVaultSalt = randomBytes(16)
            val newKey = key(newPw, newVaultSalt)
            val iv = randomBytes(12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, newKey, GCMParameterSpec(128, iv))
            val ct = cipher.doFinal(gson.toJson(_passwords).toByteArray(Charsets.UTF_8))
            val encrypted = android.util.Base64.encodeToString(newVaultSalt + iv + ct, android.util.Base64.NO_WRAP)

            val ed = prefs(ctx).edit()
                .putString("ms", android.util.Base64.encodeToString(newVerifierSalt, android.util.Base64.NO_WRAP))
                .putString("mh2", newVerifier)
                .remove("mh")
                .putBoolean("sd", true)
                .putString("pw", encrypted)

            if (oldRaw != null) ed.putString("pw_prev", oldRaw) else ed.remove("pw_prev")
            if (!ed.commit()) return false

            _master = newPw
            _salt = newVaultSalt
            _key = newKey
            if (AppPrefs.getBiometric(ctx)) AppPrefs.saveMasterForBio(ctx, newPw)
            AutoBackup.schedule(ctx)
            true
        } catch (e: Exception) {
            Log.e(TAG, "changeMaster: " + e.message)
            false
        }
    }

    @Synchronized
    private fun savePw(ctx: Context): Boolean {
        return try {
            val old = prefs(ctx).getString("pw", null)
            val ed = prefs(ctx).edit()
            if (old != null) ed.putString("pw_prev", old)
            ed.putString("pw", encryptVault(gson.toJson(_passwords)))
            val ok = ed.commit()
            if (ok) AutoBackup.schedule(ctx)
            ok
        } catch (e: Exception) {
            Log.e(TAG, "savePw: ${e.message}")
            false
        }
    }

    @Synchronized
    fun getPasswords(): List<PasswordEntry> = _passwords.filter { it.deletedAt == 0L }
    @Synchronized
    fun getById(id: String): PasswordEntry? = _passwords.firstOrNull { it.id == id && it.deletedAt == 0L }
    fun getFavorites(): List<PasswordEntry> = getPasswords().filter { it.isFavorite }
    fun getByCategory(cat: String): List<PasswordEntry> = getPasswords().filter { it.category == cat }
    @Synchronized
    fun getTrash(): List<PasswordEntry> =
        _passwords.filter { it.deletedAt > 0L }.sortedByDescending { it.deletedAt }
    @Synchronized
    fun masterForBackup(): String = _master
    /** Marks exactly one active login as the primary credential for a native app package. */
    @Synchronized
    fun setPrimaryAppLogin(ctx: Context, entryId: String, appPackage: String?): Boolean {
        val pkg = appPackage?.trim().orEmpty()
        if (pkg.isEmpty()) return false
        return try {
            val snapshot = _passwords.toMutableList()
            val found = _passwords.any {
                it.id == entryId &&
                    it.deletedAt == 0L &&
                    it.type == "login" &&
                    it.appPackage.equals(pkg, ignoreCase = true)
            }
            if (!found) return false

            val now = System.currentTimeMillis()
            _passwords = _passwords.map { e ->
                if (e.deletedAt == 0L &&
                    e.type == "login" &&
                    e.appPackage.equals(pkg, ignoreCase = true)
                ) {
                    e.copy(
                        isPrimaryAppLogin = e.id == entryId,
                        updatedAt = if (e.id == entryId) now else e.updatedAt
                    )
                } else e
            }.toMutableList()

            if (!savePw(ctx)) {
                _passwords = snapshot
                false
            } else true
        } catch (e: Exception) {
            Log.e(TAG, "setPrimaryAppLogin: ${e.message}")
            false
        }
    }


    fun search(q: String): List<PasswordEntry> = getPasswords().filter {
        it.site.contains(q, true) || it.username.contains(q, true) ||
            it.url.contains(q, true) || it.category.contains(q, true) ||
            it.folder.contains(q, true) || it.fullName.contains(q, true) ||
            it.cardholder.contains(q, true) || it.tags.any { t -> t.contains(q, true) }
    }

    // alias for autofill service backward compat
    fun searchPasswords(q: String): List<PasswordEntry> = search(q)

    @Synchronized
    fun add(ctx: Context, e: PasswordEntry): Boolean {
        return try {
            _passwords.add(e)
            if (!savePw(ctx)) {
                _passwords.remove(e)
                false
            } else true
        } catch (e: Exception) {
            Log.e(TAG, "add: ${e.message}")
            false
        }
    }

    private fun hostKey(raw: String): String {
        val h = hostOf(raw)
        return h
    }

    /** Import ke waqt duplicate pehchanne ki key (extension ke deduplicateImport jaisi). */
    fun dedupKey(e: PasswordEntry): String = when (e.type) {
        "note" -> "n|" + e.site.trim().lowercase() + "|" + e.notes
        "card" -> "c|" + e.cardNumber.filter { it.isDigit() } + "|" + e.site.trim().lowercase()
        "identity" -> "i|" + e.fullName.trim().lowercase() + "|" + e.idNumber.filter { it.isLetterOrDigit() }
        else -> "l|" + (hostKey(e.url).ifEmpty { e.site.trim().lowercase() }) + "|" +
            e.username.trim().lowercase() + "|" + e.mobile.filter { it.isDigit() }
    }

    /**
     * Import/restore ke liye: duplicates hatao aur agar entry ka id vault mein (trash sameit) pehle se hai
     * to naya id do. Warna same id ki 2 entries ban jaati thi aur edit/delete galat entry par lagta tha.
     */
    @Synchronized
    fun mergeFresh(list: List<PasswordEntry>): List<PasswordEntry> {
        val seen = getPasswords().map { dedupKey(it) }.toMutableSet()
        val ids = _passwords.map { it.id }.toMutableSet()
        val out = ArrayList<PasswordEntry>()
        for (e in list) {
            if (!seen.add(dedupKey(e))) continue
            var entry = e
            if (entry.id.isEmpty() || !ids.add(entry.id)) {
                entry = entry.copy(id = java.util.UUID.randomUUID().toString())
                ids.add(entry.id)
            }
            out.add(entry)
        }
        return out
    }

    @Synchronized
    fun addAll(ctx: Context, list: List<PasswordEntry>): Boolean {
        if (list.isEmpty()) return true
        val old = _passwords.toMutableList()
        _passwords.addAll(list)
        if (!savePw(ctx)) {
            _passwords = old
            return false
        }
        return true
    }

    @Synchronized
    fun update(ctx: Context, e: PasswordEntry): Boolean {
        return try {
            val i = _passwords.indexOfFirst { it.id == e.id && it.deletedAt == 0L }
            if (i < 0) return false
            val old = _passwords[i]
            val now = System.currentTimeMillis()
            var hist = old.history
            if (old.type != "note" && old.password.isNotEmpty() && old.password != e.password) {
                hist = (listOf(PwOld(old.password, now)) + old.history).take(10)
            }
            _passwords[i] = e.copy(
                updatedAt = now,
                createdAt = old.createdAt,
                deletedAt = old.deletedAt,
                history = hist
            )
            if (!savePw(ctx)) {
                _passwords[i] = old
                false
            } else true
        } catch (e: Exception) {
            Log.e(TAG, "update: ${e.message}")
            false
        }
    }

    // Soft delete: Trash mein jaata hai, 30 din baad apne aap hamesha ke liye delete
    @Synchronized
    fun delete(ctx: Context, id: String): Boolean {
        val i = _passwords.indexOfFirst { it.id == id }
        if (i < 0) return false
        val old = _passwords[i]
        _passwords[i] = old.copy(deletedAt = System.currentTimeMillis())
        if (!savePw(ctx)) {
            _passwords[i] = old
            return false
        }
        return true
    }

    @Synchronized
    fun restore(ctx: Context, id: String): Boolean {
        val i = _passwords.indexOfFirst { it.id == id }
        if (i < 0) return false
        val old = _passwords[i]
        _passwords[i] = old.copy(deletedAt = 0L)
        if (!savePw(ctx)) {
            _passwords[i] = old
            return false
        }
        return true
    }

    @Synchronized
    fun deleteForever(ctx: Context, id: String): Boolean {
        val old = _passwords.toMutableList()
        _passwords.removeAll { it.id == id }
        if (!savePw(ctx)) {
            _passwords = old
            return false
        }
        return true
    }

    @Synchronized
    fun emptyTrash(ctx: Context): Boolean {
        val old = _passwords.toMutableList()
        _passwords.removeAll { it.deletedAt > 0L }
        if (!savePw(ctx)) {
            _passwords = old
            return false
        }
        return true
    }

    @Synchronized
    fun toggleFav(ctx: Context, id: String): Boolean {
        val i = _passwords.indexOfFirst { it.id == id }
        if (i < 0) return false
        val old = _passwords[i]
        _passwords[i] = old.copy(isFavorite = !old.isFavorite)
        if (!savePw(ctx)) {
            _passwords[i] = old
            return false
        }
        return true
    }

    @Synchronized
    fun resetAll(ctx: Context) {
        _master = ""
        _salt = null
        _key = null
        _passwords.clear()
        prefs(ctx).edit().clear().apply()
        // Auto-backup ka folder-pointer bhi hatao: warna naye khali vault ka pehla save
        // purani achchi .vaultbak backup ko overwrite kar deta tha. Folder dobara chunne par "Restore/Merge" poochhega.
        AppPrefs.setBackupTree(ctx, null)
        AppPrefs.setLastAutoBackup(ctx, 0L)
    }

    // alias for autofill service
    fun getForUrl(url: String): List<PasswordEntry> = getPasswordsForUrl(url)

    private fun hostOf(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return ""
        return try {
            java.net.URI(if (s.contains("://")) s else "https://$s")
                .host?.lowercase()?.removePrefix("www.") ?: ""
        } catch (e: Exception) { "" }
    }

    // Strict domain match: exact ya subdomain. ("evil-paypal.com" ko "paypal.com" se match NAHI karta)
    fun getPasswordsForUrl(url: String): List<PasswordEntry> {
        val domain = hostOf(url)
        if (domain.isEmpty()) return emptyList()
        return getPasswords().filter { entry ->
            val ed = hostOf(entry.url)
            ed.isNotEmpty() && (domain == ed || domain.endsWith(".$ed") || ed.endsWith(".$domain"))
        }
    }

    fun strengthScore(pw: String): Int {
        if (pw.isEmpty()) return 0
        var s = 0
        if (pw.length >= 8) s += 20
        if (pw.length >= 12) s += 20
        if (pw.length >= 16) s += 10
        if (pw.contains(Regex("[A-Z]"))) s += 15
        if (pw.contains(Regex("[a-z]"))) s += 10
        if (pw.contains(Regex("[0-9]"))) s += 15
        if (pw.contains(Regex("[^A-Za-z0-9]"))) s += 20
        if (pw.contains(Regex("(.)\\1{2}"))) s -= 15
        return s.coerceIn(0, 100)
    }

    // Public for BackupManager
    fun encryptString(data: String, password: String): String = enc(data, password)
    fun decryptString(data: String, password: String): String = dec(data, password)

    // Extension ke liye .vaultbak banana: base64(salt16 + iv12 + AES-GCM), PBKDF2-SHA256 310000
    fun encryptExtensionBackup(plain: String, password: String): String {
        val salt = randomBytes(16)
        val iv = randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt, 310000), GCMParameterSpec(128, iv))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(salt + iv + ct, android.util.Base64.NO_WRAP)
    }

    // Chrome/Firefox extension ki .vaultbak file: base64(salt16 + iv12 + ciphertext), PBKDF2-SHA256
    fun decryptExtensionBackup(
        data: String, password: String,
        iterations: IntArray = intArrayOf(600000, 310000, 100000)
    ): String {
        val b = android.util.Base64.decode(data.trim(), android.util.Base64.DEFAULT)
        if (b.size < 44) throw IllegalArgumentException("File bahut chhoti hai")
        val salt = b.copyOfRange(0, 16)
        val iv = b.copyOfRange(16, 28)
        val ct = b.copyOfRange(28, b.size)
        var last: Exception? = null
        for (iter in iterations) {
            try {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key(password, salt, iter), GCMParameterSpec(128, iv))
                return String(c.doFinal(ct), Charsets.UTF_8)
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("Decrypt failed")
    }
}
