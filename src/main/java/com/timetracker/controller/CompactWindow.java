package com.timetracker.controller;

import com.timetracker.model.Category;
import com.timetracker.service.SessionService;
import com.timetracker.util.TimeUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A compact, always-on-top controller window that stays interactive when the
 * main
 * window is minimized. It keeps essential controls reachable without taking up
 * much screen space.
 */
public class CompactWindow {

    private final Stage owner;
    private final MainController mainController;
    private final Stage stage;
    private final ComboBox<Category> categoryCombo;
    private final Label statusLabel;
    private final Label timerLabel;
    private final Button startStopButton;
    private Timeline ticker;

    public CompactWindow(Stage owner, MainController controller) {
        this.owner = owner;
        this.mainController = controller;
        this.categoryCombo = new ComboBox<>();
        this.statusLabel = new Label("Select a category");
        this.timerLabel = new Label("00:00:00");
        this.startStopButton = new Button("Start");

        Button restoreButton = new Button("Open Full View");
        restoreButton.setOnAction(event -> restoreMain());

        VBox root = new VBox(8, statusLabel, categoryCombo, startStopButton, timerLabel, restoreButton);
        root.getStyleClass().add("card");
        root.setFillWidth(true);

        stage = new Stage(StageStyle.UTILITY);
        // stage.initOwner(owner); // Detached to prevent auto-minimizing
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
        stage.setTitle("TimeTracker+ Mini");
        Scene scene = new Scene(root, 320, 190);
        String stylesheet = getClass().getResource("/com/timetracker/view/styles.css").toExternalForm();
        scene.getStylesheets().add(stylesheet);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            event.consume();
            restoreMain();
        });

        wireCategoryCombo();
        wireActions();
    }

    public void showInCorner() {
        refreshState();
        positionBottomRight();
        stage.show();
        stage.toFront();
        startTicker();
    }

    public void hide() {
        stopTicker();
        stage.hide();
    }

    private void wireActions() {
        startStopButton.setOnAction(event -> {
            mainController.toggleSessionFromCompact();
            refreshState();
        });
        categoryCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            mainController.selectCategory(newVal);
            refreshState();
        });
    }

    private void wireCategoryCombo() {
        ObservableList<Category> categories = mainController.getCategoryItems();
        categoryCombo.setItems(categories);
        categoryCombo.setCellFactory(list -> createCategoryCell());
        categoryCombo.setButtonCell(createCategoryCell());
        categories.addListener((ListChangeListener<? super Category>) change -> {
            if (!categories.isEmpty() && categoryCombo.getSelectionModel().getSelectedItem() == null) {
                Category selected = mainController.getSelectedCategory();
                if (selected != null) {
                    categoryCombo.getSelectionModel().select(selected);
                } else {
                    categoryCombo.getSelectionModel().selectFirst();
                }
            }
            refreshState();
        });
        Category selected = mainController.getSelectedCategory();
        if (selected != null) {
            categoryCombo.getSelectionModel().select(selected);
        } else if (!categories.isEmpty()) {
            categoryCombo.getSelectionModel().selectFirst();
        }
    }

    private ListCell<Category> createCategoryCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : mainController.formatCategoryDisplay(item));
            }
        };
    }

    private void refreshState() {
        Optional<SessionService.ActiveSession> active = mainController.getActiveSession();
        Category selection = categoryCombo.getSelectionModel().getSelectedItem();

        if (active.isPresent()) {
            Category running = active.get().category();
            if (selection == null || selection.getId() != running.getId()) {
                categoryCombo.getSelectionModel().select(running);
            }
            statusLabel.setText("Running: " + running.getName());
            timerLabel.setText(formatRunningTimer(active.get()));
            startStopButton.setText("Stop");
            startStopButton.setDisable(false);
        } else {
            if (selection != null) {
                statusLabel.setText("Ready: " + selection.getName());
                timerLabel.setText(formatRemaining(selection));
            } else {
                statusLabel.setText("Select a category");
                timerLabel.setText("00:00:00");
            }
            boolean canStart = selection != null && mainController.canStartCategory(selection);
            startStopButton.setText("Start");
            startStopButton.setDisable(!canStart);
        }
    }

    private String formatRunningTimer(SessionService.ActiveSession active) {
        long elapsedSeconds = Duration.between(active.startTime(), LocalDateTime.now()).getSeconds();
        Long allowed = active.allowedSeconds();
        if (allowed != null) {
            long remaining = Math.max(0, allowed - elapsedSeconds);
            return "Remaining: " + TimeUtils.formatHHmmss(remaining);
        }
        return "Elapsed: " + TimeUtils.formatHHmmss(elapsedSeconds);
    }

    private String formatRemaining(Category selection) {
        java.util.OptionalLong remaining = mainController.getRemainingSeconds(selection);
        if (remaining.isEmpty()) {
            return "Remaining: Unlimited";
        }
        return "Remaining: " + TimeUtils.formatHHmmss(Math.max(0, remaining.getAsLong()));
    }

    private void positionBottomRight() {
        Rectangle2D ownerBounds = new Rectangle2D(
                owner.getX(),
                owner.getY(),
                Math.max(owner.getWidth(), 1),
                Math.max(owner.getHeight(), 1));
        Screen screen = Screen.getScreensForRectangle(ownerBounds).stream()
                .findFirst()
                .orElse(Screen.getPrimary());
        Rectangle2D bounds = screen.getVisualBounds();
        stage.sizeToScene();
        double x = bounds.getMaxX() - stage.getWidth() - 12;
        double y = bounds.getMaxY() - stage.getHeight() - 12;
        stage.setX(x);
        stage.setY(y);
    }

    private void startTicker() {
        stopTicker();
        ticker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> refreshState()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.playFromStart();
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.stop();
            ticker = null;
        }
    }

    private void restoreMain() {
        hide();
        if (!owner.isShowing()) {
            owner.show();
        }
        owner.setIconified(false);
        owner.toFront();
        owner.requestFocus();
    }
}
