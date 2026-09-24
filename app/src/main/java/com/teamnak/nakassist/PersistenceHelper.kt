package com.teamnak.nakassist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PersistenceHelper {

    private const val PREFS = "nak_settings"

    // ── Keep Screen Awake ────────────────────────────────────────────────

    fun saveKeepAwake(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("keep_awake", on).apply()
    }

    fun loadKeepAwake(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("keep_awake", false)
    }

    // ── Away Mode ────────────────────────────────────────────────────────
    // Draft-only: generates a reply and posts it as a notification for Nadir
    // to review and send himself. Never auto-sends anything.

    fun saveAwayMode(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("away_mode", on).apply()
    }

    fun loadAwayMode(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("away_mode", false)
    }

    // ── Conversation Cache ────────────────────────────────────────────────

    // Messages are stored as {"t": epochMillis, "m": text} so they can auto-delete after 24h.
    // Older builds stored bare strings; those are stamped with `now` on load, so they get a
    // full 24h after the update instead of vanishing instantly.

    fun saveConversations(ctx: Context, data: Map<String, List<ConversationCache.Message>>) {
        val root = JSONObject()
        data.forEach { (buyer, messages) ->
            val arr = JSONArray()
            messages.forEach { arr.put(messageToJson(it)) }
            root.put(buyer, arr)
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("conversations", root.toString()).apply()
    }

    fun loadConversations(ctx: Context, now: Long): Map<String, MutableList<ConversationCache.Message>> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("conversations", null) ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            val result = mutableMapOf<String, MutableList<ConversationCache.Message>>()
            root.keys().forEach { buyer ->
                val arr = root.getJSONArray(buyer)
                val msgs = mutableListOf<ConversationCache.Message>()
                for (i in 0 until arr.length()) jsonToMessage(arr.get(i), now)?.let { msgs.add(it) }
                result[buyer] = msgs
            }
            result
        } catch (_: Exception) { emptyMap() }
    }

    private fun messageToJson(e: ConversationCache.Message) = JSONObject().put("t", e.time).put("m", e.text)

    private fun jsonToMessage(v: Any?, now: Long): ConversationCache.Message? = when (v) {
        is JSONObject -> ConversationCache.Message(v.optLong("t", now), v.optString("m"))
        is String -> ConversationCache.Message(now, v)
        else -> null
    }

    fun saveDisplayNames(ctx: Context, data: Map<String, String>) {
        val root = JSONObject()
        data.forEach { (key, name) -> root.put(key, name) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("display_names", root.toString()).apply()
    }

    fun loadDisplayNames(ctx: Context): Map<String, String> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("display_names", null) ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            val result = mutableMapOf<String, String>()
            root.keys().forEach { key -> result[key] = root.getString(key) }
            result
        } catch (_: Exception) { emptyMap() }
    }

    fun saveUnread(ctx: Context, keys: Set<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet("unread_buyers", keys).apply()
    }

    fun loadUnread(ctx: Context): Set<String> {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("unread_buyers", emptySet()) ?: emptySet()
    }

    fun saveDrafts(ctx: Context, data: Map<String, ConversationCache.Message>) {
        val root = JSONObject()
        data.forEach { (key, draft) -> root.put(key, messageToJson(draft)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("drafts", root.toString()).apply()
    }

    fun loadDrafts(ctx: Context, now: Long): Map<String, ConversationCache.Message> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("drafts", null) ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            val result = mutableMapOf<String, ConversationCache.Message>()
            root.keys().forEach { key -> jsonToMessage(root.get(key), now)?.let { result[key] = it } }
            result
        } catch (_: Exception) { emptyMap() }
    }

    fun saveSeen(ctx: Context, data: Map<String, Long>) {
        val root = JSONObject()
        data.forEach { (fingerprint, time) -> root.put(fingerprint, time) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("seen_messages", root.toString()).apply()
    }

    fun loadSeen(ctx: Context): Map<String, Long> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("seen_messages", null) ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            val result = mutableMapOf<String, Long>()
            root.keys().forEach { key -> result[key] = root.getLong(key) }
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
