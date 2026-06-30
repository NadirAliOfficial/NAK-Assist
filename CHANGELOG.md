# Changelog

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
