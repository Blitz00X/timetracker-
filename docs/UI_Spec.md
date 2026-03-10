# UI Specification

## Layout (main-view.fxml)
- **Left panel: Categories**
  - ListView of categories with colored dots and context menu (set daily limit, adjust today’s remaining, reset today, delete).
  - Buttons: `Add Category`, `Edit`, `Delete Category` plus helper label.
- **Center panel: Timer**
  - Timer block shows HH:MM:SS, selected category, remaining today, start time, and status.
  - Buttons: `Start/Stop`, `Reset` (cancel active), mode ComboBox (placeholder values: Normal/Pomodoro/Locked Mode).
- **Right panel: TabPane (pill style)**
  - **Today tab**: Today’s Breakdown ListView (chronological sessions with color dots, edit/delete via context menu or Delete key), export buttons for the day (ICS/CSV), and Today’s Summary table (Category, Minutes) for per-category totals.
  - **Auto Usage tab**: date picker, Enable/Disable Auto Tracking toggle (pauses background capture and disables the table/export while off), Refresh, Export (.csv) buttons; TableView with columns App/Site, Domain/Title (URL), Duration (HH:mm:ss); disabled when empty or tracking is off.
  - **History tab**: date range pickers (From/To), Refresh; error label for invalid ranges; ListView of sessions in range; TableView of category summary (Category, Minutes); export buttons for ICS/CSV enabled when data exists.

## Interactions
- Selecting a category enables Start when limits permit; controls disable while a session is running.
- Start: begins timer against selected category; Stop saves the session; Reset cancels without saving.
- Category context actions call dialogs for limits and confirmations for deletions/resets.
- Session context menu permits edit/delete; Delete key removes the selected session in timeline/history.
- Date pickers default to last 7 days; validation prevents end < start.
- Auto usage Refresh triggers aggregation for the selected date; Pause toggles capture in `ActivityTrackingService`.
- Export actions prompt for file destination via OS file chooser.

## Compact Window
- Always-on-top utility window showing status, category ComboBox (mirrors main list), Start/Stop button, timer label, and “Open Full View” button.
- Appears when the main window is minimized; hides when restored; start/stop actions call back into `MainController` so both windows stay in sync.

## Styling
- The UI now uses a dark, card-based theme defined in `src/main/resources/com/timetracker/view/styles.css`; FXML no longer contains inline styles.
- Palette: bg `#1E1E1E`, card `#2A2A2A`, border `#3A3A3A`, text `#EEE`, muted text `#B5B5B5`, accent `#3B82F6`, radius `8px`.
- Font: Inter (bundled) applied app-wide via CSS, with bold weights for timer/section headers and muted tone for secondary labels.
- Cards use rounded corners, soft shadows, and consistent 24px padding; TabPane uses pill headers; buttons have accent fills with hover/pressed states.
- Category colors still derive from `CategoryColorUtil` for dots in lists/table cells.
