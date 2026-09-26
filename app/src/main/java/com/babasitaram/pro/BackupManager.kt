package com.babasitaram.pro

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

/**
 * BSR Pro Backup Manager
 *
 * Format: .bsrpro — BSR-PRO-v5 format
 *
 * Security layers:
 * 1. Signature check    — only "BSR-VAULT-v5" accepted
 * 2. App name check     — only "BabaSitaRam Pro" accepted
 * 3. Magic bytes check  — "BSRVLT" hex must be present
 * 4. Origin hash check  — HMAC with BSR secret key
 * 5. AES-256-GCM encryption — Master Password required
 *
 * Any other app / tampered file = REJECTED
 */
object BackupManager {

    private const val TAG            = "BSR_Backup"
    private const val BSR_SIG        = "BSR-VAULT-v5"
    private const val BSR_APP_NAME   = "BabaSitaRam Pro"
    private const val BSR_MAGIC      = "425352564c54"       // hex "BSRVLT"
    private const val BSR_SECRET     = "b5r9p2o1a3r7m8e4"  // Same as extension

    // Old signatures for backward compatibility
    private val VALID_SIGS = setOf(
        "BSR-VAULT-v5",
        "VaultX-Proprietary-v4",
        "VaultX-Proprietary-v3"
    )

    private val gson = Gson()

    data class BackupData(
        val passwords: List<PasswordEntry> = emptyList(),
        val otp: List<OtpEntry> = emptyList(),
        val meta: Map<String, String> = emptyMap()
    )

    data class OtpEntry(
        val id: String = "",
        val site: String = "",
        val secret: String = "",
        val digits: Int = 6,
        val period: Int = 30
    )

    // ─────────────────────────────────────────────
    // LEGACY .bsrpro builder — NOT reachable from any user-facing export (the only user export format is
    // BSRPRO.Vault V3). Kept `internal` solely so SelfTest can exercise the legacy-import path.
    // ─────────────────────────────────────────────
    internal fun buildBackupJson(masterPassword: String, passwords: List<PasswordEntry>): String {
        val data = BackupData(
            passwords = passwords,
            meta = mapOf(
                "app"       to BSR_APP_NAME,
                "version"   to "5",
                "device"    to android.os.Build.MODEL,
                "exportedAt" to java.util.Date().toString(),
                "count"     to passwords.size.toString()
            )
        )

        val originHash = computeOriginHash("5")
        val dataJson   = gson.toJson(data)
        val encrypted  = VaultManager.encryptString(dataJson, masterPassword)

        val backup = mapOf(
            "app"         to BSR_APP_NAME,
            "version"     to "5",
            "sig"         to BSR_SIG,
            "magic"       to BSR_MAGIC,
            "originHash"  to originHash,
            "encrypted"   to true,
            "vault_backup" to true,
            "bsr_only"    to true,
            "platform"    to "android",
            "savedAt"     to java.util.Date().toString(),
            "data"        to encrypted
        )

        return gson.toJson(backup)
    }

