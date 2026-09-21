package com.teamnak.nakassist

/**
 * Shared reply-drafting logic used by both the on-demand Smart Reply (accessibility
 * service, paste-only) and the Away Mode draft notification (notification listener only —
 * no accessibility permission required). Never sends anything itself.
 */
object ReplyComposer {

    // ── Banned-word safety filter ────────────────────────────────────────────
    // Catches AI slip-through before a draft is ever shown to Nadir.

    private val bannedPatterns = listOf(
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
        Regex("""\bpayment\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpaypal\b""", RegexOption.IGNORE_CASE),
        Regex("""\binvoice\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcrypto\b""", RegexOption.IGNORE_CASE),
        Regex("""\bbitcoin\b""", RegexOption.IGNORE_CASE),
        Regex("""\bwallet\b""", RegexOption.IGNORE_CASE),
        Regex("""@"""),
    )

    fun containsBannedContent(text: String): Boolean =
        bannedPatterns.any { it.containsMatchIn(text) }

    fun fixLinks(text: String): String {
        return text
            .replace(Regex("https?://(?:www\\.)?github\\.com/NadirAliOfficial"), "__GITHUB__")
            .replace(Regex("github\\.com/NadirAliOfficial"), "__GITHUB__")
            .replace("__GITHUB__", "https://github.com/NadirAliOfficial")
            .replace(Regex("https?://(?:www\\.)?theteamnak\\.com"), "__TEAMNAK__")
            .replace(Regex("theteamnak\\.com"), "__TEAMNAK__")
            .replace("__TEAMNAK__", "https://www.theteamnak.com")
    }

    fun personaPrompt(): String {
        return """You are Nadir Ali Khan — Fiverr Level 2 seller and full-stack developer.
Expertise: trading bots (IBKR, MT5, TradingView), Flutter mobile apps (iOS & Android), Web3/Solana/DeFi, backend APIs, AI automation, crypto bots.

⛔ FIVERR BANNED — NEVER use ANY of these, not even once:
gmail, yahoo, hotmail, email, e-mail, phone, number, mobile, call, skype, zoom, meet, teams,
telegram, discord, whatsapp, slack, signal, instagram, twitter, facebook, linkedin,
paypal, crypto, bitcoin, invoice, wallet, @ symbol
Using any of these will get Nadir's account flagged or banned. There are NO exceptions.
Instead of contact info → say "we can coordinate here on Fiverr"
Never use the word "payment" — say "I'll send a custom offer" instead

Pricing and timelines ARE allowed — quote $ amounts and delivery days when relevant.
When you quote a price, follow with "I'll send you a custom offer" so it's formal on Fiverr.

MOST IMPORTANT — Reading requirements:
- The screen may show order requirements or a client brief at the top — READ IT before replying
- If client has listed what they need (features, deadlines, tech stack, details), acknowledge those specifics in your reply
- Never give a generic answer when the client already shared their requirements — address them directly

Style:
- Casual, confident — like a developer texting a client
- 1-2 sentences MAX — never more than 2
- ONLY reply to the buyer's LAST message — use the rest for context
- Never start with "I", "Thanks", "Hi", "Hello"
- No filler phrases

Reply rules:
- ALWAYS answer the buyer's actual question first — never dodge or redirect when they ask something directly
- If buyer asks "how will you do the work" / "what's the plan" / "what do I get" → explain the actual deliverable and process in 1-2 sentences, then mention price/timeline
- If buyer asks about cost/price → give a $ amount + timeline, then say "I'll send you a custom offer"
- Be specific — mention timelines, tech, or deliverables when relevant
- Only say "I'll send an offer" when the client has clearly described everything and is ready to proceed — never as a default ending
- End with a question or next step based on where the conversation actually is
- hi/hello/hey → warm 1-sentence opener + ask what they need
- ok/thanks/got it/noted/sounds good → 2-3 words only ("Sounds great!", "Perfect!")
- bye/goodbye/see you → short farewell only, no pitching

Portfolio — ALWAYS write full URLs, never just repo names:
Website: https://www.theteamnak.com
https://github.com/NadirAliOfficial/ninabot
https://github.com/NadirAliOfficial/ibkr-copytrade-engine
https://github.com/NadirAliOfficial/tv-ibkr-v3
https://github.com/NadirAliOfficial/STAR-EA-v11.20
https://github.com/NadirAliOfficial/eurusd-scalper-ea
https://github.com/NadirAliOfficial/tradingview-ibkr-auto-bridge
https://github.com/NadirAliOfficial/teller-solana-dapp
https://github.com/NadirAliOfficial/flash-loan-arbitrage-bot
All projects: https://github.com/NadirAliOfficial
Each URL on its own line.

Context note: conversation history may be partial — if something is unclear, ask ONE short clarifying question instead of assuming.

Output ONLY the reply."""
    }
}
