package com.babasitaram.pro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/** BSR Pro ko phone ka default Autofill service banane ka helper. */
object AutofillSetup {

    // FIX 6: "poocha ja chuka hai" ab SharedPreferences mein — app dobara khulne par bhi dialog baar-baar nahi aata
    private fun alreadyPrompted(ctx: Context): Boolean =
        ctx.getSharedPreferences("bsr_setup", Context.MODE_PRIVATE).getBoolean("autofill_prompted", false)

    private fun markPrompted(ctx: Context) =
        ctx.getSharedPreferences("bsr_setup", Context.MODE_PRIVATE).edit().putBoolean("autofill_prompted", true).apply()

    fun isEnabled(ctx: Context): Boolean {
        return try {
            val m = ctx.getSystemService(AutofillManager::class.java)
            m != null && m.hasEnabledAutofillServices()
        } catch (e: Exception) { false }
    }

    /** System ka "Default autofill service badlein?" dialog kholta hai. */
    fun request(activity: Activity) {
        try {
            val i = Intent(
                Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                Uri.parse("package:" + activity.packageName)
            )
            activity.startActivity(i)
        } catch (e: Exception) {
            try {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
                Toast.makeText(
                    activity,
                    "Settings mein 'Autofill service' khoj kar BabaSitaRam Pro chunein",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e2: Exception) { }
        }
    }

    /** Vault unlock hone ke baad ek baar (har app-launch par) poochta hai. */
    fun maybePrompt(activity: Activity) {
        if (isEnabled(activity) || alreadyPrompted(activity)) return
        markPrompted(activity)
        try {
            AlertDialog.Builder(activity)
                .setTitle("Autofill ON karein")
                .setMessage(
                    "BabaSitaRam Pro ko default Autofill service banayein, tabhi dusre apps " +
                    "aur Chrome mein password apne aap fill honge.\n\n" +
                    "Chrome ke liye: Chrome → Settings → Autofill services → " +
                    "\"Autofill using another service\" ON karein."
                )
                .setPositiveButton("Default banayein") { _, _ -> request(activity) }
                .setNegativeButton("Baad mein", null)
                .show()
        } catch (e: Exception) { }
    }
}
