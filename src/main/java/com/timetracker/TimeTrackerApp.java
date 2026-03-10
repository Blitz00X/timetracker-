package com.timetracker;

import com.timetracker.db.DatabaseInitializer;
import com.timetracker.tracking.ActivityAggregationJob;
import com.timetracker.tracking.ActivityAggregator;
import com.timetracker.tracking.ActivityEventDao;
import com.timetracker.tracking.ActivitySessionDao;
import com.timetracker.tracking.ActivityTrackingService;
import com.timetracker.tracking.IdleDetectionService;
import com.timetracker.tracking.ActiveAppCollector;
import com.timetracker.tracking.LinuxActiveAppCollector;
import com.timetracker.tracking.NoOpActiveAppCollector;
import com.timetracker.tracking.ActivityTrackingConfig;
import com.timetracker.tracking.BrowserUrlResolver;
import com.timetracker.tracking.ChromiumDebugUrlResolver;
import com.timetracker.tracking.ActivityReportingService;
import com.timetracker.controller.CompactWindow;
import com.timetracker.controller.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.application.Platform;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TimeTrackerApp extends Application {

    private ActivityTrackingService activityTrackingService;
    private IdleDetectionService idleDetectionService;
    private ActivityAggregationJob aggregationJob;
    private ScheduledExecutorService aggregationExecutor;
    private CompactWindow compactWindow;

    @Override
    public void init() {
        DatabaseInitializer.initialize();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/timetracker/view/main-view.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();
        primaryStage.setTitle("TimeTracker+");
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/com/timetracker/view/styles.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
        compactWindow = new CompactWindow(primaryStage, controller);
        primaryStage.iconifiedProperty().addListener((obs, oldVal, iconified) -> {
            if (iconified) {
                compactWindow.showInCorner();
            } else {
                compactWindow.hide();
            }
        });

        ActivityEventDao eventDao = new ActivityEventDao();
        ActivityTrackingConfig config = trackingConfig();
        ActiveAppCollector collector = detectCollector(config);
        activityTrackingService = new ActivityTrackingService(collector, eventDao, config);
        idleDetectionService = createIdleDetection(eventDao, config);

        ActivitySessionDao sessionDao = new ActivitySessionDao();
        aggregationJob = new ActivityAggregationJob(eventDao, sessionDao, new ActivityAggregator());

        activityTrackingService.start();
        scheduleAggregation();
        controller.setTrackingDependencies(activityTrackingService, aggregationJob, new ActivityReportingService(sessionDao), config);
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() {
        if (aggregationExecutor != null) {
            aggregationExecutor.shutdownNow();
        }
        if (aggregationJob != null) {
            runDailyAggregation();
        }
        if (activityTrackingService != null) {
            activityTrackingService.close();
        }
        if (idleDetectionService != null) {
            idleDetectionService.close();
        }
    }

    private void scheduleAggregation() {
        aggregationExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "activity-aggregation");
            t.setDaemon(true);
            return t;
        });
        aggregationExecutor.scheduleAtFixedRate(this::runDailyAggregation, 1, 5, TimeUnit.MINUTES);
    }

    private void runDailyAggregation() {
        try {
            Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant now = Instant.now();
            aggregationJob.aggregate(startOfDay, now, true);
        } catch (Exception e) {
            System.err.println("Aggregation failed: " + e.getMessage());
        }
    }

    private ActiveAppCollector detectCollector(ActivityTrackingConfig config) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux")) {
            BrowserUrlResolver resolver = config.captureUrls() ? new ChromiumDebugUrlResolver() : null;
            return new LinuxActiveAppCollector(config.captureUrls(), resolver);
        }
        return new NoOpActiveAppCollector();
    }

    private ActivityTrackingConfig trackingConfig() {
        Duration polling = Duration.ofSeconds(parseLongEnv("TT_POLL_SECONDS", 10));
        Duration idle = Duration.ofMinutes(parseLongEnv("TT_IDLE_MINUTES", 5));
        boolean captureUrls = parseBooleanEnv("TT_CAPTURE_URLS", true);
        boolean redact = parseBooleanEnv("TT_REDACT_QUERY", true);
        return new ActivityTrackingConfig(polling, idle, captureUrls, redact);
    }

    private IdleDetectionService createIdleDetection(ActivityEventDao eventDao, ActivityTrackingConfig config) {
        try {
            IdleDetectionService service = new IdleDetectionService(eventDao, config.idleThreshold());
            service.start();
            return service;
        } catch (Throwable t) {
            System.err.println("Idle detection disabled: " + t.getMessage());
            return null;
        }
    }

    private long parseLongEnv(String key, long defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean parseBooleanEnv(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
    }
}
