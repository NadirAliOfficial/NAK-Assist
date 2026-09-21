package com.teamnak.nakassist

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Copies an Away Mode draft reply to the clipboard when the notification action is tapped. */
class CopyDraftReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_TEXT = "draft_text"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Draft reply", text))
        Toast.makeText(context, "Draft copied — paste it in Fiverr and review before sending", Toast.LENGTH_LONG).show()
    }
}
