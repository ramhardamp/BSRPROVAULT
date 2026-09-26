package com.babasitaram.pro

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object TrashUi {

    fun show(a: Activity) {
        val list = VaultManager.getTrash()
        if (list.isEmpty()) {
            Toast.makeText(a, "Trash khali hai", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = Array<CharSequence>(list.size) { i ->
            val e = list[i]
            (if (e.site.isEmpty()) "(bina naam)" else e.site) + "  •  " + e.username
        }
        AlertDialog.Builder(a)
            .setTitle("🗑️ Trash (" + list.size + ") — 30 din baad auto-delete")
            .setItems(labels) { _, which -> itemActions(a, list[which]) }
            .setNeutralButton("Sab khali karo") { _, _ -> confirmEmpty(a) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun itemActions(a: Activity, e: PasswordEntry) {
        AlertDialog.Builder(a)
            .setTitle(e.site)
            .setMessage("Is entry ka kya karein?")
            .setPositiveButton("Restore") { _, _ ->
                runCatching { VaultManager.restore(a, e.id) }
                    .onSuccess { ok ->
                        Toast.makeText(
                            a,
                            if (ok) "✓ Wapas aa gaya" else "Restore nahi ho paaya",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .onFailure { err ->
                        Toast.makeText(a, "Restore failed: " + (err.message ?: err.javaClass.simpleName), Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Hamesha ke liye delete") { _, _ ->
                runCatching { VaultManager.deleteForever(a, e.id) }
                    .onSuccess { ok ->
                        Toast.makeText(
                            a,
                            if (ok) "Delete ho gaya" else "Delete nahi ho paaya",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .onFailure { err ->
                        Toast.makeText(a, "Delete failed: " + (err.message ?: err.javaClass.simpleName), Toast.LENGTH_LONG).show()
                    }
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun confirmEmpty(a: Activity) {
        AlertDialog.Builder(a)
            .setTitle("Trash khali karein?")
            .setMessage("Trash ki saari entries hamesha ke liye delete ho jayengi.")
            .setPositiveButton("Haan, delete") { _, _ ->
                runCatching { VaultManager.emptyTrash(a) }
                    .onSuccess { ok ->
                        Toast.makeText(
                            a,
                            if (ok) "Trash khali" else "Trash empty nahi ho paaya",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .onFailure { err ->
                        Toast.makeText(a, "Trash empty failed: " + (err.message ?: err.javaClass.simpleName), Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
