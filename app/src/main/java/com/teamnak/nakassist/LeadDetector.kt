package com.teamnak.nakassist

/**
 * Flags clients showing buying intent (budget, pricing, deadlines, ready to order) so they
 * stand out in the client list. Pure keyword matching — instant, offline, no API cost.
 */
object LeadDetector {

    private val buyingSignals = listOf(
        Regex("""\$\s?\d"""),                                   // "$500", "$ 200"
        Regex("""\b\d+\s?(usd|dollars?)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bbudget\b""", RegexOption.IGNORE_CASE),
        Regex("""\bhow much\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(price|pricing|cost|quote|rate)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(custom )?offer\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(hire|place (an|the) order|order now|ready to (start|order|proceed))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(deadline|asap|urgent(ly)?|by (monday|tuesday|wednesday|thursday|friday|saturday|sunday|tomorrow))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(when can you start|can you start)\b""", RegexOption.IGNORE_CASE),
    )

    fun isHotLead(clientMessages: List<String>): Boolean =
        clientMessages.any { msg -> buyingSignals.any { it.containsMatchIn(msg) } }
}