    // ─────────────────────────────────────────────
    // IMPORT — Verify and decrypt BSR backup
    // ─────────────────────────────────────────────
    suspend fun importBackup(
        ctx: Context,
        content: String,
        masterPassword: String
    ): ImportResult {
        // Extension (.vaultbak / .json) ki encrypted file: JSON nahi, seedha base64 text hoti hai
        val text = content.trim().trimStart('\uFEFF')

        // KeePass XML
        if (looksLikeXml(text)) {
            return try {
                val list = parseKeePassXml(text)
                if (list.isEmpty()) ImportResult.Error("🚫 XML mein koi entry nahi mili")
                else ImportResult.Success(BackupData(passwords = list))
            } catch (e: Exception) {
                ImportResult.Error("🚫 KeePass XML padh nahi paye: " + e.message)
            }
        }

        // CSV (Chrome / Bitwarden / LastPass / 1Password / NordPass / BSR export)
        if (looksLikeCsv(text)) {
            val rows = parseCsv(text)
            val list = csvToEntries(rows)
            return if (list.isEmpty()) ImportResult.Error("🚫 CSV mein koi valid entry nahi mili")
            else ImportResult.Success(BackupData(passwords = list))
        }
        if (BsrVault.isV3(text)) return importBsrProVault(text, masterPassword)
        if (!text.startsWith("{")) {
            return importExtensionBackup(text, masterPassword)
        }

        // Extension ki auto-backup: {"vault_backup":true,"v":2,"data":"<base64>"}
        val wrapper: JsonElement? = try { JsonParser.parseString(text) } catch (e: Exception) { null }
        if (wrapper != null && wrapper.isJsonObject && wrapper.asJsonObject.has("vault_backup") &&
            !wrapper.asJsonObject.has("sig")) {
            val inner = wrapper.asJsonObject.str("data")
            if (inner.isEmpty()) return ImportResult.Error("🚫 Backup file mein data nahi mila")
            return importExtensionBackup(inner, masterPassword, true)
        }

        return try {
            val map: Map<String, Any> = gson.fromJson(
                content, object : TypeToken<Map<String, Any>>() {}.type)

            // Bina encryption wali extension JSON (entries list)
            if (map["sig"] == null && map.containsKey("entries")) {
                return parseExtensionEntries(JsonParser.parseString(text))
            }

            // ── Security Check 1: Signature ──
            val sig = map["sig"] as? String
            if (sig == null || sig !in VALID_SIGS) {
                return ImportResult.Error(
                    "🚫 Yeh BSR Pro ki backup file nahi hai!\n\n" +
                    "Sirf BabaSitaRam Pro ki BSRPRO.Vault (ya purani .vaultbak) files import ho sakti hain.\n" +
                    "Doosre apps ki files support nahi hain."
                )
            }

            // ── Security Check 2: App Name ──
            val appName = map["app"] as? String
            if (appName != null && appName != BSR_APP_NAME) {
                return ImportResult.Error(
                    "🚫 Yeh file kisi aur app ki hai: \"$appName\"\n\n" +
                    "Sirf BabaSitaRam Pro ki backup files yahan import ho sakti hain."
                )
            }

            // ── Security Check 3: Magic Bytes (v5 only) ──
            if (sig == "BSR-VAULT-v5") {
                val magic = map["magic"] as? String
                if (magic != null && magic != BSR_MAGIC) {
                    return ImportResult.Error(
                        "🚫 File corrupt ya tampered hai!\n\nMagic bytes match nahi kar rahe."
                    )
                }
            }

            // ── Security Check 4: Origin Hash ──
            val originHash = map["originHash"] as? String
            if (originHash == null) {
                return ImportResult.Error("🚫 Origin token missing — invalid file!")
            }

            val version = (map["version"] as? String) ?: "4"
            if (!verifyOriginHash(originHash, version)) {
                return ImportResult.Error(
                    "🚫 File tampered ya corrupt hai!\n\nOrigin verification failed."
                )
            }

            // ── Decrypt ──
            val encryptedData = map["data"] as? String
                ?: return ImportResult.Error("🚫 Data field missing!")

            val decrypted = try {
                VaultManager.decryptString(encryptedData, masterPassword)
            } catch (e: Exception) {
                return ImportResult.Error(
                    "❌ Master Password galat hai ya file corrupt hai!\n\nSahi Master Password dalein."
                )
            }

            // ── Parse ──
            val backupData: BackupData = try {
                gson.fromJson(decrypted, BackupData::class.java)
            } catch (e: Exception) {
                return ImportResult.Error("🚫 Backup data parse error: ${e.message}")
            }

            ImportResult.Success(backupData)

        } catch (e: Exception) {
            Log.e(TAG, "Import error: ${e.message}")
            ImportResult.Error("Import failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────
    // Chrome/Firefox Extension backup import (.vaultbak)
    // ─────────────────────────────────────────────
    /**
     * BSRPRO.Vault V3: V3 rules only (no weaker-crypto fallback). All-or-nothing: any authentication,
     * header, count or entry-shape problem aborts the WHOLE import — nothing is partially restored.
     */
    private fun importBsrProVault(text: String, password: String): ImportResult {
        val plain = try {
            String(BsrVault.decrypt(password, text), Charsets.UTF_8)
        } catch (e: BsrVault.AuthException) {
            return ImportResult.Error("❌ Master Password galat hai ya BSRPRO.Vault file corrupt/tampered hai.\n\nWahi Master Password dalein jo export ke time use kiya tha. Kuch bhi import nahi hua.")
        } catch (e: BsrVault.FormatException) {
            Log.e(TAG, "BSRPRO.Vault format error: " + e.message)
            return ImportResult.Error("🚫 " + e.message + "\nKuch bhi import nahi hua.")
        }
        return try {
            val outer = JsonParser.parseString(plain.trim().trimStart('\uFEFF'))
            if (!outer.isJsonObject) return ImportResult.Error("🚫 BSRPRO.Vault payload invalid — kuch import nahi hua.")
            val o = outer.asJsonObject
            if (o.str("format") != "BSRPRO.Vault" || o.str("version") != BsrVault.VERSION.toString())
                return ImportResult.Error("🚫 BSRPRO.Vault payload header invalid — kuch import nahi hua.")
            if (!o.str("_bsrOrigin").startsWith("BABASITARAMPro:"))
                return ImportResult.Error("🚫 Origin verification failed — file kisi aur app ki hai.")
            // inner payload: array OR {entries, folders}
            var inner: JsonElement = o.get("data") ?: return ImportResult.Error("🚫 BSRPRO.Vault mein data nahi mila.")
            if (inner.isJsonPrimitive && inner.asJsonPrimitive.isString) inner = JsonParser.parseString(inner.asString)
            val arr = when {
                inner.isJsonArray -> inner.asJsonArray
                inner.isJsonObject && inner.asJsonObject.has("entries") && inner.asJsonObject.get("entries").isJsonArray ->
                    inner.asJsonObject.getAsJsonArray("entries")
                else -> return ImportResult.Error("🚫 BSRPRO.Vault mein entries nahi mili — kuch import nahi hua.")
            }
            val expected = if (o.has("count") && !o.get("count").isJsonNull) o.get("count").asInt else -1
            if (expected >= 0 && arr.size() != expected)
                return ImportResult.Error("🚫 Entry count match nahi hua (" + arr.size() + " != " + expected + ") — backup adhoora/corrupt hai. Kuch import nahi hua.")
            for (el in arr) if (!el.isJsonObject) return ImportResult.Error("🚫 Backup mein malformed entry mili — poora import roka gaya.")
            val res = parseExtensionEntries(inner)
            if (res is ImportResult.Success && expected > 0 && res.data.passwords.isEmpty())
                return ImportResult.Error("🚫 Entries validate nahi hui — poora import roka gaya.")
            res
        } catch (e: Exception) {
            Log.e(TAG, "BSRPRO.Vault parse failed", e)
            ImportResult.Error("🚫 BSRPRO.Vault padhne mein error: " + e.message + "\nKuch import nahi hua.")
        }
    }

    private fun importExtensionBackup(b64: String, password: String, wrapper: Boolean = false): ImportResult {
        // Legacy plain base64(JSON)
        try {
            val raw = android.util.Base64.decode(b64.trim(), android.util.Base64.DEFAULT)
            val asText = String(raw, Charsets.UTF_8).trim().trimStart('\uFEFF')
            if (asText.startsWith("{") || asText.startsWith("[")) {
                val j = JsonParser.parseString(asText)
                if (j.isJsonObject) {
                    val jo = j.asJsonObject
                    if (jo.has("vault_backup") && jo.str("data").isNotEmpty()) {
                        return importExtensionBackup(jo.str("data"), password, true)
                    }
                    if (jo.has("_bsrOrigin") || jo.has("entries") || jo.has("data")) return parseExtensionJsonPayload(j)
                } else if (j.isJsonArray) return parseExtensionJsonPayload(j)
            }
        } catch (_: Exception) {}

        val plain = try {
            VaultManager.decryptExtensionBackup(b64.trim(), password, if (wrapper) intArrayOf(600000, 310000, 100000) else intArrayOf(310000, 600000, 100000))
        } catch (e: Exception) {
            Log.e(TAG, "vaultbak decrypt failed", e)
            return ImportResult.Error("❌ Master Password galat hai ya yeh BSR Pro Extension ki backup file nahi hai.\n\nWahi Master Password dalein jo export ke time use kiya tha.")
        }

        val plainTrim = plain.trim().trimStart('\uFEFF')
        if (plainTrim.startsWith("{") || plainTrim.startsWith("[")) {
            return try { parseExtensionJsonPayload(JsonParser.parseString(plainTrim)) }
            catch (e: Exception) { Log.e(TAG, "vaultbak JSON parse failed", e); ImportResult.Error("🚫 .vaultbak JSON padhne mein error: ${e.message}") }
        }

        if (looksLikeCsv(plainTrim)) {
            val list = csvToEntries(parseCsv(plainTrim))
            return if (list.isEmpty()) ImportResult.Error("🚫 CSV mein koi valid entry nahi mili") else ImportResult.Success(BackupData(passwords = list))
        }
        return ImportResult.Error("🚫 .vaultbak ka decrypted format samajh nahi aaya.")
    }

    private fun parseExtensionJsonPayload(input: JsonElement): ImportResult {
        var root = input
        repeat(8) {
            if (root.isJsonPrimitive && root.asJsonPrimitive.isString) {
                val s = root.asString.trim().trimStart('\uFEFF')
                if (s.startsWith("{") || s.startsWith("[")) { root = JsonParser.parseString(s); return@repeat }
                return ImportResult.Error("🚫 Extension backup ka JSON data format samajh nahi aaya.")
            }
            if (root.isJsonArray) return parseExtensionEntries(root)
            if (!root.isJsonObject) return ImportResult.Error("🚫 Backup JSON format invalid hai.")
            val obj = root.asJsonObject
            if (obj.has("_bsrOrigin")) {
                val origin = obj.str("_bsrOrigin")
                if (!origin.startsWith("BABASITARAMPro:")) return ImportResult.Error("🚫 Origin verification failed — file kisi aur app ki hai.")
            }
            if (obj.has("data") && !obj.get("data").isJsonNull) { root = obj.get("data"); return@repeat }
            if (obj.has("entries")) return parseExtensionEntries(obj)
            return ImportResult.Error("🚫 Backup mein koi passwords nahi mile")
        }
        return ImportResult.Error("🚫 Backup JSON nesting bahut zyada hai.")
    }

    private fun JsonObject.str(key: String): String {
        return try { if (has(key) && !get(key).isJsonNull) get(key).asString else "" } catch (_: Exception) { "" }
    }

    // ─────────────────────────────────────────────
    // CSV import / export
    // ─────────────────────────────────────────────
    fun looksLikeCsv(text: String): Boolean {
        if (text.startsWith("{")) return false
        val first = text.lineSequence().firstOrNull()?.lowercase() ?: return false
        return first.contains(",") && first.contains("password")
    }

    /** RFC-4180 CSV parser (quotes, commas aur newlines ke saath). */
    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') { cell.append('"'); i++ } else inQuotes = false
                } else cell.append(c)
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> { row.add(cell.toString()); cell.setLength(0) }
                    '\r' -> { }
                    '\n' -> {
                        row.add(cell.toString()); cell.setLength(0)
                        rows.add(row); row = ArrayList()
                    }
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }

    private fun csvToEntries(rows: List<List<String>>): List<PasswordEntry> {
        if (rows.size < 2) return emptyList()
        val header = rows[0].map { it.trim().lowercase().trimStart('\uFEFF') }
        fun idx(vararg names: String): Int {
            for (nm in names) { val k = header.indexOf(nm); if (k >= 0) return k }
            return -1
        }
        val iName = idx("name", "title", "site", "account", "login_name")
        val iUrl = idx("url", "login_uri", "website", "uri", "web site")
        val iUser = idx("username", "login_username", "user", "login", "email", "login_email")
        val iPass = idx("password", "login_password", "pass")
        val iNote = idx("note", "notes", "extra", "comments")
        val iTotp = idx("totp", "login_totp", "otpauth", "otp")
        val iCat = idx("category")
        val iFolder = idx("folder", "grouping", "group")
        val iFav = idx("favorite", "fav", "starred")
        val iType = idx("type")
        val iMobile = idx("mobile")
        // NordPass / cards / identity
        val iCardNo = idx("cardnumber", "card_number", "cardno")
        val iCardHolder = idx("cardholdername", "cardholder", "card_holder")
        val iCvv = idx("cvc", "cvv", "card_cvv")
        val iExp = idx("expirydate", "expiry", "card_expiry")
        val iFullName = idx("full_name", "fullname")
        val iEmail = idx("email")
        val iPhone = idx("phone_number", "phone")
        val iAddr = idx("address1", "address")
        val iIdNo = idx("id_number", "idnumber")
        val iIdExp = idx("id_expiry", "idexpiry")
        val now = System.currentTimeMillis()
        val out = ArrayList<PasswordEntry>()
        for (r in rows.drop(1)) {
            fun g(k: Int): String = if (k >= 0 && k < r.size) r[k].trim() else ""
            val url = g(iUrl)
            var site = g(iName)
            if (site.isEmpty()) site = url
            val typeRaw = g(iType).lowercase().replace(" ", "").replace("_", "")
            val recType = when {
                typeRaw == "note" || typeRaw == "securenote" -> "note"
                typeRaw == "card" || typeRaw == "creditcard" || iCardNo >= 0 && g(iCardNo).isNotEmpty() -> "card"
                typeRaw == "identity" || (iFullName >= 0 && g(iFullName).isNotEmpty() && g(iPass).isEmpty()) -> "identity"
                else -> "login"
            }
            val user = if (recType == "identity") "" else g(iUser)
            val pass = g(iPass)
            val notes = g(iNote)
            if (site.isEmpty() && user.isEmpty() && pass.isEmpty() && notes.isEmpty() &&
                g(iCardNo).isEmpty() && g(iFullName).isEmpty()) continue
            val fav = g(iFav).lowercase().let { it == "1" || it == "true" || it == "yes" }
            val folder = g(iFolder)
            val cat = if (iCat >= 0) normalizeCategory(g(iCat)) else "Other"
            out.add(
                PasswordEntry(
                    site = site,
                    url = url,
                    username = user,
                    password = pass,
                    notes = notes,
                    category = cat,
                    totp = g(iTotp),
                    type = recType,
                    mobile = g(iMobile),
                    folder = folder,
                    cardNumber = g(iCardNo),
                    cardholder = g(iCardHolder),
                    cardCvv = g(iCvv),
                    cardExpiry = g(iExp),
                    fullName = g(iFullName),
                    email = if (recType == "identity") g(iEmail) else "",
                    phone = g(iPhone),
                    address = g(iAddr),
                    idNumber = g(iIdNo),
                    idExpiry = g(iIdExp),
                    isFavorite = fav,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        return out
    }

    private fun csvCell(v: String): String {
        val needs = v.contains(',') || v.contains('"') || v.contains('\n') || v.contains('\r')
        return if (needs) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    /**
     * Plain-text CSV. Pehle 6 columns extension ke "Vault CSV" jaise (name,url,username,mobile,password,notes),
     * baaki extra. Chrome/Bitwarden jaise header-based importers name/url/username/password padh lete hain.
     * WARNING: unencrypted.
     */
    fun exportCsv(list: List<PasswordEntry>): String {
        ExportGate.require()   // plaintext export: fresh Master Password verification required
        val sb = StringBuilder()
        sb.append("name,url,username,mobile,password,notes,strength,starred,createdAt,updatedAt,totp,category,type,folder,cardnumber,cardholdername,cvc,expirydate,full_name,email,phone_number,address1,id_number,id_expiry\n")
        for (e in list) {
            val cells = listOf(
                e.site, e.url, e.username, e.mobile, e.password, e.notes,
                VaultManager.strengthScore(e.password).toString(),
                if (e.isFavorite) "1" else "0",
                e.createdAt.toString(), e.updatedAt.toString(),
                e.totp, e.category, e.type, e.folder,
                e.cardNumber, e.cardholder, e.cardCvv, e.cardExpiry,
                e.fullName, e.email, e.phone, e.address, e.idNumber, e.idExpiry
            )
            sb.append(cells.joinToString(",") { csvCell(it) }).append('\n')
        }
        return sb.toString()
    }

    /**
     * Universal CSV (Chrome / Google Password Manager / Edge / Firefox / Bitwarden / extension ka "Chrome" format).
     * Sirf login entries: name,url,username,password,note. WARNING: unencrypted.
     */
    fun exportChromeCsv(list: List<PasswordEntry>): String {
        ExportGate.require()   // plaintext export: fresh Master Password verification required
        val sb = StringBuilder()
        sb.append("name,url,username,password,note\n")
        for (e in list) {
            if (e.type != "login" || e.password.isEmpty()) continue
            val user = if (e.username.isEmpty()) e.mobile else e.username
            sb.append(listOf(e.site, e.url, user, e.password, e.notes).joinToString(",") { csvCell(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun normalizeCategory(raw: String): String {
        val l = raw.trim().lowercase()
        return when {
            l.contains("bank") || l.contains("financ") || l.contains("card") -> "Banking"
            l.contains("social") -> "Social"
            l.contains("mail") -> "Email"
            l.contains("work") || l.contains("office") -> "Work"
            l.contains("personal") -> "Personal"
            l.contains("shop") || l.contains("commerce") -> "Shopping"
            l.contains("game") -> "Games"
            else -> "Other"
        }
    }

    /** Android category -> extension category id (extension mein sirf work/personal/banking/social/other hain). */
    private fun extCategory(cat: String): String = when (cat) {
        "Banking" -> "banking"
        "Social" -> "social"
        "Work" -> "work"
        "Personal" -> "personal"
        else -> "other"
    }

    private fun parseExtensionEntries(root: JsonElement): ImportResult {
        val arr = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject && root.asJsonObject.has("entries") ->
                root.asJsonObject.getAsJsonArray("entries")
            else -> return ImportResult.Error("🚫 Backup mein koi passwords nahi mile")
        }

        // Folders: payload.folders + raw-vault ke _metaType=folder records
        val folderNames = HashMap<String, String>()
        if (root.isJsonObject && root.asJsonObject.has("folders") &&
            root.asJsonObject.get("folders").isJsonArray) {
            for (f in root.asJsonObject.getAsJsonArray("folders")) {
                if (!f.isJsonObject) continue
                val fo = f.asJsonObject
                if (fo.str("id").isNotEmpty()) folderNames[fo.str("id")] = fo.str("name")
            }
        }
        for (el in arr) {
            if (el.isJsonObject && el.asJsonObject.str("_metaType") == "folder") {
                val fo = el.asJsonObject
                if (fo.str("id").isNotEmpty()) folderNames[fo.str("id")] = fo.str("name")
            }
        }

        val now = System.currentTimeMillis()
        val list = mutableListOf<PasswordEntry>()
        for (el in arr) {
            if (!el.isJsonObject) continue
            val o = el.asJsonObject
            val meta = o.str("_metaType")
            if (meta == "folder" || meta == "activityLog") continue

            val recordType = o.str("recordType").ifEmpty {
                if (o.str("isNote") == "true") "note" else "login"
            }
            val type = when (recordType) {
                "note", "card", "identity" -> recordType
                else -> "login"
            }

            val title = o.str("title")
            val url = o.str("url")
            val mobile = o.str("mobile")
            val user = o.str("username")
            val pass = o.str("password")
            val fullName = o.str("fullName")
            val cardNumber = o.str("cardNumber")

            if (title.isEmpty() && url.isEmpty() && user.isEmpty() && pass.isEmpty() &&
                fullName.isEmpty() && cardNumber.isEmpty() && o.str("notes").isEmpty()) continue

            val notes = o.str("notes")

            val hist = ArrayList<PwOld>()
            if (o.has("passwordHistory") && o.get("passwordHistory").isJsonArray) {
                for (h in o.getAsJsonArray("passwordHistory")) {
                    if (!h.isJsonObject) continue
                    val ho = h.asJsonObject
                    val hp = ho.str("pw")
                    if (hp.isNotEmpty()) hist.add(PwOld(hp, ho.str("changedAt").toLongOrNull() ?: 0L))
                }
            }
            val tags = ArrayList<String>()
            if (o.has("tags") && o.get("tags").isJsonArray) {
                for (t in o.getAsJsonArray("tags")) {
                    if (t.isJsonPrimitive && t.asString.isNotEmpty()) tags.add(t.asString)
                }
            }
            val fields = ArrayList<CustomField>()
            if (o.has("customFields") && o.get("customFields").isJsonArray) {
                for (f in o.getAsJsonArray("customFields")) {
                    if (!f.isJsonObject) continue
                    val fo = f.asJsonObject
                    if (fo.str("k").isNotEmpty()) fields.add(CustomField(fo.str("k"), fo.str("v")))
                }
            }

            // Android-only categories (Email/Shopping/Games) extension mein tag ban kar jaati hain — wapas category banao
            var category = normalizeCategory(o.str("category"))
            if (category == "Other") {
                val t = tags.firstOrNull { it.lowercase() == "email" || it.lowercase() == "shopping" || it.lowercase() == "games" }
                if (t != null) {
                    category = normalizeCategory(t)
                    tags.remove(t)
                }
            }

            val site = if (title.isNotEmpty()) title else if (url.isNotEmpty()) url else fullName
            list.add(
                PasswordEntry(
                    id = o.str("id").ifEmpty { java.util.UUID.randomUUID().toString() },
                    site = site,
                    url = url,
                    username = user,
                    password = pass,
                    notes = notes,
                    category = category,
                    totp = o.str("totp"),
                    type = type,
                    history = hist,
                    mobile = mobile,
                    folder = folderNames[o.str("folderId")] ?: "",
                    folderId = if (folderNames.containsKey(o.str("folderId"))) o.str("folderId") else "",
                    tags = tags,
                    fields = fields,
                    cardNumber = cardNumber,
                    cardholder = o.str("cardholder"),
                    cardExpiry = o.str("cardExpiry"),
                    cardCvv = o.str("cardCvv"),
                    fullName = fullName,
                    email = o.str("email"),
                    phone = o.str("phone"),
                    address = o.str("address"),
                    idNumber = o.str("idNumber"),
                    idExpiry = o.str("idExpiry"),
                    isFavorite = o.str("starred") == "true",
                    createdAt = o.str("createdAt").toLongOrNull() ?: now,
                    updatedAt = o.str("updatedAt").toLongOrNull() ?: now
                )
            )
        }
        return ImportResult.Success(BackupData(passwords = list))
    }

    // ─────────────────────────────────────────────
    // BSRPRO.Vault (V3) export — Chrome/Firefox extension aur APK dono mein import hota hai
    // ─────────────────────────────────────────────
    /**
     * includeFolders=false: entries mein folder nahi jaata aur "folders" list bhi nahi bheji jaati.
     * Zaroori: extension import mein file ki folders list us ke apne folders ko REPLACE kar deti hai.
     */
    fun buildBsrProVault(master: String, list: List<PasswordEntry>, includeFolders: Boolean = true): String {
        ExportGate.require()   // manual export: Master Password verification enforced here, not only in the UI
        return buildBsrProVaultUnchecked(master, list, includeFolders)
    }

    /** Auto-backup: only while the vault is unlocked; always the same encrypted BSRPRO.Vault V3 format. */
    fun buildAutoBackup(master: String, list: List<PasswordEntry>): String {
        check(VaultManager.isUnlocked) { "Vault locked — auto-backup nahi banega" }
        return buildBsrProVaultUnchecked(master, list, true)
    }

    /** Callers must hold an [ExportGate] grant (manual export), be unlocked auto-backup, or the self-test. */
    internal fun buildBsrProVaultUnchecked(master: String, list: List<PasswordEntry>, includeFolders: Boolean = true): String {
        val folderIds = LinkedHashMap<String, String>()
        // Extension se aaye folders ki purani id wahi rakho — warna extension mein entries ka folder toot jata hai
        for (e in list) {
            if (includeFolders && e.folder.isNotEmpty() && e.folderId.isNotEmpty() && !folderIds.containsKey(e.folder)) {
                folderIds[e.folder] = e.folderId
            }
        }
        fun folderId(name: String): String {
            if (!includeFolders || name.isEmpty()) return ""
            return folderIds.getOrPut(name) { java.util.UUID.randomUUID().toString() }
        }
        val entries = ArrayList<Map<String, Any?>>()
        for (e in list) {
            val m = LinkedHashMap<String, Any?>()
            m["id"] = e.id
            m["title"] = e.site
            m["url"] = e.url
            m["username"] = e.username
            m["mobile"] = e.mobile
            m["password"] = e.password
            m["notes"] = e.notes
            val extCat = extCategory(e.category)
            val outTags = ArrayList<String>(e.tags)
            if (e.category != "Other" && extCat == "other" && !outTags.contains(e.category.lowercase())) {
                outTags.add(e.category.lowercase())   // extension mein yeh category nahi — tag bana kar bacha lo
            }
            m["tags"] = outTags
            m["customFields"] = e.fields.map { mapOf("k" to it.k, "v" to it.v) }
            m["totp"] = e.totp
            m["category"] = extCat
            m["isNote"] = e.type == "note"
            m["recordType"] = e.type
            m["folderId"] = folderId(e.folder)
            m["cardNumber"] = e.cardNumber
            m["cardholder"] = e.cardholder
            m["cardExpiry"] = e.cardExpiry
            m["cardCvv"] = e.cardCvv
            m["fullName"] = e.fullName
            m["email"] = e.email
            m["phone"] = e.phone
            m["address"] = e.address
            m["idNumber"] = e.idNumber
            m["idExpiry"] = e.idExpiry
            m["starred"] = e.isFavorite
            m["passwordHistory"] = e.history.map { mapOf("pw" to it.pw, "changedAt" to it.at) }
            m["createdAt"] = e.createdAt
            m["updatedAt"] = e.updatedAt
            entries.add(m)
        }
        val now = System.currentTimeMillis()
        val folders = folderIds.entries.map {
            mapOf("id" to it.value, "name" to it.key, "createdAt" to now, "updatedAt" to now)
        }
        val payload = LinkedHashMap<String, Any?>()
        payload["version"] = "2.0"
        payload["app"] = "BABASITARAMPro"
        payload["exportDate"] = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(now))
        payload["count"] = entries.size
        payload["entries"] = entries
        // Khali "folders": [] bhejne se extension ke saare folders mit jaate — isliye tabhi bhejo jab folders ho
        if (includeFolders && folders.isNotEmpty()) payload["folders"] = folders
        val outer = LinkedHashMap<String, Any?>()
        outer["_bsrOrigin"] = "BABASITARAMPro:android"
        outer["format"] = "BSRPRO.Vault"
        outer["version"] = BsrVault.VERSION
        outer["ts"] = now
        outer["count"] = entries.size
        // Extension ka niyam: object payload mein "folders" na ho to woh [] maan leta hai aur apne saare folders mita deta hai.
        // Sirf entries ki bare array bhejne par woh folders ko chhuta hi nahi — isliye folders na hon to array bhejte hain.
        outer["data"] = if (includeFolders && folders.isNotEmpty()) gson.toJson(payload) else gson.toJson(entries)
        return BsrVault.encrypt(master, gson.toJson(outer).toByteArray(Charsets.UTF_8))
    }

    // ─────────────────────────────────────────────
    // KeePass XML import (KeePass 2 standard + extension ka simple XML)
    // ─────────────────────────────────────────────
    fun looksLikeXml(text: String): Boolean =
        text.startsWith("<?xml") || text.startsWith("<KeePassFile")

    private fun elText(el: org.w3c.dom.Element, tag: String): String {
        val kids = el.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n is org.w3c.dom.Element && n.tagName == tag) return n.textContent ?: ""
        }
        return ""
    }

    fun parseKeePassXml(text: String): List<PasswordEntry> {
        val dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        try { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (e: Exception) { }
        val doc = dbf.newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(text)))
        val out = ArrayList<PasswordEntry>()
        walkKpGroup(doc.documentElement, "", out, System.currentTimeMillis())
        return out
    }

    private fun walkKpGroup(el: org.w3c.dom.Element, folder: String, out: MutableList<PasswordEntry>, now: Long) {
        val kids = el.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n !is org.w3c.dom.Element) continue
            when (n.tagName) {
                "Root" -> {
                    // Root ka pehla Group database ka root hota hai — uska naam folder nahi banta
                    val rk = n.childNodes
                    for (j in 0 until rk.length) {
                        val g = rk.item(j)
                        if (g is org.w3c.dom.Element && g.tagName == "Group") walkKpGroup(g, "", out, now)
                    }
                }
                "Group" -> {
                    val nm = elText(n, "Name")
                    val f = if (nm.isEmpty() || nm == "BabaSitaRam Pro" || nm == "Root") folder
                        else if (folder.isEmpty()) nm else "$folder / $nm"
                    walkKpGroup(n, f, out, now)
                }
                "Entry" -> {
                    val m = HashMap<String, String>()
                    val ek = n.childNodes
                    for (j in 0 until ek.length) {
                        val c = ek.item(j)
                        if (c !is org.w3c.dom.Element) continue
                        if (c.tagName == "String") {
                            val k = elText(c, "Key")
                            if (k.isNotEmpty()) m[k] = elText(c, "Value")
                        } else if (c.tagName == "Title" || c.tagName == "UserName" || c.tagName == "Password" ||
                            c.tagName == "URL" || c.tagName == "Notes") {
                            m[c.tagName] = c.textContent ?: ""
                        }
                    }
                    val title = m["Title"] ?: ""
                    val url = m["URL"] ?: ""
                    val user = m["UserName"] ?: ""
                    val pass = m["Password"] ?: ""
                    if (title.isEmpty() && url.isEmpty() && user.isEmpty() && pass.isEmpty()) continue
                    out.add(
                        PasswordEntry(
                            site = if (title.isNotEmpty()) title else url,
                            url = url,
                            username = user,
                            password = pass,
                            notes = m["Notes"] ?: "",
                            totp = m["otp"] ?: m["TimeOtp-Secret-Base32"] ?: "",
                            folder = folder,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Origin hash — same algorithm as extension
    // ─────────────────────────────────────────────
    private fun computeOriginHash(version: String): String {
        val raw = "{\"app\":\"$BSR_APP_NAME\",\"version\":\"$version\"}$BSR_SECRET"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun verifyOriginHash(hash: String, version: String): Boolean {
        // Try current + old secrets for backward compatibility
        val secrets = listOf(
            BSR_SECRET,
            "7a3f9b2e1c8d4f6b",  // extension v4
            "7a3f9b2e1c8d4f6a"   // extension v3
        )
        val versions = listOf(version, "4", "5")

        for (secret in secrets) {
            for (ver in versions) {
                val raw = "{\"app\":\"$BSR_APP_NAME\",\"version\":\"$ver\"}$secret"
                val digest = MessageDigest.getInstance("SHA-256")
                val expected = digest.digest(raw.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }.take(32)
                if (hash == expected) return true
            }
        }
        return false
    }

    sealed class ImportResult {
        data class Success(val data: BackupData) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }
}
