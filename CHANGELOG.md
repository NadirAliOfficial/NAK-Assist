# Changelog

## [Unreleased] — 24h auto-delete & smarter inbox

### Added
- Client messages and Away Mode drafts auto-delete 24h after they arrive
- Message timestamps in the thread view; "2h ago · auto-deletes in 22h" on the client list
- 🔥 LEAD badge on clients showing buying intent (budget, price, deadline, ready to order)

### Fixed
- Fiverr re-posted/stacked notifications no longer duplicate messages, re-bump unread, or burn extra AI drafts
- Copied-out messages no longer reappear from a stacked notification
- Away Mode cooldown is now per client, so two clients writing within seconds both get a draft
- Client list order (and Smart Reply's "last client") now follows the latest message, not the last thread viewed

## [Unreleased] — feature/scheduled-away-mode

### Added
- ScheduledAwayModeManager class for time-based Away Mode activation
- TimeRangePicker UI component for selecting active hours
- Day-of-week toggle grid in settings screen
- Timezone-aware scheduling with automatic DST handling
- Visual badge on main screen indicating scheduled Away Mode status
- Local notification when Away Mode auto-activates or deactivates

### Changed
- Refactored AwayModeService to support both manual and scheduled triggers
- Updated SharedPreferences schema to store schedule configuration

### Fixed
- Away Mode not persisting across app restarts
