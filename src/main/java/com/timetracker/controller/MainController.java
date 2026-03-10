package com.timetracker.controller;

import com.timetracker.model.Category;
import com.timetracker.model.CategorySummaryViewModel;
import com.timetracker.model.Session;
import com.timetracker.model.SessionDto;
import com.timetracker.model.SessionViewModel;
import com.timetracker.service.CategoryService;
import com.timetracker.service.SessionService;
import com.timetracker.tracking.ActivityAggregationJob;
import com.timetracker.tracking.ActivityDailyTotal;
import com.timetracker.tracking.ActivityEvent;
import com.timetracker.tracking.ActivityEventDao;
import com.timetracker.tracking.ActivityEventType;
import com.timetracker.tracking.ActivityReportingService;
import com.timetracker.tracking.ActivityTrackingConfig;
import com.timetracker.tracking.ActivityTrackingService;
import com.timetracker.tracking.ActivityTotalViewModel;
import com.timetracker.util.TimeUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.beans.binding.Bindings;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.control.Tooltip;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.scene.control.ToggleButton;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

public class MainController {

    private static final DateTimeFormatter TIME_INPUT_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private ListView<Category> categoryListView;

    @FXML
    private ListView<SessionViewModel> timelineListView;

    @FXML
    private ListView<SessionViewModel> historyListView;

    @FXML
    private DatePicker rangeStartDatePicker;

    @FXML
    private DatePicker rangeEndDatePicker;

    @FXML
    private TableView<CategorySummaryViewModel> summaryTable;

    @FXML
    private TableColumn<CategorySummaryViewModel, String> summaryCategoryColumn;

    @FXML
    private TableColumn<CategorySummaryViewModel, Number> summaryDurationColumn;

    @FXML
    private TableView<CategorySummaryViewModel> todaySummaryTable;

    @FXML
    private TableColumn<CategorySummaryViewModel, String> todaySummaryCategoryColumn;

    @FXML
    private TableColumn<CategorySummaryViewModel, Number> todaySummaryDurationColumn;

    @FXML
    private TableView<ActivityTotalViewModel> autoTotalsTable;

    @FXML
    private TableColumn<ActivityTotalViewModel, String> autoAppColumn;

    @FXML
    private TableColumn<ActivityTotalViewModel, String> autoUrlColumn;

    @FXML
    private TableColumn<ActivityTotalViewModel, String> autoDurationColumn;

    @FXML
    private ToggleButton trackingPauseToggle;

    @FXML
    private Button autoExportButton;

    @FXML
    private DatePicker autoDatePicker;

    @FXML
    private Label rangeErrorLabel;

    @FXML
    private Button exportRangeIcsButton;

    @FXML
    private Button exportRangeCsvButton;

    @FXML
    private Label timerLabel;

    @FXML
    private Label selectedCategoryLabel;

    @FXML
    private Label remainingTimeLabel;

    @FXML
    private Label startTimeLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Button startStopButton;

    @FXML
    private Button resetButton;

    @FXML
    private Button addCategoryButton;

    private final CategoryService categoryService = new CategoryService();
    private final SessionService sessionService = new SessionService();
    private final ActivityEventDao activityEventDao = new ActivityEventDao();
    private ActivityAggregationJob aggregationJob;
    private ActivityReportingService reportingService;
    private ActivityTrackingService trackingService;
    private ActivityTrackingConfig trackingConfig;

    private final ObservableList<Category> categoryItems = FXCollections.observableArrayList();
    private final ObservableList<SessionViewModel> timelineItems = FXCollections.observableArrayList();
    private final ObservableList<SessionViewModel> historyItems = FXCollections.observableArrayList();
    private final ObservableList<CategorySummaryViewModel> summaryItems = FXCollections.observableArrayList();
    private final ObservableList<CategorySummaryViewModel> todaySummaryItems = FXCollections.observableArrayList();
    private final ObservableList<ActivityTotalViewModel> autoTotals = FXCollections.observableArrayList();

    private Timeline tickingTimeline;

