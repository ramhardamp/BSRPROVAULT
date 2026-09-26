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
                VaultManager.restore(a, e.id)
                Toast.makeText(a, "✓ Wapas aa gaya", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hamesha ke liye delete") { _, _ ->
                VaultManager.deleteForever(a, e.id)
                Toast.makeText(a, "Delete ho gaya", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun confirmEmpty(a: Activity) {
        AlertDialog.Builder(a)
            .setTitle("Trash khali karein?")
            .setMessage("Trash ki saari entries hamesha ke liye delete ho jayengi.")
            .setPositiveButton("Haan, delete") { _, _ ->
                VaultManager.emptyTrash(a)
                Toast.makeText(a, "Trash khali", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
