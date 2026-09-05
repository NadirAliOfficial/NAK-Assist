package com.teamnak.nakassist

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Lists every Fiverr client with a saved conversation (captured passively from
 * notifications — nothing here reads or sends anything on Fiverr's behalf).
 * Tap a client to see the full thread and copy it in one go for pasting elsewhere.
 */
class ClientsActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clients)

        ConversationCache.init(this)
        listContainer = findViewById(R.id.llClientList)

        findViewById<View>(R.id.btnClearAll).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear all client messages?")
                .setMessage("This removes every saved conversation from this device.")
                .setPositiveButton("Clear") { _, _ ->
                    ConversationCache.clear()
                    render()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.navMessages
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navMessages -> true
                R.id.navSettings -> {
                    startActivity(Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    overridePendingTransition(0, 0)
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        listContainer.removeAllViews()
        val keys = ConversationCache.buyerKeysMostRecentFirst()
        findViewById<View>(R.id.tvEmpty).visibility = if (keys.isEmpty()) View.VISIBLE else View.GONE

        val density = resources.displayMetrics.density
        keys.forEach { key -> listContainer.addView(buildRow(key, density)) }
    }

    private fun buildRow(key: String, density: Float): View {
        val unread = ConversationCache.isUnread(key)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt())
            setOnClickListener {
                startActivity(Intent(this@ClientsActivity, ThreadActivity::class.java)
                    .putExtra(ThreadActivity.EXTRA_BUYER_KEY, key))
            }
            isClickable = true
            isFocusable = true
            val outValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }

        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val name = TextView(this).apply {
            text = ConversationCache.displayNameFor(key)
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor(if (unread) "#FFFFFF" else "#AAAAAA"))
            setTypeface(typeface, if (unread) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        nameRow.addView(name)

        if (unread) {
            val badge = TextView(this).apply {
                text = " NEW "
                textSize = 11f
                setTextColor(android.graphics.Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(android.graphics.Color.parseColor("#4F8CFF"))
                }
                setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
            }
            nameRow.addView(badge)
        }

        val preview = TextView(this).apply {
            text = ConversationCache.lastMessagePreview(key)
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(android.graphics.Color.parseColor("#888888"))
            setPadding(0, (4 * density).toInt(), 0, 0)
        }

        row.addView(nameRow)
        row.addView(preview)

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt())
            setBackgroundColor(android.graphics.Color.parseColor("#2A2A2A"))
        }

        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrapper.addView(row)
        wrapper.addView(divider)
        return wrapper
    }
}
