package com.babasitaram.pro

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/** Chrome native third-party Autofill status + settings launcher. */
object ChromeAutofill {
    enum class Status { ENABLED, DISABLED, UNAVAILABLE }

    private const val CHROME_PACKAGE = "com.android.chrome"
    private const val PROVIDER_SUFFIX = ".AutofillThirdPartyModeContentProvider"
    private const val COLUMN = "autofill_third_party_state"
    private const val PATH = "autofill_third_party_mode"

    fun getStatus(ctx: Context): Status {
        return try {
            val uri = android.net.Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(CHROME_PACKAGE + PROVIDER_SUFFIX)
                .path(PATH)
                .build()
            val cursor = ctx.contentResolver.query(
                uri, arrayOf(COLUMN), null, null, null
            ) ?: return Status.UNAVAILABLE
            cursor.use {
                if (!it.moveToFirst()) return Status.UNAVAILABLE
                val index = it.getColumnIndex(COLUMN)
                if (index < 0) return Status.UNAVAILABLE
                if (it.getInt(index) == 0) Status.DISABLED else Status.ENABLED
            }
        } catch (_: Exception) {
            Status.UNAVAILABLE
        }
    }

    fun openSettings(activity: android.app.Activity) {
        try {
            val intent = Intent(Intent.ACTION_APPLICATION_PREFERENCES).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_APP_BROWSER)
                addCategory(Intent.CATEGORY_PREFERENCE)
                setPackage(CHROME_PACKAGE)
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            try {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
                Toast.makeText(
                    activity,
                    "Chrome → Settings → Autofill services → Autofill using another service ON karein",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Exception) { }
        }
    }
}
