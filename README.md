# NAK Assist

> AI-powered Fiverr assistant for Android — by **Nadir Ali Khan** | [theteamnak.com](https://www.theteamnak.com)

## What it does

NAK Assist runs as a floating overlay button on Android. It reads the screen using the Accessibility API and uses AI to generate context-aware replies for Fiverr conversations — fully automated or on demand.

## Features

### Smart Reply (single tap)
Reads the current Fiverr chat screen, pulls in conversation history, and generates a short professional reply. If there is already text in the input field it polishes it instead.

### Auto Polish
If the input field has text when you tap, it fixes grammar and spelling and improves clarity — then shows the result for review before sending.

### Away Mode
Monitors Fiverr notifications. When a message arrives, it automatically opens the conversation, generates a reply, injects it, and sends — then navigates back. Fully hands-free.

### Stay Online
Periodically sends a micro-gesture to keep Fiverr online status active. Configurable intervals: 5s / 10s / 30s / 60s. Shown as a live countdown on the floating button.

### Conversation Memory
Caches up to 30 messages per buyer across 20 buyers, persisted to disk. Every smart reply includes full conversation history so context is never lost across sessions.

### Banned Word Safety Filter
Secondary defence on top of the AI prompt. Any generated reply containing Fiverr-banned words (off-platform contacts, payment methods, @ symbol) is automatically regenerated up to 2 times before being discarded. Dollar amounts and timelines are allowed.

### Context Detection
Automatically switches between Fiverr chat mode, Fiverr order page mode, and general mode based on the active screen — no manual switching needed.

## Controls

| Action | Result |
|--------|--------|
| Single tap | Smart reply (or polish if input has text) |
| Long press 500ms | Open mode selector menu |
| Mode menu | Toggle Away Mode, Stay Online, set interval |

## Tech Stack

- **Language:** Kotlin (Native Android)
- **AI:** Llama 3.3 70B via Groq API
- **Min SDK:** Android 8.0 (API 26)
- **Permissions:** Accessibility Service, Draw Over Other Apps, Notifications

## Setup

1. Clone and open in Android Studio
2. Add your Groq API key to `GroqApiHelper.kt`
3. Build and install: `./gradlew installDebug`
4. Grant overlay permission
5. Enable NAK Assist in Android Accessibility Settings
6. Floating button appears on all screens

## Running Tests

```bash
./gradlew testDebugUnitTest
```

Includes unit tests for the banned-word safety filter covering 39 cases.

---

Built by **Nadir Ali Khan** — [theteamnak.com](https://www.theteamnak.com) | [GitHub](https://github.com/NadirAliOfficial)
