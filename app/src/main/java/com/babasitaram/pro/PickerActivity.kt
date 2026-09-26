package com.babasitaram.pro

import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.babasitaram.pro.autofill.BSRAutofillService

/** Autofill ke "🔍 Vault se chunein" chip ka screen: koi bhi entry search karke chunein. */
class PickerActivity : AppCompatActivity() {

    private var usernameIds: ArrayList<AutofillId> = ArrayList()
    private var passwordIds: ArrayList<AutofillId> = ArrayList()
    private var shown: List<PasswordEntry> = emptyList()
    private lateinit var adapter: ArrayAdapter<String>
    private val labels = ArrayList<String>()

    @Suppress("DEPRECATION")
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (!VaultManager.isUnlocked) {
            Toast.makeText(this, "Pehle BSR Pro unlock karein", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        usernameIds = intent.getParcelableArrayListExtra<AutofillId>(BSRAutofillService.EXTRA_USERNAME_IDS) ?: ArrayList()
        passwordIds = intent.getParcelableArrayListExtra<AutofillId>(BSRAutofillService.EXTRA_PASSWORD_IDS) ?: ArrayList()

        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF0B1220.toInt())
        root.setPadding(pad, pad, pad, pad)

        val title = TextView(this)
        title.text = "🔐 BSR Pro — login chunein"
        title.textSize = 18f
        title.setTextColor(0xFFEEF2FF.toInt())
        title.gravity = Gravity.CENTER_VERTICAL
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val search = EditText(this)
        search.hint = "Search..."
        search.setHintTextColor(0xFF64748B.toInt())
        search.setTextColor(0xFFEEF2FF.toInt())
        search.setSingleLine(true)
        val sp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        sp.topMargin = (12 * dp).toInt()
        root.addView(search, sp)

        val list = ListView(this)
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                if (v is TextView) {
                    v.setTextColor(0xFFE2E8F0.toInt())
                    v.textSize = 15f
                }
                return v
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            if (position >= 0 && position < shown.size) returnEntry(shown[position])
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) { refresh(e?.toString() ?: "") }
            override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        refresh("")
    }

    private fun refresh(q: String) {
        val all = VaultManager.getPasswords().filter { it.type != "note" && it.password.isNotEmpty() }
        val f = if (q.isBlank()) all else all.filter {
            it.site.contains(q, true) || it.username.contains(q, true) || it.url.contains(q, true)
        }
        shown = f.sortedBy { it.site.lowercase() }
        labels.clear()
        for (e in shown) labels.add(e.site + "  •  " + e.username.ifEmpty { e.mobile })
        adapter.notifyDataSetChanged()
    }

    private fun returnEntry(e: PasswordEntry) {
        val ds = Dataset.Builder()
        for (id in usernameIds) ds.setValue(id, AutofillValue.forText(e.username.ifEmpty { e.mobile }))
        for (id in passwordIds) ds.setValue(id, AutofillValue.forText(e.password))
        val result = Intent()
        result.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, ds.build())
        AutofillLog.add(this, "picker: entry chuni gayi")
        setResult(RESULT_OK, result)
        finish()
    }
}
