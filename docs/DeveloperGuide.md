# Developer Guide

## Prerequisites
- JDK 21+ on PATH.
- SQLite JDBC is bundled; no external DB install required.
- On Linux for auto tracking: `xprop` available; optional Chromium-based browser with `--remote-debugging-port=9222` for URL capture.

## Project Layout
- `src/main/java/com/timetracker/` — app code (controller, service, dao, db, tracking, util).
- `src/main/resources/com/timetracker/view/main-view.fxml` — JavaFX UI definition.
- `src/main/resources/com/timetracker/view/styles.css` — shared dark theme and component styles (Inter font bundled under `view/fonts/`).
- `src/test/java/com/timetracker/` — unit tests (currently `SessionServiceTest`).
- `timetracker.db` — SQLite DB created in project root at runtime.

## Setup & Run
```bash
# Launch UI (skips tests)
./run.sh
# or via Maven directly
./mvnw -q -DskipTests javafx:run

# Build (with tests)
./mvnw clean install

# Run tests only
./mvnw test
```

## Configuration
Environment variables read at startup (defaults in parentheses):
- `TT_POLL_SECONDS` (10): auto-tracking polling interval in seconds.
- `TT_IDLE_MINUTES` (5): idle threshold in minutes for idle detection.
- `TT_CAPTURE_URLS` (true): capture browser URLs when available.
- `TT_REDACT_QUERY` (true): strip query strings from captured URLs.

Browser URL capture: start Chrome/Brave with `--remote-debugging-port=9222` or adjust `ChromiumDebugUrlResolver` if you change the port. Without it, only window titles/domains are used.

## Database
- Schema is created/upgraded automatically on boot (`DatabaseInitializer`).
- Tables: `categories`, `sessions`, `category_usage_resets`, `activity_events`, `activity_sessions`, `activity_daily_totals`.
- Deleting `timetracker.db` resets all data (manual + auto). Foreign keys cascade session data when a category is removed.

## Development Tips
- Keep business logic in services; controllers should orchestrate and update UI state.
- Use `TimeUtils` and view models for presentation formatting.
- When adding SQL, prefer prepared statements in a DAO; keep schema changes additive and update `DatabaseInitializer`.
- Auto-tracking is Linux-first: guard new platform-specific code behind capability checks and fail silently when unavailable.
- The compact window (`CompactWindow`) mirrors the main controls; ensure state changes propagate through `MainController` methods.
- Styling is centralized in `styles.css`; keep FXML free of inline styles and reuse style classes (e.g., `card`, `section-title`, `muted-text`) for consistency.
- Auto Usage tab includes a toggle to enable/disable background tracking; when toggled off, the auto table and export controls are disabled and tracking is paused in `ActivityTrackingService`.

## Debugging
- Enable SLF4J output by adjusting `slf4j-simple` config (default logs to stderr).
- For auto tracking, verify `ActivityEvent` rows are being created, then run `aggregationJob.aggregate(...)` manually in a debugger to inspect sessions/totals.
- Idle detection can fail if native hooks are blocked; failures are logged and the app continues without idle signals.

## Contributing
- Follow `CodingStandards.md` for style, validations, and testing expectations.
- New features should include either unit tests or manual test notes in `TestPlan.md` until automated coverage exists.
