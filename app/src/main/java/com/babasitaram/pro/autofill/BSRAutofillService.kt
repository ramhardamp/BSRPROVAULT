package com.babasitaram.pro.autofill

import com.babasitaram.pro.SystemLog

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Parcelable
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.babasitaram.pro.AppPrefs
import com.babasitaram.pro.AutofillLog
import com.babasitaram.pro.LoginActivity
import com.babasitaram.pro.PasswordEntry
import com.babasitaram.pro.PickerActivity
import com.babasitaram.pro.R
import com.babasitaram.pro.VaultManager

class BSRAutofillService : AutofillService() {

    companion object {
        const val EXTRA_FROM_AUTOFILL = "from_autofill"
        const val EXTRA_USERNAME_IDS = "bsr_username_ids"
        const val EXTRA_PASSWORD_IDS = "bsr_password_ids"
        const val EXTRA_WEB_DOMAIN = "bsr_web_domain"
        const val EXTRA_APP_ID = "bsr_app_id"
        const val EXTRA_INLINE_REQ = "bsr_inline_req"

        // Browsers: inke liye sirf webDomain se match hota hai (package name se nahi)
        private val BROWSERS = setOf(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
            "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
            "com.brave.browser", "com.microsoft.emmx", "com.sec.android.app.sbrowser",
            "com.opera.browser", "com.opera.mini.native", "com.duckduckgo.mobile.android",
            "com.vivaldi.browser", "com.kiwibrowser.browser", "org.chromium.chrome",
            // Xiaomi/Redmi/POCO (MIUI/HyperOS) aur anya common browsers — inhe native app maan kar token-fallback na chale
            "com.mi.globalbrowser", "com.mi.globalbrowser.mini", "com.android.browser", "com.miui.browser",
            "com.sec.android.app.sbrowser.beta", "com.google.android.apps.chrome", "com.yandex.browser",
            "com.ecosia.android", "com.brave.browser_beta", "org.torproject.torbrowser"
        )

        // Package name ke aam shabd jo site ka naam nahi hote
        private val IGNORE_TOKENS = setOf(
            "android", "mobile", "app", "apps", "official", "prod", "release",
            "main", "client", "user", "login", "beta", "lite", "free", "pro", "www"
        )

        private fun domainOf(raw: String): String {
            val s = raw.trim()
            if (s.isEmpty()) return ""
            return try {
                java.net.URI(if (s.contains("://")) s else "https://$s")
                    .host?.lowercase()?.removePrefix("www.") ?: ""
            } catch (e: Exception) { "" }
        }

        /** Site/app ke liye saved logins dhundta hai. */
        fun findMatches(webDomain: String, appId: String): List<PasswordEntry> {
            val all = VaultManager.getPasswords().filter { it.type != "note" && it.password.isNotEmpty() }

            // MATCHING PRECEDENCE — DO NOT REORDER: browser/domain matching must stay isolated
            // from explicit app-package links. Browser requests use the web domain first; an
            // appPackage link is considered only for non-browser apps when no web domain exists.

            val domain = domainOf(webDomain)

            // 1) Browser request: domain/eTLD+1 matching ONLY.
            //    Browser package matching is deliberately forbidden so Chrome + github.com
            //    cannot select an unrelated app-linked entry.
            if (domain.isNotEmpty() && appId in BROWSERS) {
                val dd = Psl.registrableDomain(domain)
                val hits = all.filter { e ->
                    val eh = domainOf(e.url)
                    eh.isNotEmpty() && (eh == domain || (dd.isNotEmpty() && Psl.registrableDomain(eh) == dd))
                }
                return hits.sortedBy { if (domainOf(it.url) == domain) 0 else 1 }
            }

            // 2) Native/non-browser app: explicit package link has priority EVEN IF the
            //    app exposes a WebView/webDomain (for example JioPOS -> jphy.jio.com).
            //    This preserves the App Picker contract and fixes native WebView regressions.
            if (appId.isNotEmpty() && appId !in BROWSERS) {
                val appMatch = all.filter { it.appPackage.equals(appId, ignoreCase = true) }
                if (appMatch.isNotEmpty()) {
                    // Primary is presentation priority, NOT an exclusive filter.
                    // Always keep every matching account available so switching accounts
                    // never requires editing/deleting the default credential.
                    return appMatch.sortedWith(
                        compareByDescending<PasswordEntry> { it.isPrimaryAppLogin }
                            .thenBy { it.site.lowercase() }
                            .thenBy { it.username.lowercase() }
                    )
                }
            }

            // 3) Non-browser WebView with no explicit app link: domain matching may be used.
            //    It is deliberately after exact package matching.
            if (domain.isNotEmpty()) {
                val dd = Psl.registrableDomain(domain)
                val hits = all.filter { e ->
                    val eh = domainOf(e.url)
                    eh.isNotEmpty() && (eh == domain || (dd.isNotEmpty() && Psl.registrableDomain(eh) == dd))
                }
                if (hits.isNotEmpty()) return hits.sortedBy { if (domainOf(it.url) == domain) 0 else 1 }
            }

            // 4) No usable domain/package match: browser packages must never use token fallback.
            if (appId.isEmpty() || appId in BROWSERS) return emptyList()

            // 5) Legacy normal-app fallback — package name ke shabdon se match.
            val tokens = appId.lowercase().split('.')
                .filter { it.length >= 4 && it !in IGNORE_TOKENS }
            if (tokens.isEmpty()) return emptyList()
            return all.filter { e ->
                val hay = (e.site + " " + e.url).lowercase().replace(" ", "")
                tokens.any { hay.contains(it) }
            }
        }

        private fun hasExactAppMatch(appId: String): Boolean {
            if (appId.isBlank() || appId in BROWSERS) return false
            return VaultManager.getPasswords().any {
                it.type != "note" && it.password.isNotEmpty() &&
                    it.appPackage.equals(appId, ignoreCase = true)
            }
        }

        private fun mutableFlags(): Int {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
            return flags
        }

        private fun nextCode(): Int = (System.nanoTime() and 0x0FFFFFFFL).toInt()

        private fun pickerSender(
            ctx: Context, u: List<AutofillId>, p: List<AutofillId>
        ): IntentSender {
            val i = Intent(ctx, PickerActivity::class.java)
            i.putParcelableArrayListExtra(EXTRA_USERNAME_IDS, ArrayList(u))
            i.putParcelableArrayListExtra(EXTRA_PASSWORD_IDS, ArrayList(p))
            return PendingIntent.getActivity(ctx, nextCode(), i, mutableFlags()).intentSender
        }

        private fun setVal(
            ds: Dataset.Builder, id: AutofillId, value: AutofillValue?,
            pres: RemoteViews, inline: Any?, useInline: Boolean
        ) {
            if (useInline) InlineHelper.setValue(ds, id, value, pres, inline)
            else ds.setValue(id, value, pres)
        }

        /**
         * FillResponse banata hai (vault unlocked hone par).
         * inlineReq: keyboard-ke-upar suggestions ke liye (Android 11+), warna null.
         */
        fun buildResponse(
            ctx: Context,
            usernameIds: List<AutofillId>,
            passwordIds: List<AutofillId>,
            webDomain: String,
            appId: String,
            inlineReq: Any? = null
        ): FillResponse? {
            if (usernameIds.isEmpty() && passwordIds.isEmpty()) return null
            Psl.ensureLoaded(ctx)
            val matches = findMatches(webDomain, appId)

            val useInline = Build.VERSION.SDK_INT >= 30 && inlineReq != null
            // FIX 4: chips ki sankhya keyboard ke max AUR specs dono se zyada nahi
            val limit = if (useInline) {
                val mc = InlineHelper.maxCount(inlineReq)
                val sc = InlineHelper.specCount(inlineReq)
                minOf(mc, sc).coerceAtLeast(1)
            } else 6

            val responseBuilder = FillResponse.Builder()
            var count = 0
            for (entry in matches) {
                if (count >= limit) break
                val presentation = RemoteViews(ctx.packageName, R.layout.autofill_item)
                presentation.setTextViewText(
                    R.id.tvAutofillSite,
                    entry.appName?.takeIf { it.isNotBlank() } ?: entry.site
                )
                presentation.setTextViewText(R.id.tvAutofillUser, entry.username.ifEmpty { entry.mobile })
                val ip = if (useInline) InlineHelper.make(ctx, inlineReq, count, entry.site, entry.username.ifEmpty { entry.mobile }) else null

                val ds = Dataset.Builder()
                for (id in usernameIds) {
                    setVal(ds, id, AutofillValue.forText(entry.username.ifEmpty { entry.mobile }), presentation, ip, useInline)
                }
                for (id in passwordIds) {
                    setVal(ds, id, AutofillValue.forText(entry.password), presentation, ip, useInline)
                }
                responseBuilder.addDataset(ds.build())
                count++
            }

            // Exact native-app match exists: don't put the generic "Vault se chunein"
            // chip in front of the normal linked-app login. Keep it only when there is no
            // exact app-linked credential, or when the response has room for manual selection.
            val exactAppMatch = hasExactAppMatch(appId)
            val primaryMatch = if (exactAppMatch) {
                matches.firstOrNull { it.isPrimaryAppLogin }
            } else null
            if (count < limit && (!exactAppMatch || primaryMatch == null)) {
                val presentation = RemoteViews(ctx.packageName, R.layout.autofill_item)
                presentation.setTextViewText(R.id.tvAutofillSite, "BSR Pro")
                presentation.setTextViewText(R.id.tvAutofillUser, "Vault se chunein…")
                val ip = if (useInline) InlineHelper.make(ctx, inlineReq, count, "BSR Pro", "Vault se chunein") else null
                val ds = Dataset.Builder()
                for (id in usernameIds + passwordIds) {
                    setVal(ds, id, null, presentation, ip, useInline)
                }
                ds.setAuthentication(pickerSender(ctx, usernameIds, passwordIds))
                responseBuilder.addDataset(ds.build())
                count++
            }

            // Naya login submit hone par "Save karein?" prompt
            if (passwordIds.isNotEmpty()) {
                // Login forms are credential pairs. Tell Android that both username
                // and password participate in the save trigger; password-only forms
                // still use the password field as the required save id.
                val saveIds = if (usernameIds.isNotEmpty()) {
                    (usernameIds + passwordIds).distinct().toTypedArray()
                } else {
                    passwordIds.toTypedArray()
                }
                val saveTypes = if (usernameIds.isNotEmpty()) {
                    SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
                } else {
                    SaveInfo.SAVE_DATA_TYPE_PASSWORD
                }
                val saveBuilder = SaveInfo.Builder(saveTypes, saveIds)
                    .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                responseBuilder.setSaveInfo(saveBuilder.build())
            }
            return responseBuilder.build()
        }
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        try {
            Psl.ensureLoaded(this)
            val structure = request.fillContexts.lastOrNull()?.structure
            if (structure == null) { callback.onSuccess(null); return }

            // Reject BSR Vault's own package before any field parsing or dataset
            // generation. This is the first package gate, so the vault can never
            // receive its own Autofill datasets.
            val pkg = structure.activityComponent?.packageName
            if (pkg.equals(applicationContext.packageName, ignoreCase = true)) {
                AutofillLog.add(this, "fill: own BSR package excluded")
                SystemLog.add(this, "Autofill request excluded for own BSR package")
                callback.onSuccess(null)
                return
            }

            val parser = StructureParser(structure)
            parser.parse()

            val usernameIds: List<AutofillId> = parser.usernameIds
            val passwordIds: List<AutofillId> = parser.passwordIds
            val safePkg = pkg ?: "?"
            if (usernameIds.isEmpty() && passwordIds.isEmpty()) {
                AutofillLog.add(this, "fill: koi login field nahi mila (app=" + pkg + ")")
                callback.onSuccess(null); return
            }

            val appId = safePkg
            val webDomain = parser.webDomain ?: ""
            val inlineReq: Any? =
                if (Build.VERSION.SDK_INT >= 30) request.inlineSuggestionsRequest else null

            if (parser.notes.isNotEmpty()) {
                AutofillLog.add(this, "fields: " + parser.notes.joinToString(" ; "))
            }
            AutofillLog.add(
                this,
                "fill: app=" + appId + " domain=" + (if (webDomain.isEmpty()) "(nahi mila)" else webDomain) +
                    " userFields=" + usernameIds.size + " passFields=" + passwordIds.size +
                    " locked=" + (!VaultManager.isUnlocked) + " | " +
                    (if (Build.VERSION.SDK_INT >= 30) InlineHelper.describe(inlineReq) else "inline: Android 11 se purana")
            )

            // Auto-lock time nikal gaya ho to vault lock kar do
            if (VaultManager.isUnlocked && AppPrefs.isSessionExpired(this)) {
                VaultManager.lock()
            }

            if (!VaultManager.isUnlocked) {
                // Vault locked: unlock screen dikhao, unlock ke baad LoginActivity result wapas karega
                val authIntent = Intent(this, LoginActivity::class.java).apply {
                    putExtra(EXTRA_FROM_AUTOFILL, true)
                    putParcelableArrayListExtra(EXTRA_USERNAME_IDS, ArrayList(usernameIds))
                    putParcelableArrayListExtra(EXTRA_PASSWORD_IDS, ArrayList(passwordIds))
                    putExtra(EXTRA_WEB_DOMAIN, webDomain)
                    putExtra(EXTRA_APP_ID, appId)
                    val pr = inlineReq as? Parcelable
                    if (pr != null) putExtra(EXTRA_INLINE_REQ, pr)
                }
                // Autofill auth ke liye PendingIntent MUTABLE hona zaroori hai (Android 12+)
                val pending = PendingIntent.getActivity(this, nextCode(), authIntent, mutableFlags())

                val presentation = RemoteViews(packageName, R.layout.autofill_item)
                presentation.setTextViewText(R.id.tvAutofillSite, "BSR Pro")
                presentation.setTextViewText(R.id.tvAutofillUser, "Unlock karke fill karein")

                val ids: Array<AutofillId> = (usernameIds + passwordIds).toTypedArray()
                val fb = FillResponse.Builder()
                if (Build.VERSION.SDK_INT >= 30 && inlineReq != null) {
                    val ip = InlineHelper.make(this, inlineReq, 0, "BSR Pro", "Unlock karke fill karein")
                    InlineHelper.setAuth(fb, ids, pending.intentSender, presentation, ip)
                } else {
                    fb.setAuthentication(ids, pending.intentSender, presentation)
                }
                val response = fb.build()
                AutofillLog.add(this, "response: LOCKED — unlock chip bheja")
                callback.onSuccess(response)
                return
            }

            // Vault unlocked: seedha suggestions dikhao
            val resp = buildResponse(this, usernameIds, passwordIds, webDomain, appId, inlineReq)
            AutofillLog.add(this, "response: matches=" + findMatches(webDomain, appId).size + " bheja=" + (resp != null))
            callback.onSuccess(resp)
        } catch (e: Exception) {
            AutofillLog.add(this, "ERROR fill: " + e.javaClass.simpleName + " " + e.message)
            try { callback.onSuccess(null) } catch (ignored: Exception) { }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        try {
            val structure = request.fillContexts.lastOrNull()?.structure
            if (structure == null) {
                AutofillLog.add(this, "save: FAIL reason=no structure")
                callback.onFailure("Save data nahi mila")
                return
            }

            val parser = StructureParser(structure)
            parser.parse()
            val passwordCount = parser.passwordIds.size
            val usernameCount = parser.usernameIds.size
            val appId = structure.activityComponent?.packageName ?: ""
            val webDomain = parser.webDomain ?: ""
            val domain = domainOf(webDomain)
            AutofillLog.add(
                this,
                "save: passwordIds=$passwordCount, usernameIds=$usernameCount, domain=${domain.ifEmpty { "-" }}, pkg=${appId.ifEmpty { "-" }}"
            )

            if (!VaultManager.isUnlocked) {
                AutofillLog.add(this, "save: vault locked=Yes")
                AutofillLog.add(this, "save: FAIL reason=vault locked")
                callback.onFailure("Vault locked hai — pehle BSR Pro unlock karein")
                return
            }
            AutofillLog.add(this, "save: vault locked=No")

            val pass = parser.passwordText
            val user = parser.usernameText
            if (pass.isEmpty()) {
                AutofillLog.add(this, "save: FAIL reason=password empty")
                callback.onFailure("Password nahi mila")
                return
            }

            if (appId in BROWSERS && domain.isEmpty()) {
                AutofillLog.add(this, "save: FAIL reason=browser domain missing")
                callback.onFailure("Browser domain nahi mila")
                return
            }

            val existing = findMatches(webDomain, appId).find { it.username == user }
            AutofillLog.add(this, "save: existing=${if (existing != null) "Yes" else "No"}")

            val success = if (existing != null) {
                VaultManager.update(this, existing.copy(username = if (user.isNotEmpty()) user else existing.username, password = pass))
            } else {
                VaultManager.add(
                    this,
                    PasswordEntry(
                        site = if (domain.isNotEmpty()) domain else appId,
                        url = if (domain.isNotEmpty()) "https://$domain" else "",
                        appPackage = if (appId.isNotEmpty() && appId !in BROWSERS) appId else null,
                        username = user,
                        password = pass
                    )
                )
            }

            if (success) {
                AutofillLog.add(this, "save: ${if (existing != null) "UPDATE" else "ADD"} SUCCESS")
                callback.onSuccess()
            } else {
                AutofillLog.add(this, "save: FAIL reason=vault write returned false")
                callback.onFailure("Save nahi ho paya")
            }
        } catch (e: Exception) {
            AutofillLog.add(this, "save: FAIL reason=${e.javaClass.simpleName}: ${e.message}")
            try { callback.onFailure("Save nahi ho paya") } catch (ignored: Exception) { }
        }
    }
}

// ── Structure Parser ──
class StructureParser(private val structure: AssistStructure) {

