# Code Walkthrough

## Entry Point
- `TimeTrackerApp` (extends `Application`) initializes the database, loads `main-view.fxml`, and binds the `MainController`. It starts background services (tracking, idle detection) and schedules aggregation every 5 minutes; on exit it shuts them down and runs a final aggregation.

## UI Layer
- **FXML**: `src/main/resources/com/timetracker/view/main-view.fxml` defines three panels (categories, central timer, right-side tabs for Today/Auto Usage/History; Today tab hosts the breakdown + day exports + per-category summary) without inline styles.
- **Stylesheet**: `src/main/resources/com/timetracker/view/styles.css` applies the dark card-based theme, Inter font, and component look (buttons, pills, tables, lists).
- **MainController**: Wires all UI controls, orchestrates service calls, binds enable/disable states, and handles alerts/dialogs. Auto Usage tab now includes an enable/disable toggle that pauses background tracking and disables the table/export when off.
- **Custom cells**: `CategoryListCell` adds context menu actions (set limit, adjust remaining today, reset usage, delete) with color dots; `SessionListCell` supports edit/delete via context menu or Delete key.
- **CompactWindow**: Auxiliary always-on-top window that uses `MainController` APIs (`toggleSessionFromCompact`, `selectCategory`, `getActiveSession`) to mirror state when the main window is minimized.

## Manual Time Tracking
1. **Categories**: Controller delegates CRUD to `CategoryService`, which validates inputs and calls `CategoryDao` for persistence.
2. **Start session**: Controller checks remaining seconds (`SessionService#getRemainingSecondsForCategoryToday`). If allowed, it starts an `ActiveSession` (in-memory) and records a `MANUAL_START` event via `ActivityEventDao`.
3. **Stop/Reset**: `SessionService#stopSession` persists a `Session` via `SessionDao` (respecting allowedSeconds cutoff), emits `MANUAL_STOP`, and refreshes timeline + history. Reset cancels without saving.
4. **Editing**: Controller fetches a `SessionDto` by id, validates new timestamps, and calls `SessionService#updateSession`, which recalculates duration and updates via DAO.
5. **History/export**: Controller validates date pickers, loads sessions with `SessionService#getSessionsForDateRange`, builds summaries, and calls `generateIcsForDateRange` / `generateCsvForDateRange` when exporting.

## Auto Activity Tracking
1. **Capture**: `ActivityTrackingService` runs on a scheduled executor, calling an `ActiveAppCollector` (Linux: `LinuxActiveAppCollector`; others: `NoOpActiveAppCollector`). It optionally redacts URL query strings via `ActivityUrlUtils` and writes `FOCUS` events to `ActivityEventDao`.
2. **Idle**: `IdleDetectionService` (JNativeHook) posts `IDLE_ON/OFF` events to the same table.
3. **Aggregation**: `ActivityAggregationJob` (scheduled + on-demand) reads events in a time window, uses `ActivityAggregator` to build contiguous `ActivitySession` blocks and `ActivityDailyTotal` summaries, and persists both through `ActivitySessionDao`.
4. **Reporting**: `ActivityReportingService` reads daily totals; `MainController#refreshAutoUsage` maps them to `ActivityTotalViewModel` for the Auto Usage table and CSV export.

## Utilities & Models
- **Formatting**: `TimeUtils` handles HH:mm/HH:mm:ss formatting and duration strings.
- **View models**: `SessionViewModel` and `CategorySummaryViewModel` hold UI-friendly values; `ActivityTotalViewModel` is used by the Auto Usage table.
- **Data access support**: `DatabaseManager` centralizes SQLite connections; `DatabaseInitializer` ensures tables and additive columns exist before the UI loads.

## Tests
- `SessionServiceTest` exercises limit calculation, update logic, and stop behavior with a Mockito-backed `SessionDao`. Extend this pattern for new service logic.
