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

    fun init(context: Context) {
        ctx = context.applicationContext
        val saved = PersistenceHelper.loadConversations(context)
        cache.clear()
        cache.putAll(saved)
    }

    fun addMessage(buyerName: String, message: String) {
        addToCache(buyerName, "Client: ${message.take(400)}")
    }

    fun addReply(buyerName: String, reply: String) {
        addToCache(buyerName, "Nadir: ${reply.take(400)}")
    }

    private fun addToCache(buyerName: String, entry: String) {
        val key = buyerName.trim().lowercase()
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

    fun clear() {
        cache.clear()
        ctx?.let { PersistenceHelper.saveConversations(it, emptyMap()) }
    }
}
