package com.teamnak.nakassist

import android.content.Context

object ConversationCache {

    private const val MAX_BUYERS = 20
    private const val MAX_MESSAGES_PER_BUYER = 30

    private var ctx: Context? = null

    private val cache = object : LinkedHashMap<String, MutableList<String>>(MAX_BUYERS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableList<String>>): Boolean {
            return size > MAX_BUYERS
        }
    }

    // key (lowercased buyer name) -> original display name, e.g. "john_d" -> "John D."
    private val displayNames = mutableMapOf<String, String>()

    // keys with a client message that hasn't been opened/copied yet
    private val unread = mutableSetOf<String>()

    // key -> latest Away Mode AI draft reply for that client, shown inline on the thread screen
    private val drafts = mutableMapOf<String, String>()

    fun init(context: Context) {
        ctx = context.applicationContext
        val saved = PersistenceHelper.loadConversations(context)
        cache.clear()
        cache.putAll(saved)
        displayNames.clear()
        displayNames.putAll(PersistenceHelper.loadDisplayNames(context))
        unread.clear()
        unread.addAll(PersistenceHelper.loadUnread(context))
        drafts.clear()
        drafts.putAll(PersistenceHelper.loadDrafts(context))
    }

    fun addMessage(buyerName: String, message: String) {
        val key = buyerName.trim().lowercase()
        if (key.isBlank()) return
        displayNames[key] = buyerName.trim()
        unread.add(key)
        addToCache(key, "Client: ${message.take(400)}")
        persistMeta()
    }

    fun addReply(buyerName: String, reply: String) {
        addToCache(buyerName.trim().lowercase(), "Nadir: ${reply.take(400)}")
    }

    private fun addToCache(key: String, entry: String) {
        if (key.isBlank()) return
        val messages = cache.getOrPut(key) { mutableListOf() }
        messages.add(entry)
        while (messages.size > MAX_MESSAGES_PER_BUYER) messages.removeAt(0)
        ctx?.let { PersistenceHelper.saveConversations(it, cache) }
    }

    fun getContext(buyerName: String): String? {
        val key = buyerName.trim().lowercase()
        val messages = cache[key] ?: return null
        return if (messages.isEmpty()) null else messages.joinToString("\n")
    }

    fun lastBuyerName(): String? = cache.keys.lastOrNull()

    // ── Client list / aggregator UI support ─────────────────────────────────

    /** Buyer keys, most recently active first. */
    fun buyerKeysMostRecentFirst(): List<String> = cache.keys.reversed()

    fun displayNameFor(key: String): String = displayNames[key] ?: key

    fun isUnread(key: String): Boolean = key in unread

    fun lastMessagePreview(key: String): String =
        cache[key]?.lastOrNull()?.removePrefix("Client: ")?.removePrefix("Nadir: ") ?: ""

    fun threadFor(key: String): String = cache[key]?.joinToString("\n") ?: ""

    fun markRead(key: String) {
        if (unread.remove(key)) persistMeta()
    }

    // ── Away Mode draft (shown inline on the thread screen) ──────────────────

    fun setDraft(buyerName: String, draft: String) {
        val key = buyerName.trim().lowercase()
        if (key.isBlank()) return
        drafts[key] = draft
        ctx?.let { PersistenceHelper.saveDrafts(it, drafts) }
    }

    fun getDraft(key: String): String? = drafts[key]

    fun clearDraft(key: String) {
        if (drafts.remove(key) != null) ctx?.let { PersistenceHelper.saveDrafts(it, drafts) }
    }

    private fun persistMeta() {
        val c = ctx ?: return
        PersistenceHelper.saveDisplayNames(c, displayNames)
        PersistenceHelper.saveUnread(c, unread)
    }

    fun clear() {
        cache.clear()
        displayNames.clear()
        unread.clear()
        drafts.clear()
        ctx?.let {
            PersistenceHelper.saveConversations(it, emptyMap())
            PersistenceHelper.saveDisplayNames(it, emptyMap())
            PersistenceHelper.saveUnread(it, emptySet())
            PersistenceHelper.saveDrafts(it, emptyMap())
        }
    }
}
