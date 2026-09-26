package com.babasitaram.pro

import android.app.Activity
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ExportSecurity {
    private const val PREFS = "bsr_export_security"
    private const val KEY_FAILS = "fails"
    private const val KEY_COOLDOWN_UNTIL = "cooldown_until"
    private const val MAX_FAILURES = 3
    private const val COOLDOWN_MS = 30_000L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Backward-compatible export gate. The verified password is intentionally not exposed. */
    fun requireMasterPassword(activity: Activity, onVerified: () -> Unit) {
        requireMasterPasswordInternal(
            activity, "Confirm Master Password",
            "Export करने के लिए Master Password डालें।\nExported file में plaintext passwords होंगे।",
            "Verify & Export",
            "Export verification temporarily locked. %s sec baad try karein.",
            { _ -> ExportGate.issue(); onVerified() }
        )
    }

    /** Import gate: verify the current vault master password before ANY import parsing/write. */
    fun requireMasterPasswordForImport(activity: Activity, onVerified: (String) -> Unit) {
        requireMasterPasswordInternal(
            activity, "Confirm Master Password",
            "Backup import करने से पहले Master Password verify करें।\nगलत password पर import नहीं होगा।",
            "Verify & Import",
            "Import verification temporarily locked. %s sec baad try karein.",
            onVerified
        )
    }

    private fun requireMasterPasswordInternal(
        activity: Activity,
        title: String,
        message: String,
        actionLabel: String,
        cooldownMessage: String,
        onVerified: (String) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val remaining = cooldownRemainingMs(activity)
        if (remaining > 0L) {
            Toast.makeText(activity, cooldownMessage.format((remaining + 999L) / 1000L), Toast.LENGTH_LONG).show()
            return
        }

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_export_master, null, false)
        val inputLayout = view.findViewById<TextInputLayout>(R.id.tilExportPassword)
        val input = view.findViewById<EditText>(R.id.etExportPassword)
        val error = view.findViewById<TextView>(R.id.tvExportPasswordError)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        val dialog = AlertDialog.Builder(activity)
            .setTitle(title).setMessage(message).setView(view)
            .setNegativeButton("Cancel") { _, _ -> wipeInput(input) }
            .setPositiveButton(actionLabel, null).create()
        dialog.setOnDismissListener { wipeInput(input) }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_export_bg)
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            positive.visibility = android.view.View.VISIBLE
            negative.visibility = android.view.View.VISIBLE
            positive.text = actionLabel
            negative.text = "Cancel"
            styleDialogButtons(positive, negative, activity)
            positive.setOnClickListener {
                error.visibility = TextView.GONE
                inputLayout.error = null
                val chars = input.text.toString().toCharArray()
                if (chars.isEmpty()) {
                    error.text = "Master Password required"
                    error.visibility = TextView.VISIBLE
                    inputLayout.error = "Required"
                    wipe(chars)
                    return@setOnClickListener
                }
                positive.isEnabled = false
                input.isEnabled = false
                scope.launch {
                    var verifiedPassword: String? = null
                    val ok = try {
                        val candidate = String(chars)
                        val verified = withContext(Dispatchers.Default) { VaultManager.verifyMaster(activity, candidate) }
                        if (verified) verifiedPassword = candidate
                        verified
                    } catch (_: Exception) { false } finally { wipe(chars) }
                    input.isEnabled = true
                    positive.isEnabled = true
                    if (ok) {
                        clearFailures(activity)
                        wipeInput(input)
                        dialog.dismiss()
                        verifiedPassword?.let(onVerified)
                    } else {
                        verifiedPassword = null
                        val failures = recordFailure(activity)
                        wipeInput(input)
                        input.requestFocus()
                        if (failures >= MAX_FAILURES) {
                            dialog.dismiss()
                            Toast.makeText(activity, "3 wrong attempts. Verification locked for 30 seconds.", Toast.LENGTH_LONG).show()
                        } else {
                            error.text = "Incorrect master password"
                            error.visibility = TextView.VISIBLE
                            inputLayout.error = "Incorrect master password"
                            Toast.makeText(activity, "Incorrect master password", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        dialog.show()
        input.requestFocus()
    }
    private fun styleDialogButtons(
        positive: android.widget.Button,
        negative: android.widget.Button,
        activity: Activity
    ) {
        val density = activity.resources.displayMetrics.density
        val radius = 10f * density

        positive.background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(androidx.core.content.ContextCompat.getColor(activity, R.color.accent))
        }
        positive.setTextColor(Color.WHITE)
        positive.minWidth = (132 * density).toInt()
        positive.setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)

        negative.background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            setStroke(1, androidx.core.content.ContextCompat.getColor(activity, R.color.outline))
        }
        negative.setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.text_primary))
        negative.minWidth = (96 * density).toInt()
        negative.setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cooldownRemainingMs(ctx: Context): Long =
        (prefs(ctx).getLong(KEY_COOLDOWN_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    private fun recordFailure(ctx: Context): Int {
        val p = prefs(ctx)
        val fails = p.getInt(KEY_FAILS, 0) + 1
        if (fails >= MAX_FAILURES) {
            p.edit()
                .putInt(KEY_FAILS, 0)
                .putLong(KEY_COOLDOWN_UNTIL, System.currentTimeMillis() + COOLDOWN_MS)
                .apply()
        } else {
            p.edit().putInt(KEY_FAILS, fails).apply()
        }
        return fails
    }

    private fun clearFailures(ctx: Context) {
        prefs(ctx).edit().putInt(KEY_FAILS, 0).putLong(KEY_COOLDOWN_UNTIL, 0L).apply()
    }

    private fun wipeInput(input: EditText) { input.text?.clear() }

    private fun wipe(chars: CharArray) { java.util.Arrays.fill(chars, '\u0000') }
}