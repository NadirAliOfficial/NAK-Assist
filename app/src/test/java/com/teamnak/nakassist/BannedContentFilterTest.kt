package com.teamnak.nakassist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the banned-content safety filter.
 *
 * BANNED = off-platform contact/payment methods — Fiverr TOS violation.
 * CLEAN  = normal replies including dollar pricing and timelines (both allowed).
 */
class BannedContentFilterTest {

    // Mirrors bannedPatterns in AssistAccessibilityService (pure JVM — no Android runtime needed).
    private val bannedPatterns = listOf(
        // Off-platform contact
        Regex("""\bgmail\b""", RegexOption.IGNORE_CASE),
        Regex("""\byahoo\b""", RegexOption.IGNORE_CASE),
        Regex("""\bhotmail\b""", RegexOption.IGNORE_CASE),
        Regex("""\be-?mail\b""", RegexOption.IGNORE_CASE),
        Regex("""\bphone\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmobile\b""", RegexOption.IGNORE_CASE),
        Regex("""\bskype\b""", RegexOption.IGNORE_CASE),
        Regex("""\bzoom\b""", RegexOption.IGNORE_CASE),
        Regex("""\btelegram\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdiscord\b""", RegexOption.IGNORE_CASE),
        Regex("""\bwhatsapp\b""", RegexOption.IGNORE_CASE),
        Regex("""\bslack\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsignal\b""", RegexOption.IGNORE_CASE),
        Regex("""\binstagram\b""", RegexOption.IGNORE_CASE),
        Regex("""\btwitter\b""", RegexOption.IGNORE_CASE),
        Regex("""\bfacebook\b""", RegexOption.IGNORE_CASE),
        Regex("""\blinkedin\b""", RegexOption.IGNORE_CASE),
        // Off-platform payment methods
        Regex("""\bpayment\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpaypal\b""", RegexOption.IGNORE_CASE),
        Regex("""\binvoice\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcrypto\b""", RegexOption.IGNORE_CASE),
        Regex("""\bbitcoin\b""", RegexOption.IGNORE_CASE),
        Regex("""\bwallet\b""", RegexOption.IGNORE_CASE),
        // @ symbol (email addresses)
        Regex("""@"""),
    )

    private fun containsBannedContent(text: String): Boolean =
        bannedPatterns.any { it.containsMatchIn(text) }

    private fun assertBanned(reply: String) =
        assertTrue("Expected BANNED but passed: \"$reply\"", containsBannedContent(reply))

    private fun assertClean(reply: String) =
        assertFalse("Expected CLEAN but flagged: \"$reply\"", containsBannedContent(reply))

    // ════════════════════════════════════════════════════════════════════════
    // BANNED — off-platform contact and payment methods
    // ════════════════════════════════════════════════════════════════════════

    @Test fun `payment word flagged`() =
        assertBanned("I'll send you a payment link once we confirm the details.")

    @Test fun `case insensitive Payment flagged`() =
        assertBanned("Payment can be processed once you confirm the order.")

    @Test fun `telegram mention flagged`() =
        assertBanned("You can reach me on Telegram for faster responses.")

    @Test fun `whatsapp mention flagged`() =
        assertBanned("Let's continue on WhatsApp so we can share files easily.")

    @Test fun `gmail mention flagged`() =
        assertBanned("Send me the files at myaddress@gmail.com")

    @Test fun `at symbol flagged`() =
        assertBanned("My address is nadir@example.com")

    @Test fun `email word flagged`() =
        assertBanned("Drop me an email with the brief and I'll review it.")

    @Test fun `e-mail hyphenated flagged`() =
        assertBanned("Send it by e-mail and I'll check tonight.")

    @Test fun `phone mention flagged`() =
        assertBanned("Just drop your phone number and I'll call you.")

    @Test fun `discord mention flagged`() =
        assertBanned("Join our Discord server and I'll walk you through it.")

    @Test fun `paypal mention flagged`() =
        assertBanned("I normally accept PayPal for international clients.")

    @Test fun `invoice mention flagged`() =
        assertBanned("I'll send you an invoice once the project is done.")

    @Test fun `bitcoin mention flagged`() =
        assertBanned("We can settle via Bitcoin if you prefer.")

    @Test fun `wallet mention flagged`() =
        assertBanned("Send it to my crypto wallet and we're good to go.")

    @Test fun `instagram mention flagged`() =
        assertBanned("Check my portfolio on Instagram for more examples.")

    @Test fun `linkedin mention flagged`() =
        assertBanned("You can verify my experience on LinkedIn.")

    @Test fun `zoom mention flagged`() =
        assertBanned("Let's hop on a Zoom call to discuss the requirements.")

    @Test fun `skype mention flagged`() =
        assertBanned("Add me on Skype and we can screen-share.")

    @Test fun `mobile mention flagged`() =
        assertBanned("Send me your mobile number and I'll get back to you.")

    @Test fun `crypto mention flagged`() =
        assertBanned("Payment via crypto is fine with me.")

    @Test fun `case insensitive TELEGRAM flagged`() =
        assertBanned("Reach me on TELEGRAM anytime.")

    // ════════════════════════════════════════════════════════════════════════
    // CLEAN — normal replies, pricing in dollars, and timelines all allowed
    // ════════════════════════════════════════════════════════════════════════

    @Test fun `dollar amount with offer follow-up passes`() =
        assertClean("This would be \$150 for the full bot, delivered in 3 days — I'll send you a custom offer.")

    @Test fun `dollar amount gig quote passes`() =
        assertClean("Around \$80 for the EA setup, 2 revisions included.")

    @Test fun `timeline only passes`() =
        assertClean("Delivery in 5 days once I have the requirements confirmed.")

    @Test fun `dollar plus timeline passes`() =
        assertClean("\$200, done in 3 days with source code included.")

    @Test fun `custom offer redirect passes`() =
        assertClean("I'll send you a custom offer with everything included, just confirm you're ready.")

    @Test fun `clean greeting passes`() =
        assertClean("Sure, what tech stack are you using for the backend?")

    @Test fun `clean acknowledgement passes`() =
        assertClean("Sounds great!")

    @Test fun `clean portfolio link passes`() =
        assertClean("Here's a similar project I built: https://github.com/NadirAliOfficial/eurusd-scalper-ea")

    @Test fun `clean technical reply passes`() =
        assertClean("Built on MT5 with Python bridge — delivery in 3 days once requirements are confirmed.")

    @Test fun `clean question passes`() =
        assertClean("What timeframe and pairs are you targeting for the EA?")

    @Test fun `clean farewell passes`() =
        assertClean("Take care, feel free to reach back out anytime.")

    @Test fun `clean fiverr coordination passes`() =
        assertClean("We can coordinate everything here on Fiverr once you place the order.")

    @Test fun `clean url with website passes`() =
        assertClean("Check https://www.theteamnak.com for the full portfolio.")

    @Test fun `clean short reply passes`() =
        assertClean("On it!")

    @Test fun `word okay does not trigger pay pattern`() =
        assertClean("okay sounds good let me check the requirements")

    @Test fun `repay does not trigger pay pattern`() =
        assertClean("No need to repay anything, the revision is included.")

    @Test fun `bank transfer in context still passes`() =
        assertClean("Everything goes through Fiverr, no need to worry about anything else.")

    @Test fun `signal in trading context is flagged — known trade-off`() {
        // "signal" is banned because Fiverr flags Signal messenger.
        // TradingView "signal" is a false positive but safer to regenerate than risk a ban.
        assertBanned("I can build a TradingView signal forwarder for your strategy.")
    }
}
