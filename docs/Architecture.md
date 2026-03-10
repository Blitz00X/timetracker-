# Architecture

This document captures the current structure of TimeTracker+, including manual timers and the newer automatic activity pipeline.

## High-Level View
```
JavaFX UI (main-view.fxml)
      │
      ▼
MainController ↔ CompactWindow
      │
      ├─ CategoryService ── CategoryDao ─┐
      │                                  │
      ├─ SessionService  ── SessionDao ──┼─ DatabaseManager (SQLite)
      │                                  │
      └─ ActivityReportingService        │
                    ▲                   │
ActivityTrackingService → ActivityEventDao
                    │
                    ▼
          ActivityAggregationJob
          ├─ ActivityAggregator (in-memory)
          └─ ActivitySessionDao (sessions + daily totals)
```

## Modules
- **UI (`controller`, `view`)**: `MainController` wires FXML controls to services; `CategoryListCell`/`SessionListCell` provide context menus; `CompactWindow` exposes minimal controls when the main window is minimized. The Today tab in the right pane hosts the manual timeline, day exports, and per-category summary.
- **Services (`service`)**: `CategoryService` and `SessionService` implement validation, limit enforcement, exports, and view-model mapping.
- **Persistence (`dao`, `db`)**: Shared `DatabaseManager` supplies SQLite connections. DAOs handle SQL and schema assumptions. `DatabaseInitializer` creates tables and applies additive migrations at startup.
- **Auto tracking (`tracking`)**: Captures foreground app/URL (`ActiveAppCollector`, platform-specific implementations) and idle state (`IdleDetectionService`), persists raw events (`ActivityEventDao`), aggregates to sessions/totals (`ActivityAggregationJob`, `ActivityAggregator`, `ActivitySessionDao`), and exposes reports (`ActivityReportingService`).
- **Utilities (`util`)**: Formatting helpers (`TimeUtils`) and deterministic category colors.

## Manual Timer Flow
1. `TimeTrackerApp` loads `main-view.fxml`, instantiates `MainController`, and sets minimum window sizes.
2. `MainController` loads categories, today’s sessions, and history defaults; controls are bound to enable/disable logic based on selection and limits.
3. When starting a session, the controller asks `SessionService` to start with the remaining allowed seconds (if limited). The timer UI updates and a manual start event is recorded in `ActivityEventDao` for aggregation consistency.
4. Stopping a session persists it via `SessionDao`, refreshes timeline/history, and records a manual stop event. Reset/cancel clears the active session without saving.
5. Editing/deleting sessions routes through `SessionService` and triggers UI refresh. History exports call `generateIcsForDateRange`/`generateCsvForDateRange`.

## Auto Tracking Pipeline
- **Capture**: `ActivityTrackingService` polls every `pollingInterval` (default 10s). On Linux, `LinuxActiveAppCollector` shells out to `xprop` to obtain window class/title; if `captureUrls` is true, it also asks a `BrowserUrlResolver` (Chromium devtools or window-title heuristic). Non-Linux falls back to `NoOpActiveAppCollector`.
- **Idle/lock events**: `IdleDetectionService` listens to global input (JNativeHook). It emits `IDLE_ON/OFF` events. Manual start/stop also emit events for aggregation alignment.
- **Aggregation**: `ActivityAggregationJob` runs every 5 minutes (and on-demand refresh). It fetches events between time bounds, builds contiguous `ActivitySession` segments (`ActivityAggregator`), summarizes to `ActivityDailyTotal`, and persists both.
- **Reporting/UI**: `MainController#refreshAutoUsage` triggers aggregation for a day and reads totals via `ActivityReportingService`, mapping them to `ActivityTotalViewModel` for the Auto Usage table and CSV export.

## Data Schema Summary
- `categories (id, name, daily_limit_minutes)` with cascaded delete into sessions/resets.
- `sessions (id, category_id, start_time, end_time, duration_minutes)` stored as ISO strings.
- `category_usage_resets (category_id, usage_date, offset_seconds, override_limit_seconds)` for per-day resets/overrides.
- Auto tables: `activity_events` (raw samples), `activity_sessions` (aggregated contiguous usage), `activity_daily_totals` (per-day per-app/domain/url totals).

## Concurrency and Threading
- Manual timer state is guarded by synchronization in `SessionService`.
- Background capture and idle detection run on dedicated daemon threads; aggregation runs on a scheduled executor. UI mutations occur on the JavaFX thread via controller methods (called directly after DAO reads).
- Auto capture failures are logged and ignored to keep the UI responsive.

## Configuration
- Environment variables: `TT_POLL_SECONDS`, `TT_IDLE_MINUTES`, `TT_CAPTURE_URLS`, `TT_REDACT_QUERY` (defaults: 10s, 5m, true, true).
- Browser URL capture expects a Chromium-based browser launched with `--remote-debugging-port=9222`; otherwise only window titles are used.

## Startup/Shutdown
- `DatabaseInitializer.initialize()` is called from `TimeTrackerApp#init` to create/upgrade tables.
- `start()` wires controllers and kicks off tracking + aggregation.
- `stop()` shuts down executors, runs a final aggregation for the current day, and disposes tracking/idle detectors.
