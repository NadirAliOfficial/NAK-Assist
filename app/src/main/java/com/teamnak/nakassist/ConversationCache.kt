package com.teamnak.nakassist

import android.content.Context

object ConversationCache {

    private const val MAX_BUYERS = 20
    private const val MAX_MESSAGES_PER_BUYER = 30
    private const val MAX_MESSAGE_LENGTH = 6000 // was 400 — cut off real client briefs

    /** Every message (and draft) disappears from the app 24h after it arrived. */
    const val RETENTION_MS = 24L * 60 * 60 * 1000

    /** One line of a thread. [text] keeps the "Client: " / "Nadir: " prefix the AI prompts expect. */
    data class Message(val time: Long, val text: String)

    // Swappable so the expiry logic can be unit-tested without waiting a day.
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private var ctx: Context? = null

    private val cache = object : LinkedHashMap<String, MutableList<Message>>(MAX_BUYERS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableList<Message>>): Boolean {
            return size > MAX_BUYERS
        }
    }

    // key (lowercased buyer name) -> original display name, e.g. "john_d" -> "John D."
    private val displayNames = mutableMapOf<String, String>()

    // keys with a client message that hasn't been opened/copied yet
    private val unread = mutableSetOf<String>()

    // key -> latest Away Mode AI draft reply for that client, shown inline on the thread screen
    private val drafts = mutableMapOf<String, Message>()

    // "key\nmessage" -> when it was first seen. Fiverr re-posts and stacks old lines into new
    // notifications; this stops them re-appearing (even after Copy All) or re-triggering drafts.
    private val seen = mutableMapOf<String, Long>()

    fun init(context: Context) {
        ctx = context.applicationContext
        val saved = PersistenceHelper.loadConversations(context, clock())
        cache.clear()
        cache.putAll(saved)
        displayNames.clear()
        displayNames.putAll(PersistenceHelper.loadDisplayNames(context))
        unread.clear()
        unread.addAll(PersistenceHelper.loadUnread(context))
        drafts.clear()
        drafts.putAll(PersistenceHelper.loadDrafts(context, clock()))
        seen.clear()
        seen.putAll(PersistenceHelper.loadSeen(context))
        // Write back straight away so entries migrated from the old untimestamped format keep
        // the time they were first loaded, instead of being re-stamped on every launch.
        saveConversations()
        saveDrafts()
        pruneExpired()
    }

    /**
     * Adds a client message. Returns false when nothing new was stored — the message was blank
     * or Fiverr re-posted something already captured — so callers can skip alerts and AI drafts.
     */
    fun addMessage(buyerName: String, message: String): Boolean {
        val key = buyerName.trim().lowercase()
        val text = message.trim().take(MAX_MESSAGE_LENGTH)
        if (key.isBlank() || text.isBlank()) return false
        pruneExpired()

        val fingerprint = "$key\n${text.lowercase()}"
        if (fingerprint in seen) return false
        val now = clock()
        seen[fingerprint] = now

        displayNames[key] = buyerName.trim()
        unread.add(key)
        addToCache(key, Message(now, "Client: $text"))
        persistMeta()
        return true
    }

    fun addReply(buyerName: String, reply: String) {
        addToCache(buyerName.trim().lowercase(), Message(clock(), "Nadir: ${reply.take(MAX_MESSAGE_LENGTH)}"))
    }

    private fun addToCache(key: String, entry: Message) {
        if (key.isBlank()) return
        val messages = cache.getOrPut(key) { mutableListOf() }
        messages.add(entry)
        while (messages.size > MAX_MESSAGES_PER_BUYER) messages.removeAt(0)
        saveConversations()
    }

    fun getContext(buyerName: String): String? {
        pruneExpired()
        val key = buyerName.trim().lowercase()
        val messages = cache[key] ?: return null
        return if (messages.isEmpty()) null else messages.joinToString("\n") { it.text }
    }

    /** The client who messaged most recently (by time, not by who was last viewed). */
    fun lastBuyerName(): String? = buyerKeysMostRecentFirst().firstOrNull()

    // ── Client list / aggregator UI support ─────────────────────────────────

    /** Buyer keys with at least one live message, most recently active first. */
    fun buyerKeysMostRecentFirst(): List<String> {
        pruneExpired()
        // Sort by real time: `cache` is access-ordered, so merely viewing a thread reorders it.
        return cache.keys.sortedByDescending { lastActivityTime(it) ?: 0L }
    }

    fun displayNameFor(key: String): String = displayNames[key] ?: key

    fun isUnread(key: String): Boolean = key in unread

    fun lastMessagePreview(key: String): String =
        cache[key]?.lastOrNull()?.text?.removePrefix("Client: ")?.removePrefix("Nadir: ") ?: ""

    fun lastActivityTime(key: String): Long? = cache[key]?.lastOrNull()?.time ?: drafts[key]?.time

    /** When this client's oldest remaining message auto-deletes. */
    fun nextExpiryTime(key: String): Long? = cache[key]?.firstOrNull()?.let { it.time + RETENTION_MS }

    /** Client messages only (no prefix) — used for lead detection. */
    fun clientMessages(key: String): List<String> =
        cache[key].orEmpty().filter { it.text.startsWith("Client: ") }.map { it.text.removePrefix("Client: ") }

    fun threadFor(key: String): String = cache[key]?.joinToString("\n") { it.text } ?: ""

    fun entriesFor(key: String): List<Message> = cache[key]?.toList() ?: emptyList()

    fun markRead(key: String) {
        if (unread.remove(key)) persistMeta()
    }

    /**
     * Wipes a client's thread after it's been copied out, so the box only ever holds
     * messages that haven't been copied yet — not the full history forever.
     */
    fun clearThread(key: String) {
        cache[key]?.clear()
        drafts.remove(key)
        saveConversations()
        saveDrafts()
    }

    // ── 24h auto-delete ──────────────────────────────────────────────────────

    /**
     * Drops every message, draft and duplicate-fingerprint older than [RETENTION_MS], and
     * forgets clients left with nothing. Runs on every read/write, so expired messages never
     * show up in the UI or get sent to the AI — no background job needed.
     */
    fun pruneExpired() {
        val cutoff = clock() - RETENTION_MS
        var changed = false

        cache.values.forEach { msgs -> if (msgs.removeAll { it.time <= cutoff }) changed = true }
        if (drafts.values.removeAll { it.time <= cutoff }) changed = true
        // A client with nothing left (all expired, or copied out) and no pending draft drops off the list.
        if (cache.entries.removeAll { (key, msgs) -> msgs.isEmpty() && key !in drafts }) changed = true
        if (seen.values.removeAll { it <= cutoff }) changed = true

        val live = cache.keys + drafts.keys
        if (unread.retainAll { it in live }) changed = true
        if (displayNames.keys.retainAll { it in live }) changed = true

        if (changed) {
            saveConversations()
            saveDrafts()
            persistMeta()
        }
    }

    // ── Away Mode draft (shown inline on the thread screen) ──────────────────

    fun setDraft(buyerName: String, draft: String) {
        val key = buyerName.trim().lowercase()
        if (key.isBlank()) return
        drafts[key] = Message(clock(), draft)
        saveDrafts()
    }

    fun getDraft(key: String): String? = drafts[key]?.text

    fun clearDraft(key: String) {
        if (drafts.remove(key) != null) saveDrafts()
    }

    private fun saveConversations() {
        ctx?.let { PersistenceHelper.saveConversations(it, cache) }
    }

    private fun saveDrafts() {
        ctx?.let { PersistenceHelper.saveDrafts(it, drafts) }
    }

    private fun persistMeta() {
        val c = ctx ?: return
        PersistenceHelper.saveDisplayNames(c, displayNames)
        PersistenceHelper.saveUnread(c, unread)
        PersistenceHelper.saveSeen(c, seen)
    }

    fun clear() {
        cache.clear()
        displayNames.clear()
        unread.clear()
        drafts.clear()
        // Keep `seen` so stacked Fiverr notifications don't refill the box right after a clear.
        ctx?.let {
            PersistenceHelper.saveConversations(it, emptyMap())
            PersistenceHelper.saveDisplayNames(it, emptyMap())
            PersistenceHelper.saveUnread(it, emptySet())
            PersistenceHelper.saveDrafts(it, emptyMap())
        }
    }

    /** Test-only: wipes in-memory state, including duplicate fingerprints. */
    internal fun resetForTest() {
        ctx = null
        clear()
        seen.clear()
    }
}
