package com.teamnak.nakassist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Shows one client's full aggregated thread plus any pending Away Mode draft, with Copy actions. */
class ThreadActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BUYER_KEY = "buyer_key"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)

        ConversationCache.init(this)
        val key = intent.getStringExtra(EXTRA_BUYER_KEY) ?: run { finish(); return }
        val displayName = ConversationCache.displayNameFor(key)
        val thread = ConversationCache.threadFor(key)

        findViewById<TextView>(R.id.tvClientName).text = displayName
        findViewById<TextView>(R.id.tvThread).text =
            if (thread.isBlank()) "No messages yet." else thread

        ConversationCache.markRead(key)

        val draft = ConversationCache.getDraft(key)
        val cardDraft = findViewById<View>(R.id.cardDraft)
        if (!draft.isNullOrBlank()) {
            cardDraft.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvDraft).text = draft
            findViewById<View>(R.id.btnCopyDraft).setOnClickListener {
                copyToClipboard("Draft reply", draft)
            }
        } else {
            cardDraft.visibility = View.GONE
        }

        findViewById<View>(R.id.btnCopyAll).setOnClickListener {
            val formatted = "Conversation with $displayName:\n\n$thread"
            copyToClipboard("Fiverr conversation", formatted)
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Copied — paste it into Claude", Toast.LENGTH_SHORT).show()
    }
}