    val usernameIds = mutableListOf<AutofillId>()
    val passwordIds = mutableListOf<AutofillId>()
    var webDomain: String? = null
    var usernameText: String = ""
    var passwordText: String = ""
    /** Diagnostics ke liye: pehle kuch fields ka chhota sa vivaran (koi value nahi). */
    val notes = mutableListOf<String>()

    private class Cand(val order: Int, val id: AutofillId, val text: String)
    private val candidates = mutableListOf<Cand>()
    private var order = 0
    private var firstPasswordOrder = Int.MAX_VALUE

    private fun textOf(node: AssistStructure.ViewNode): String {
        val v = node.autofillValue ?: return ""
        return if (v.isText) v.textValue.toString() else ""
    }

    fun parse() {
        for (i in 0 until structure.windowNodeCount) {
            parseNode(structure.getWindowNodeAt(i).rootViewNode)
        }
        // Agar password field mila par username pehchana nahi gaya: password se theek pehle wala text field username maano
        if (passwordIds.isNotEmpty() && usernameIds.isEmpty()) {
            val prev = candidates.filter { it.order < firstPasswordOrder }.maxByOrNull { it.order }
            if (prev != null) {
                usernameIds.add(prev.id)
                if (usernameText.isEmpty()) usernameText = prev.text
                notes.add("username=fallback(prev text field)")
            }
        }
    }

