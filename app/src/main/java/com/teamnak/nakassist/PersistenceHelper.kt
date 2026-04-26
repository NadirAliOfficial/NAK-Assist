package com.teamnak.nakassist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PersistenceHelper {

    private const val PREFS = "nak_settings"

    // ── Away Mode ─────────────────────────────────────────────────────────

    fun saveAwayMode(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("away_mode", on).apply()
    }

    fun loadAwayMode(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("away_mode", false)
    }

    // ── Stay Online ───────────────────────────────────────────────────────

    fun saveStayOnline(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("stay_online", on).apply()
    }

    fun loadStayOnline(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("stay_online", false)
    }

    fun saveStayOnlineInterval(ctx: Context, seconds: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("stay_online_interval", seconds).apply()
    }

    fun loadStayOnlineInterval(ctx: Context): Int {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("stay_online_interval", 21)
    }

    // ── Conversation Cache ────────────────────────────────────────────────

    fun saveConversations(ctx: Context, data: Map<String, List<String>>) {
        val root = JSONObject()
        data.forEach { (buyer, messages) ->
            val arr = JSONArray()
            messages.forEach { arr.put(it) }
            root.put(buyer, arr)
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("conversations", root.toString()).apply()
    }

    fun loadConversations(ctx: Context): Map<String, MutableList<String>> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("conversations", null) ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            val result = mutableMapOf<String, MutableList<String>>()
            root.keys().forEach { buyer ->
                val arr = root.getJSONArray(buyer)
                val msgs = mutableListOf<String>()
                for (i in 0 until arr.length()) msgs.add(arr.getString(i))
                result[buyer] = msgs
            }
            result
        } catch (_: Exception) { emptyMap() }
    }

    // ── Quick Replies ─────────────────────────────────────────────────────

    fun saveQuickReplies(ctx: Context, replies: List<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("quick_replies", replies.joinToString("||")).apply()
    }

    fun loadQuickReplies(ctx: Context): List<String> {
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("quick_replies", null)
        if (saved.isNullOrBlank()) return defaultQuickReplies()
        return saved.split("||").filter { it.isNotBlank() }
    }

    private fun defaultQuickReplies(): List<String> = listOf(
        "Sure, share your requirements and I'll review them.",
        "Got it, I'll send you a custom offer shortly.",
        "Sounds great!",
        "Let me check and get back to you.",
        "Yes, I can handle that. What's the timeline?",
        "Check out my portfolio:\nhttps://www.theteamnak.com\nhttps://github.com/NadirAliOfficial"
    )
}
