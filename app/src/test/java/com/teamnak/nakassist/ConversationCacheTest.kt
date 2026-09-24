package com.teamnak.nakassist

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 24h auto-delete + duplicate-notification handling. Pure JVM — no Context, so nothing persists. */
class ConversationCacheTest {

    private var now = 1_700_000_000_000L
    private val hour = 60L * 60 * 1000

    @Before fun setUp() {
        ConversationCache.resetForTest()
        ConversationCache.clock = { now }
    }

    @After fun tearDown() {
        ConversationCache.resetForTest()
        ConversationCache.clock = { System.currentTimeMillis() }
    }

    @Test fun `message is visible before 24h`() {
        ConversationCache.addMessage("John", "Need a trading bot")
        now += 23 * hour
        assertEquals(listOf("john"), ConversationCache.buyerKeysMostRecentFirst())
        assertEquals("Client: Need a trading bot", ConversationCache.getContext("John"))
    }

    @Test fun `message disappears after 24h`() {
        ConversationCache.addMessage("John", "Need a trading bot")
        now += 24 * hour + 1
        assertTrue(ConversationCache.buyerKeysMostRecentFirst().isEmpty())
        assertNull(ConversationCache.getContext("John"))
        assertFalse(ConversationCache.isUnread("john"))
    }

    @Test fun `only old messages expire, newer ones stay`() {
        ConversationCache.addMessage("John", "first")
        now += 20 * hour
        ConversationCache.addMessage("John", "second")
        now += 5 * hour
        assertEquals("Client: second", ConversationCache.getContext("John"))
    }

    @Test fun `draft expires after 24h`() {
        ConversationCache.addMessage("John", "hi")
        ConversationCache.setDraft("John", "Hey, what do you need built?")
        now += 25 * hour
        ConversationCache.pruneExpired()
        assertNull(ConversationCache.getDraft("john"))
    }

    @Test fun `reposted notification is ignored`() {
        assertTrue(ConversationCache.addMessage("John", "Need a trading bot"))
        assertFalse(ConversationCache.addMessage("John", "Need a trading bot"))
        assertFalse(ConversationCache.addMessage("john ", "  need a trading bot "))
        assertEquals("Client: Need a trading bot", ConversationCache.getContext("John"))
    }

    @Test fun `copied-out message does not come back from a stacked notification`() {
        ConversationCache.addMessage("John", "line one")
        ConversationCache.clearThread("john")
        assertFalse(ConversationCache.addMessage("John", "line one"))
        assertTrue(ConversationCache.addMessage("John", "line two"))
        assertEquals("Client: line two", ConversationCache.getContext("John"))
    }

    @Test fun `same text is accepted again after 24h`() {
        ConversationCache.addMessage("John", "hello")
        now += 25 * hour
        assertTrue(ConversationCache.addMessage("John", "hello"))
    }

    @Test fun `clients ordered by latest message, not by last viewed`() {
        ConversationCache.addMessage("Alice", "a")
        now += 1000
        ConversationCache.addMessage("Bob", "b")
        ConversationCache.threadFor("alice") // viewing must not reorder
        assertEquals(listOf("bob", "alice"), ConversationCache.buyerKeysMostRecentFirst())
        assertEquals("bob", ConversationCache.lastBuyerName())
    }
}
