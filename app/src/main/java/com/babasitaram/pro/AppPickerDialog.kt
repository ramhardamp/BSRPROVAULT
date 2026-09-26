package com.babasitaram.pro

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText

class AppPickerDialog(
    private val context: Context,
    private val currentPackage: String? = null,
    private val onAppSelected: (packageName: String, appName: String) -> Unit
) {

    private data class AppInfo(
        val packageName: String,
        val appName: String,
        val icon: Drawable
    )

    fun show() {
        val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val primaryText: Int = if (night) Color.WHITE else Color.BLACK
        val secondaryText: Int = if (night) -5195837 else -11184811
        val pm = context.packageManager
        // App Picker contract: show installed applications, not only apps with a launcher activity.
        val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            .mapNotNull { ai ->
                if (ai.packageName.equals(context.packageName, ignoreCase = true)) return@mapNotNull null
                val candidates = listOf(
                    runCatching { ai.loadLabel(pm)?.toString()?.trim().orEmpty() }.getOrDefault(""),
                    runCatching { pm.getApplicationLabel(ai).toString().trim() }.getOrDefault("")
                )
                val label = candidates.firstOrNull { raw ->
                    raw.isNotBlank() &&
                        !raw.equals(ai.packageName, ignoreCase = true) &&
                        !raw.startsWith(ai.packageName + ".", ignoreCase = true)
                } ?: ai.packageName.substringAfterLast('.').ifEmpty { ai.packageName }
                if (label == ai.packageName || label.contains("com.")) {
                    Log.w("BSR_AppPicker", "label fallback package=" + ai.packageName)
                }
                val icon = try { pm.getApplicationIcon(ai) } catch (_: Exception) { null }
                    ?: return@mapNotNull null
                AppInfo(ai.packageName, label, icon)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }

        if (apps.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("Select App")
                .setMessage("कोई launchable app नहीं मिला।")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 0, 8, 0)
        }
        val searchLayout = TextInputLayout(context).apply {
            hint = "Search app name or package..."
            isHintEnabled = true
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_FILLED
            setBoxCornerRadii(14f, 14f, 14f, 14f)
            boxStrokeWidth = 1
            boxStrokeWidthFocused = 2
            setPadding(0, 8, 0, 8)
        }
        val search = TextInputEditText(context).apply {
            isSingleLine = true
            textSize = 15f
            setTextColor(primaryText)
            setHintTextColor(if (night) 0xFF9AA4B2.toInt() else 0xFF777777.toInt())
            setPadding(12, 0, 12, 0)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        searchLayout.addView(search)
        root.addView(searchLayout, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val listView = ListView(context).apply {
            divider = null
            dividerHeight = 0
            setPadding(0, 8, 0, 8)
        }
        root.addView(listView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val dialog = AlertDialog.Builder(context)
            .setTitle("Select App")
            .setView(root)
            .setNegativeButton("Cancel", null)
            .create()

        val adapter = AppAdapter(context, apps, currentPackage, primaryText, secondaryText)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = adapter.getItem(position)
            onAppSelected(selected.packageName, selected.appName)
            dialog.dismiss()
        }

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                adapter.filter(s?.toString()?.trim().orEmpty())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(if (night) 0xFF111827.toInt() else android.R.color.white)
            searchLayout.setBoxStrokeColor(0xFF4B8F8A.toInt())
            val alertTitleId = context.resources.getIdentifier("alertTitle", "id", "android")
            dialog.findViewById<TextView>(alertTitleId)?.apply {
                setTextColor(primaryText)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFF1565C0.toInt())
        }
        dialog.show()
    }

    private class AppAdapter(
        private val context: Context,
        private val allApps: List<AppInfo>,
        private val selectedPackage: String?,
        private val primaryText: Int,
        private val secondaryText: Int
    ) : BaseAdapter() {
        private var shownApps: List<AppInfo> = allApps

        override fun getCount(): Int = shownApps.size
        override fun getItem(position: Int): AppInfo = shownApps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        fun filter(query: String) {
            shownApps = if (query.isBlank()) allApps else allApps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val app = shownApps[position]
            val selected = app.packageName == selectedPackage

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 10, 12, 10)
                setBackgroundColor(if (selected) 0x1A2196F3 else Color.TRANSPARENT)
            }

            val size = (48 * context.resources.displayMetrics.density).toInt()
            val icon = ImageView(context).apply {
                setImageDrawable(app.icon)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    rightMargin = 14
                }
            }

            val textBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val name = TextView(context).apply {
                text = app.appName
                textSize = 16f
                setTextColor(primaryText)
                setTypeface(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
            }

            val packageName = TextView(context).apply {
                text = app.packageName
                textSize = 12f
                setTextColor(secondaryText)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            textBox.addView(name)
            textBox.addView(packageName)
            root.addView(icon)
            root.addView(textBox)
            return root
        }
    }
}
