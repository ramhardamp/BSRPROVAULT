package com.babasitaram.pro.autofill

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.babasitaram.pro.AutofillLog
import com.babasitaram.pro.LoginActivity
import com.babasitaram.pro.R

/**
 * Keyboard ke upar (Google Password Manager / Keepass jaisi) inline suggestion chips.
 * Sirf Android 11+ (API 30) par use hota hai — isliye alag class mein.
 */
@TargetApi(Build.VERSION_CODES.R)
object InlineHelper {

    fun maxCount(req: Any?): Int {
        val r = req as? InlineSuggestionsRequest ?: return 0
        return r.maxSuggestionCount
    }

    /** FIX 4: keyboard ne kitne specs bheje (chips ki asli seema = min(maxCount, specCount)). */
    fun specCount(req: Any?): Int {
        val r = req as? InlineSuggestionsRequest ?: return 0
        return r.inlinePresentationSpecs.size
    }

    fun describe(req: Any?): String {
        val r = req as? InlineSuggestionsRequest ?: return "inline request nahi aayi (keyboard/app support nahi)"
        return "inline request AAYI: max=" + r.maxSuggestionCount + " specs=" + r.inlinePresentationSpecs.size +
            " -> chips<=" + minOf(r.maxSuggestionCount, r.inlinePresentationSpecs.size)
    }

    /** Ek chip ka InlinePresentation (ya null agar keyboard iski style support nahi karta). */
    fun make(ctx: Context, req: Any?, index: Int, title: String, subtitle: String): Any? {
        val r = req as? InlineSuggestionsRequest ?: return null
        val specs = r.inlinePresentationSpecs
        if (specs.isEmpty()) { AutofillLog.add(ctx, "inline: specs khali"); return null }
        // FIX 4: index kabhi specs se bahar na jaaye (limit pehle se min(max, specs) hai)
        val spec = specs[index % specs.size]
        if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) {
            AutofillLog.add(ctx, "inline: keyboard ka style v1 support nahi karta")
            return null
        }
        return try {
            // FIX 2: har chip ke liye alag request code — warna sab chips ek hi PendingIntent share karte hain
            val rc = (System.nanoTime() and 0x0FFFFFFFL).toInt() + index
            val attribution = PendingIntent.getActivity(
                ctx, rc, Intent(ctx, LoginActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val b = InlineSuggestionUi.newContentBuilder(attribution).setTitle(title)
            if (subtitle.isNotEmpty()) b.setSubtitle(subtitle)
            b.setStartIcon(Icon.createWithResource(ctx, R.mipmap.ic_launcher))
            b.setContentDescription(title)
            InlinePresentation(b.build().slice, spec, false)
        } catch (e: Exception) {
            AutofillLog.add(ctx, "inline chip ERROR: " + e.javaClass.simpleName + " " + e.message)
            null
        }
    }

    fun setValue(
        ds: Dataset.Builder, id: AutofillId, value: AutofillValue?,
        dropdown: RemoteViews, inline: Any?
    ) {
        val ip = inline as? InlinePresentation
        if (ip != null) ds.setValue(id, value, dropdown, ip) else ds.setValue(id, value, dropdown)
    }

    fun setAuth(
        fb: FillResponse.Builder, ids: Array<AutofillId>, sender: IntentSender,
        dropdown: RemoteViews, inline: Any?
    ) {
        val ip = inline as? InlinePresentation
        if (ip != null) fb.setAuthentication(ids, sender, dropdown, ip)
        else fb.setAuthentication(ids, sender, dropdown)
    }
}