    private fun parseNode(node: AssistStructure.ViewNode) {
        node.webDomain?.let { if (webDomain == null && it.isNotEmpty()) webDomain = it }

        // FIX 3: chhupe hue (GONE/INVISIBLE) node aur unke bachche skip
        if (node.visibility != android.view.View.VISIBLE) return

        // FIX 3: importantForAutofill (API 30+). NO = sirf yeh node skip; *_EXCLUDE_DESCENDANTS = bachche bhi skip.
        var skipSelf = false
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val imp = node.importantForAutofill
            if (imp == android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS) return
            if (imp == android.view.View.IMPORTANT_FOR_AUTOFILL_NO) skipSelf = true
        }

        val autofillId = node.autofillId
        if (autofillId != null && !skipSelf && node.autofillType == android.view.View.AUTOFILL_TYPE_TEXT) {
            // Chrome / WebView: HTML attributes (type, name, id, autocomplete ...) yahin milte hain
            var hType = ""; var hName = ""; var hId = ""; var hAuto = ""; var hPh = ""; var hAria = ""
            val attrs = node.htmlInfo?.attributes
            if (attrs != null) {
                for (p in attrs) {
                    val k = (p.first ?: "").lowercase()
                    val v = p.second ?: ""
                    when (k) {
                        "type" -> hType = v
                        "name" -> hName = v
                        "id" -> hId = v
                        "autocomplete" -> hAuto = v
                        "placeholder" -> hPh = v
                        "aria-label" -> hAria = v
                    }
                }
            }
            val d = FieldClassifier.Desc(
                hints = (node.autofillHints ?: emptyArray()).toList(),
                inputType = node.inputType,
                idEntry = node.idEntry ?: "",
                hint = node.hint ?: "",
                contentDesc = node.contentDescription?.toString() ?: "",
                htmlType = hType, htmlName = hName, htmlId = hId,
                htmlAutocomplete = hAuto, htmlPlaceholder = hPh, htmlAria = hAria
            )
            val kind = FieldClassifier.classify(d)
            val myOrder = order++
            when (kind) {
                FieldClassifier.Kind.PASSWORD -> {
                    passwordIds.add(autofillId)
                    if (myOrder < firstPasswordOrder) firstPasswordOrder = myOrder
                    if (passwordText.isEmpty()) passwordText = textOf(node)
                }
                FieldClassifier.Kind.USERNAME -> {
                    usernameIds.add(autofillId)
                    if (usernameText.isEmpty()) usernameText = textOf(node)
                }
                FieldClassifier.Kind.TEXT -> candidates.add(Cand(myOrder, autofillId, textOf(node)))
                FieldClassifier.Kind.IGNORE -> { }
            }
            if (notes.size < 6 && kind != FieldClassifier.Kind.IGNORE) {
                notes.add(kind.name.substring(0, 1) + "(id=" + (node.idEntry ?: "-") + " it=0x" +
                    Integer.toHexString(node.inputType) + (if (hType.isNotEmpty()) " html:" + hType else "") + ")")
            }
        }

        for (i in 0 until node.childCount) parseNode(node.getChildAt(i))
    }
}