    @FXML
    private void initialize() {
        categoryListView.setItems(categoryItems);
        categoryListView.setPlaceholder(new Label("No categories yet"));
        categoryListView.setCellFactory(listView -> new CategoryListCell(this));
        categoryListView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            updateSelectedCategoryLabel(newValue);
            updateControlAvailability();
        });

        timelineListView.setItems(timelineItems);
        timelineListView.setPlaceholder(new Label("No sessions recorded today"));
        configureSessionList(timelineListView);

        historyListView.setItems(historyItems);
        historyListView.setPlaceholder(new Label("No sessions in range"));
        configureSessionList(historyListView);

        summaryTable.setItems(summaryItems);
        summaryTable.setPlaceholder(new Label("No activity for range"));
        summaryCategoryColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().categoryName()));
        summaryDurationColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().totalMinutes()));

        if (todaySummaryTable != null) {
            todaySummaryTable.setItems(todaySummaryItems);
            todaySummaryTable.setPlaceholder(new Label("No activity yet today"));
            if (todaySummaryCategoryColumn != null) {
                todaySummaryCategoryColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().categoryName()));
            }
            if (todaySummaryDurationColumn != null) {
                todaySummaryDurationColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().totalMinutes()));
            }
        }

        if (autoTotalsTable != null) {
            autoTotalsTable.setItems(autoTotals);
            autoTotalsTable.setPlaceholder(new Label("No auto activity"));
            if (autoDatePicker != null) {
                autoDatePicker.setValue(LocalDate.now());
                autoDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> refreshAutoUsage());
            }
            if (autoAppColumn != null) {
                autoAppColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().appOrSite()));
            }
            if (autoUrlColumn != null) {
                autoUrlColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                        data.getValue().url() == null ? "" : data.getValue().url()));
            }
            if (autoDurationColumn != null) {
                autoDurationColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().formattedDuration()));
            }
        }

        LocalDate today = LocalDate.now();
        rangeErrorLabel.setText("");
        rangeStartDatePicker.setValue(today.minusDays(6));
        rangeEndDatePicker.setValue(today);
        rangeStartDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> refreshHistoryRange());
        rangeEndDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> refreshHistoryRange());
        exportRangeIcsButton.setDisable(true);
        exportRangeCsvButton.setDisable(true);

        startStopButton.setText("Start");
        startStopButton.setDisable(true);
        resetButton.setDisable(true);
        timerLabel.setText("00:00:00");
        selectedCategoryLabel.setText("Selected Category: -");
        remainingTimeLabel.setText("Remaining Today: -");
        startTimeLabel.setText("Start Time: -");
        statusLabel.setText("Status: Idle");

        loadCategories();
        refreshTimeline();
        refreshHistoryRange();
        refreshTodaySummary();
        refreshAutoUsage();
        updateTrackingToggle();
        updateAutoControls();
    }

    @FXML
    private void onAddCategory() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Category");
        dialog.setHeaderText("Create a new category");
        dialog.setContentText("Name:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                showError("Invalid category name", "Category name cannot be empty.");
            } else {
                LimitDialogResult limitResult = promptForDailyLimit(null);
                if (limitResult.cancelled()) {
                    return;
                }
                createCategory(trimmed, limitResult.limitMinutes());
            }
        });
    }

    @FXML
    private void onEditCategory() {
        Category selected = categoryListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("No category selected", "Please select a category to edit.");
            return;
        }
        promptSetCategoryLimit(selected);
    }

    @FXML
    private void onDeleteCategory() {
        Category selected = categoryListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("No category selected", "Please select a category to delete.");
            return;
        }
        promptDeleteCategory(selected);
    }

    @FXML
    private void onExportTodayIcs() {
        if (timelineItems.isEmpty()) {
            showInfo("Nothing to export", "No sessions recorded today.");
            return;
        }
        LocalDate today = LocalDate.now();
        exportIcsForRange(today, today, "TimeTracker-" + today + ".ics");
    }

    @FXML
    private void onExportTodayCsv() {
        if (timelineItems.isEmpty()) {
            showInfo("Nothing to export", "No sessions recorded today.");
            return;
        }
        LocalDate today = LocalDate.now();
        exportCsvForRange(today, today, "TimeTracker-" + today + ".csv");
    }

    @FXML
    private void onExportRangeIcs() {
        Optional<DateRange> range = resolveSelectedRange();
        if (range.isEmpty()) {
            return;
        }
        if (historyItems.isEmpty()) {
            showInfo("Nothing to export", "No sessions found for the selected range.");
            return;
        }
        DateRange value = range.get();
        exportIcsForRange(value.start(), value.end(),
                "TimeTracker-" + value.start() + "_" + value.end() + ".ics");
    }

    @FXML
    private void onExportRangeCsv() {
        Optional<DateRange> range = resolveSelectedRange();
        if (range.isEmpty()) {
            return;
        }
        if (historyItems.isEmpty()) {
            showInfo("Nothing to export", "No sessions found for the selected range.");
            return;
        }
        DateRange value = range.get();
        exportCsvForRange(value.start(), value.end(),
                "TimeTracker-" + value.start() + "_" + value.end() + ".csv");
    }

    @FXML
    private void onRefreshRange() {
        refreshHistoryRange();
    }

    @FXML
    private void onStartStop() {
        if (sessionService.isSessionRunning()) {
            handleStop();
        } else {
            handleStart();
        }
    }

    @FXML
    private void onReset() {
        stopTimer();
        sessionService.cancelActiveSession();
        timerLabel.setText("00:00:00");
        startTimeLabel.setText("Start Time: -");
        statusLabel.setText("Status: Idle");
        startStopButton.setText("Start");
        resetButton.setDisable(true);
        updateControlAvailability();
        updateSelectedCategoryLabel(categoryListView.getSelectionModel().getSelectedItem());
    }

    private void handleStart() {
        Category selected = categoryListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("No category selected", "Please select a category before starting the timer.");
            return;
        }
        try {
            OptionalLong remaining = sessionService.getRemainingSecondsForCategoryToday(selected);
            Long allowedSeconds = null;
            if (remaining.isPresent()) {
                long remainingSeconds = remaining.getAsLong();
                if (remainingSeconds <= 0) {
                    showError("Daily limit reached", "No remaining time left today for " + selected.getName() + ".");
                    return;
                }
                allowedSeconds = remainingSeconds;
            }
            sessionService.startSession(selected, allowedSeconds);
            emitManualEvent(ActivityEventType.MANUAL_START, selected);
            updateSelectedCategoryLabel(selected);
            sessionService.getActiveSession()
                    .ifPresent(active -> startTimeLabel.setText("Start Time: " + TimeUtils.formatHHmm(active.startTime())));
            statusLabel.setText("Status: Running");
            startStopButton.setText("Stop");
            resetButton.setDisable(false);
            startTimer();
            updateControlAvailability();
        } catch (IllegalStateException | IllegalArgumentException e) {
            showError("Cannot start session", e.getMessage());
        }
    }

    private void handleStop() {
        stopTimer();
        sessionService.stopSession();
        emitManualEvent(ActivityEventType.MANUAL_STOP, categoryListView.getSelectionModel().getSelectedItem());
        timerLabel.setText("00:00:00");
        startTimeLabel.setText("Start Time: -");
        statusLabel.setText("Status: Idle");
        startStopButton.setText("Start");
        resetButton.setDisable(true);
        updateControlAvailability();
        refreshTimeline();
        refreshHistoryRange();
        refreshAutoUsage();
        updateSelectedCategoryLabel(categoryListView.getSelectionModel().getSelectedItem());
    }

    private void loadCategories() {
        Category previouslySelected = categoryListView.getSelectionModel().getSelectedItem();
        Integer selectedId = previouslySelected != null ? previouslySelected.getId() : null;
        categoryItems.setAll(categoryService.getAllCategories());
        if (!categoryItems.isEmpty()) {
            if (selectedId != null) {
                selectCategoryById(selectedId);
            } else if (categoryListView.getSelectionModel().getSelectedItem() == null) {
                categoryListView.getSelectionModel().selectFirst();
            }
        }
        updateControlAvailability();
        updateSelectedCategoryLabel(categoryListView.getSelectionModel().getSelectedItem());
    }

    private void refreshTimeline() {
        timelineItems.setAll(sessionService.getTodaySessions());
        updateSelectedCategoryLabel(categoryListView.getSelectionModel().getSelectedItem());
        updateControlAvailability();
        refreshTodaySummary();
    }

    private void refreshTodaySummary() {
        if (todaySummaryTable == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        todaySummaryItems.setAll(sessionService.getCategorySummaryForDateRange(today, today).entrySet().stream()
                .map(entry -> new CategorySummaryViewModel(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()));
    }

    private void refreshHistoryRange() {
        if (rangeStartDatePicker == null || rangeEndDatePicker == null) {
            return;
        }
        LocalDate start = rangeStartDatePicker.getValue();
        LocalDate end = rangeEndDatePicker.getValue();
        if (start == null || end == null) {
            rangeErrorLabel.setText("Select both start and end dates.");
            historyItems.clear();
            summaryItems.clear();
            exportRangeIcsButton.setDisable(true);
            exportRangeCsvButton.setDisable(true);
            return;
        }
        if (end.isBefore(start)) {
            rangeErrorLabel.setText("End date must be on or after start date.");
            historyItems.clear();
            summaryItems.clear();
            exportRangeIcsButton.setDisable(true);
            exportRangeCsvButton.setDisable(true);
            return;
        }
        rangeErrorLabel.setText("");
        historyItems.setAll(sessionService.getSessionsForDateRange(start, end));
        summaryItems.setAll(sessionService.getCategorySummaryForDateRange(start, end).entrySet().stream()
                .map(entry -> new CategorySummaryViewModel(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()));
        boolean hasData = !historyItems.isEmpty();
        exportRangeIcsButton.setDisable(!hasData);
        exportRangeCsvButton.setDisable(!hasData);
    }

    void selectCategory(Category category) {
        if (category == null) {
            categoryListView.getSelectionModel().clearSelection();
        } else {
            categoryListView.getSelectionModel().select(category);
        }
    }

    private boolean updateRemainingTimeLabel(Category category) {
        if (category == null) {
            remainingTimeLabel.setText("Remaining Today: -");
            return false;
        }
        OptionalLong remaining = sessionService.getRemainingSecondsForCategoryToday(category);
        if (remaining.isEmpty()) {
            remainingTimeLabel.setText("Remaining Today: Unlimited");
            return false;
        }
        long seconds = remaining.getAsLong();
        remainingTimeLabel.setText("Remaining Today: " + TimeUtils.formatHHmmss(seconds));
        return seconds <= 0;
    }

    private void selectCategoryById(int categoryId) {
        for (int i = 0; i < categoryItems.size(); i++) {
            if (categoryItems.get(i).getId() == categoryId) {
                categoryListView.getSelectionModel().select(i);
                return;
            }
        }
        categoryListView.getSelectionModel().clearSelection();
    }

    private void createCategory(String name, Integer dailyLimitMinutes) {
        try {
            Category category = categoryService.createCategory(name, dailyLimitMinutes);
            categoryItems.add(category);
            selectCategoryById(category.getId());
            updateSelectedCategoryLabel(categoryListView.getSelectionModel().getSelectedItem());
            updateControlAvailability();
        } catch (IllegalArgumentException | IllegalStateException e) {
            showError("Unable to create category", e.getMessage());
        }
    }

    String formatCategoryDisplay(Category category) {
        if (category.getDailyLimitMinutes() == null) {
            return category.getName();
        }
        return category.getName() + " (" + category.getDailyLimitMinutes() + " dk/day)";
    }

    void promptSetCategoryLimit(Category category) {
        LimitDialogResult result = promptForDailyLimit(category.getDailyLimitMinutes());
        if (result.cancelled()) {
            return;
        }
        try {
            Category updated = categoryService.updateCategoryLimit(category.getId(), result.limitMinutes());
            replaceCategoryInList(updated);
            updateSelectedCategoryLabel(categoryListView.getSelectionModel().getSelectedItem());
            updateControlAvailability();
        } catch (IllegalArgumentException | IllegalStateException e) {
            showError("Unable to update category", e.getMessage());
        }
    }

    private void replaceCategoryInList(Category updatedCategory) {
        for (int i = 0; i < categoryItems.size(); i++) {
            if (categoryItems.get(i).getId() == updatedCategory.getId()) {
                categoryItems.set(i, updatedCategory);
                categoryListView.getSelectionModel().select(i);
                return;
            }
        }
        categoryItems.add(updatedCategory);
        categoryListView.getSelectionModel().select(updatedCategory);
    }

    private LimitDialogResult promptForDailyLimit(Integer existingLimit) {
        while (true) {
            TextInputDialog limitDialog = new TextInputDialog(existingLimit == null ? "" : existingLimit.toString());
            limitDialog.setTitle("Daily Limit");
            limitDialog.setHeaderText("Set a daily limit in minutes (leave blank for unlimited).");
            limitDialog.setContentText("Minutes:");
            Optional<String> response = limitDialog.showAndWait();
            if (response.isEmpty()) {
                return LimitDialogResult.cancelledResult();
            }
            String trimmed = response.get().trim();
            if (trimmed.isEmpty()) {
                return LimitDialogResult.unlimited();
            }
            try {
                int minutes = Integer.parseInt(trimmed);
                if (minutes <= 0) {
                    throw new NumberFormatException();
                }
                return LimitDialogResult.ofMinutes(minutes);
            } catch (NumberFormatException e) {
                showError("Invalid limit", "Please enter a positive number of minutes or leave blank for unlimited.");
            }
        }
    }

    private void handleSessionExpiredByLimit() {
        stopTimer();
        sessionService.stopSession();
        timerLabel.setText("00:00:00");
        startTimeLabel.setText("Start Time: -");
        statusLabel.setText("Status: Limit reached");
        startStopButton.setText("Start");
        resetButton.setDisable(true);
        refreshTimeline();
    }

    private void emitManualEvent(ActivityEventType type, Category category) {
        try {
            String appId = category != null ? category.getName() : null;
            String payload = category != null ? "{\"categoryId\":" + category.getId() + "}" : null;
            activityEventDao.insert(new ActivityEvent(Instant.now(), type, appId, null, null, payload));
        } catch (Exception ignored) {
            // best-effort; manual tracking should not break UI
        }
    }

    private record LimitDialogResult(boolean cancelled, Integer limitMinutes) {
        static LimitDialogResult cancelledResult() {
            return new LimitDialogResult(true, null);
        }

        static LimitDialogResult unlimited() {
            return new LimitDialogResult(false, null);
        }

        static LimitDialogResult ofMinutes(int minutes) {
            return new LimitDialogResult(false, minutes);
        }
    }

    private void updateSelectedCategoryLabel(Category category) {
        if (category == null) {
            selectedCategoryLabel.setText("Selected Category: -");
            remainingTimeLabel.setText("Remaining Today: -");
            if (!sessionService.isSessionRunning()) {
                statusLabel.setText("Status: Idle");
            }
        } else {
            StringBuilder builder = new StringBuilder("Selected Category: ").append(category.getName());
            if (category.getDailyLimitMinutes() != null) {
                builder.append(" (").append(category.getDailyLimitMinutes()).append(" dk/day limit)");
            }
            selectedCategoryLabel.setText(builder.toString());
            boolean limitReached = updateRemainingTimeLabel(category);
            if (!sessionService.isSessionRunning()) {
                statusLabel.setText(limitReached ? "Status: Limit reached" : "Status: Idle");
            }
        }
    }

    private void updateControlAvailability() {
        Category selected = categoryListView.getSelectionModel().getSelectedItem();
        boolean running = sessionService.isSessionRunning();
        boolean hasSelection = selected != null;
        boolean limitReached = false;
        if (hasSelection && !running) {
            OptionalLong remaining = sessionService.getRemainingSecondsForCategoryToday(selected);
            limitReached = remaining.isPresent() && remaining.getAsLong() <= 0;
        }
        startStopButton.setDisable(!running && (!hasSelection || limitReached));
        addCategoryButton.setDisable(running);
        categoryListView.setDisable(running);
        if (!running) {
            startStopButton.setText("Start");
        }
    }

    private void startTimer() {
        if (tickingTimeline != null) {
            tickingTimeline.stop();
        }
        timerLabel.setText(sessionService.getActiveSession()
                .map(active -> {
                    Long allowedSeconds = active.allowedSeconds();
                    if (allowedSeconds != null) {
                        return TimeUtils.formatHHmmss(allowedSeconds);
                    }
                    return "00:00:00";
                })
                .orElse("00:00:00"));
        tickingTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> updateTimerDisplay()));
        tickingTimeline.setCycleCount(Timeline.INDEFINITE);
        tickingTimeline.playFromStart();
    }

    private void stopTimer() {
        if (tickingTimeline != null) {
            tickingTimeline.stop();
            tickingTimeline = null;
        }
    }

    private void updateTimerDisplay() {
        sessionService.getActiveSession().ifPresent(active -> {
            long elapsedSeconds = Math.max(0, Duration.between(active.startTime(), LocalDateTime.now()).getSeconds());
            Long allowedSeconds = active.allowedSeconds();
            if (allowedSeconds != null) {
                long remaining = Math.max(0, allowedSeconds - elapsedSeconds);
                if (remaining <= 0) {
                    handleSessionExpiredByLimit();
                    return;
                }
                timerLabel.setText(TimeUtils.formatHHmmss(remaining));
            } else {
                timerLabel.setText(TimeUtils.formatHHmmss(elapsedSeconds));
            }
        });
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    void promptResetCategoryUsage(Category category) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Reset Daily Usage");
        confirmation.setHeaderText("Reset today's usage for \"" + category.getName() + "\"?");
        confirmation.setContentText("This will restore the full daily limit for the rest of today. Existing entries remain in the timeline.");
        Optional<ButtonType> response = confirmation.showAndWait();
        if (response.isPresent() && response.get() == ButtonType.OK) {
            boolean wasRunning = sessionService.getActiveSession()
                    .map(active -> active.category().getId() == category.getId())
                    .orElse(false);
            if (wasRunning) {
                stopTimer();
                timerLabel.setText("00:00:00");
                startTimeLabel.setText("Start Time: -");
                statusLabel.setText("Status: Idle");
                startStopButton.setText("Start");
                resetButton.setDisable(true);
            }
            try {
                sessionService.resetUsageForToday(category);
                updateSelectedCategoryLabel(categoryListView.getSelectionModel().getSelectedItem());
                updateControlAvailability();
            } catch (IllegalStateException | IllegalArgumentException e) {
                showError("Unable to reset usage", e.getMessage());
            }
        }
    }

    void promptAdjustRemainingTime(Category category) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Adjust Today's Remaining Time");
        dialog.setHeaderText("Set today's remaining time for \"" + category.getName() + "\".");
        dialog.setContentText("Minutes (leave blank for unlimited today):");
        Optional<String> response = dialog.showAndWait();
        if (response.isEmpty()) {
            return;
        }
        String trimmed = response.get().trim();
        try {
            if (trimmed.isEmpty()) {
                sessionService.setRemainingSecondsForToday(category, null);
            } else {
                long minutes = Long.parseLong(trimmed);
                if (minutes <= 0) {
                    throw new NumberFormatException();
                }
                sessionService.setRemainingSecondsForToday(category, minutes * 60L);
            }
            updateSelectedCategoryLabel(categoryListView.getSelectionModel().getSelectedItem());
            updateControlAvailability();
        } catch (NumberFormatException e) {
            showError("Invalid input", "Please enter a positive number of minutes or leave blank for unlimited.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            showError("Unable to adjust remaining time", e.getMessage());
        }
    }

    void promptDeleteCategory(Category category) {
        if (sessionService.getActiveSession()
                .map(active -> active.category().getId() == category.getId())
                .orElse(false)) {
            showError("Cannot delete category", "Stop the running session before deleting this category.");
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Category");
        confirmation.setHeaderText("Delete \"" + category.getName() + "\"?");
        confirmation.setContentText("All recorded sessions for this category will be removed.");
        Optional<ButtonType> response = confirmation.showAndWait();
        if (response.isPresent() && response.get() == ButtonType.OK) {
            try {
                sessionService.deleteSessionsForCategory(category.getId());
                categoryService.deleteCategory(category.getId());
                removeCategoryFromList(category.getId());
                refreshTimeline();
                refreshHistoryRange();
            } catch (IllegalStateException e) {
                showError("Unable to delete category", e.getMessage());
            }
        }
    }

    private void removeCategoryFromList(int categoryId) {
        int currentIndex = categoryListView.getSelectionModel().getSelectedIndex();
        for (int i = 0; i < categoryItems.size(); i++) {
            if (categoryItems.get(i).getId() == categoryId) {
                categoryItems.remove(i);
                if (currentIndex >= categoryItems.size()) {
                    currentIndex = categoryItems.size() - 1;
                }
                break;
            }
        }
        if (categoryItems.isEmpty()) {
            categoryListView.getSelectionModel().clearSelection();
        } else if (currentIndex >= 0) {
            categoryListView.getSelectionModel().select(currentIndex);
        }
        updateSelectedCategoryLabel(categoryListView.getSelectionModel().getSelectedItem());
        updateControlAvailability();
    }

    void promptEditSession(SessionViewModel session) {
        Optional<SessionDto> sessionOpt;
        try {
            sessionOpt = sessionService.findSessionById(session.id());
        } catch (IllegalArgumentException e) {
            showError("Session not found", e.getMessage());
            return;
        }
        if (sessionOpt.isEmpty()) {
            showError("Session not found", "The selected session could not be located.");
            return;
        }
        SessionDto existing = sessionOpt.get();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Session");
        dialog.setHeaderText("Update session for \"" + existing.getCategoryName() + "\"");
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        DatePicker startDatePicker = new DatePicker(existing.getStartTime().toLocalDate());
        TextField startTimeField = new TextField(existing.getStartTime().toLocalTime().format(TIME_INPUT_FORMATTER));
        DatePicker endDatePicker = new DatePicker(existing.getEndTime().toLocalDate());
        TextField endTimeField = new TextField(existing.getEndTime().toLocalTime().format(TIME_INPUT_FORMATTER));
        Label validationLabel = new Label();
        validationLabel.setStyle("-fx-text-fill: red;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 10, 0, 10));
        grid.add(new Label("Start date:"), 0, 0);
        grid.add(startDatePicker, 1, 0);
        grid.add(new Label("Start time (HH:mm):"), 0, 1);
        grid.add(startTimeField, 1, 1);
        grid.add(new Label("End date:"), 0, 2);
        grid.add(endDatePicker, 1, 2);
        grid.add(new Label("End time (HH:mm):"), 0, 3);
        grid.add(endTimeField, 1, 3);
        grid.add(validationLabel, 0, 4, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                LocalDate startDate = startDatePicker.getValue();
                LocalDate endDate = endDatePicker.getValue();
                if (startDate == null || endDate == null) {
                    throw new IllegalArgumentException("Select start and end dates.");
                }
                LocalTime startTime = LocalTime.parse(startTimeField.getText().trim(), TIME_INPUT_FORMATTER);
                LocalTime endTime = LocalTime.parse(endTimeField.getText().trim(), TIME_INPUT_FORMATTER);
                LocalDateTime newStart = LocalDateTime.of(startDate, startTime);
                LocalDateTime newEnd = LocalDateTime.of(endDate, endTime);
                sessionService.updateSession(session.id(), newStart, newEnd);
                validationLabel.setText("");
                refreshTimeline();
                refreshHistoryRange();
            } catch (DateTimeParseException ex) {
                validationLabel.setText("Enter time in HH:mm format.");
                event.consume();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                validationLabel.setText(ex.getMessage());
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    void promptDeleteSession(SessionViewModel session) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Session");
        confirmation.setHeaderText("Delete this session?");
        confirmation.setContentText(session.asDisplayString());
        Optional<ButtonType> response = confirmation.showAndWait();
        if (response.isPresent() && response.get() == ButtonType.OK) {
            try {
                boolean deleted = sessionService.deleteSession(session.id());
                if (deleted) {
                    refreshTimeline();
                    refreshHistoryRange();
                    timelineListView.getSelectionModel().clearSelection();
                    historyListView.getSelectionModel().clearSelection();
                    updateSelectedCategoryLabel(categoryListView.getSelectionModel().getSelectedItem());
                } else {
                    showError("Unable to delete session", "The session could not be found. It may have already been deleted.");
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                showError("Unable to delete session", e.getMessage());
            }
        }
    }

    private void exportIcsForRange(LocalDate start, LocalDate end, String defaultFileName) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Sessions to iCalendar");
        fileChooser.setInitialFileName(defaultFileName);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("iCalendar Files", "*.ics"));
        File target = fileChooser.showSaveDialog(timerLabel.getScene().getWindow());
        if (target == null) {
            return;
        }
        try {
            String ics = sessionService.generateIcsForDateRange(start, end);
            Files.writeString(target.toPath(), ics, StandardCharsets.UTF_8);
            showInfo("Export complete", "Saved sessions to:\n" + target.getAbsolutePath());
        } catch (IOException e) {
            showError("Export failed", "Unable to write file: " + e.getMessage());
        }
    }

    private void exportCsvForRange(LocalDate start, LocalDate end, String defaultFileName) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Sessions to CSV");
        fileChooser.setInitialFileName(defaultFileName);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File target = fileChooser.showSaveDialog(timerLabel.getScene().getWindow());
        if (target == null) {
            return;
        }
        try {
            String csv = sessionService.generateCsvForDateRange(start, end);
            Files.writeString(target.toPath(), csv, StandardCharsets.UTF_8);
            showInfo("Export complete", "Saved sessions to:\n" + target.getAbsolutePath());
        } catch (IOException e) {
            showError("Export failed", "Unable to write file: " + e.getMessage());
        }
    }

    private Optional<DateRange> resolveSelectedRange() {
        if (rangeStartDatePicker == null || rangeEndDatePicker == null) {
            return Optional.empty();
        }
        LocalDate start = rangeStartDatePicker.getValue();
        LocalDate end = rangeEndDatePicker.getValue();
        if (start == null || end == null) {
            rangeErrorLabel.setText("Select both start and end dates.");
            showError("Invalid date range", "Select both start and end dates.");
            return Optional.empty();
        }
        if (end.isBefore(start)) {
            rangeErrorLabel.setText("End date must be on or after start date.");
            showError("Invalid date range", "End date must be on or after start date.");
            return Optional.empty();
        }
        return Optional.of(new DateRange(start, end));
    }

    private void configureSessionList(ListView<SessionViewModel> listView) {
        listView.setCellFactory(view -> new SessionListCell(this));
        listView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                SessionViewModel selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    promptDeleteSession(selected);
                }
            }
        });
    }

    @FXML
    private void onRefreshAuto() {
        refreshAutoUsage();
    }

    @FXML
    private void onToggleTracking() {
        if (trackingService == null || trackingPauseToggle == null) {
            return;
        }
        boolean enable = trackingPauseToggle.isSelected();
        trackingService.setPaused(!enable);
        updateTrackingToggle();
        updateAutoControls();
        refreshAutoUsage();
    }

    @FXML
    private void onExportAutoCsv() {
        if (autoTotals.isEmpty()) {
            showInfo("Nothing to export", "No auto activity totals available.");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Auto Usage to CSV");
        LocalDate date = autoDatePicker != null && autoDatePicker.getValue() != null
                ? autoDatePicker.getValue()
                : LocalDate.now();
        fileChooser.setInitialFileName("AutoUsage-" + date + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File target = fileChooser.showSaveDialog(timerLabel.getScene().getWindow());
        if (target == null) {
            return;
        }
        String lineSep = System.lineSeparator();
        StringBuilder builder = new StringBuilder();
        builder.append("App/Site,URL,Duration").append(lineSep);
        for (ActivityTotalViewModel row : autoTotals) {
            builder.append(escapeCsv(row.appOrSite())).append(',')
                    .append(escapeCsv(row.url() == null ? "" : row.url())).append(',')
                    .append(row.formattedDuration()).append(lineSep);
        }
        try {
            Files.writeString(target.toPath(), builder.toString(), StandardCharsets.UTF_8);
            showInfo("Export complete", "Saved auto usage to:\n" + target.getAbsolutePath());
        } catch (IOException e) {
            showError("Export failed", "Unable to write file: " + e.getMessage());
        }
    }

    public void setTrackingDependencies(ActivityTrackingService trackingService,
                                        ActivityAggregationJob aggregationJob,
                                        ActivityReportingService reportingService,
                                        ActivityTrackingConfig config) {
        this.trackingService = trackingService;
        this.aggregationJob = aggregationJob;
        this.reportingService = reportingService;
        this.trackingConfig = config;
        updateTrackingToggle();
    }

    private void refreshAutoUsage() {
        if (aggregationJob == null || reportingService == null) {
            return;
        }
        if (trackingService != null && trackingService.isPaused()) {
            autoTotals.clear();
            updateAutoControls();
            return;
        }
        try {
            LocalDate date = autoDatePicker != null && autoDatePicker.getValue() != null
                    ? autoDatePicker.getValue()
                    : LocalDate.now();
            Instant startOfDay = date.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant();
            Instant endOfDay = date.plusDays(1).atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant();
            aggregationJob.aggregate(startOfDay, endOfDay, true);
            List<ActivityDailyTotal> totals = reportingService.getTotalsForDate(date);
            autoTotals.setAll(toAutoViewModels(totals));
            updateAutoControls();
        } catch (Exception e) {
            autoTotals.clear();
            updateAutoControls();
        }
    }

    private List<ActivityTotalViewModel> toAutoViewModels(List<ActivityDailyTotal> totals) {
        List<ActivityTotalViewModel> viewModels = new ArrayList<>();
        for (ActivityDailyTotal total : totals) {
            String label = total.appId();
            if (total.domain() != null) {
                label = total.appId() + " / " + total.domain();
            }
            viewModels.add(new ActivityTotalViewModel(
                    label == null ? "-" : label,
                    total.url(),
                    total.totalSeconds(),
                    TimeUtils.formatHHmmss(total.totalSeconds())
            ));
        }
        return viewModels;
    }

    private String escapeCsv(String value) {
        String safeValue = value == null ? "" : value;
        boolean needsQuoting = safeValue.contains(",") || safeValue.contains("\"")
                || safeValue.contains("\n") || safeValue.contains("\r");
        String escaped = safeValue.replace("\"", "\"\"");
        if (needsQuoting) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private void updateTrackingToggle() {
        if (trackingPauseToggle == null || trackingService == null) {
            return;
        }
        boolean paused = trackingService.isPaused();
        trackingPauseToggle.setSelected(!paused);
        trackingPauseToggle.setText(paused ? "Enable Auto Tracking" : "Disable Auto Tracking");
        if (trackingConfig != null) {
            trackingPauseToggle.setTooltip(new Tooltip(
                    "Background polling: " + trackingConfig.pollingInterval().toSeconds() + "s, " +
                            "idle threshold: " + trackingConfig.idleThreshold().toMinutes() + "m"));
        }
    }

    private void updateAutoControls() {
        boolean paused = trackingService != null && trackingService.isPaused();
        boolean disableActions = paused;
        if (autoDatePicker != null) {
            autoDatePicker.setDisable(disableActions);
        }
        if (autoExportButton != null) {
            autoExportButton.setDisable(disableActions || autoTotals.isEmpty());
        }
        if (autoTotalsTable != null) {
            autoTotalsTable.setDisable(disableActions);
            if (paused) {
                autoTotalsTable.setPlaceholder(new Label("Auto tracking disabled"));
            } else {
                autoTotalsTable.setPlaceholder(new Label("No auto activity"));
            }
        }
    }

    ObservableList<Category> getCategoryItems() {
        return categoryItems;
    }

    Category getSelectedCategory() {
        return categoryListView.getSelectionModel().getSelectedItem();
    }

    Optional<SessionService.ActiveSession> getActiveSession() {
        return sessionService.getActiveSession();
    }

    OptionalLong getRemainingSeconds(Category category) {
        if (category == null) {
            return OptionalLong.empty();
        }
        return sessionService.getRemainingSecondsForCategoryToday(category);
    }

    boolean canStartCategory(Category category) {
        if (category == null) {
            return false;
        }
        OptionalLong remaining = sessionService.getRemainingSecondsForCategoryToday(category);
        return remaining.isEmpty() || remaining.getAsLong() > 0;
    }

    void toggleSessionFromCompact() {
        onStartStop();
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}
