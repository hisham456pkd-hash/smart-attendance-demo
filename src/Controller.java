import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

public class Controller {
    private static final int TOKEN_LIFETIME_SECONDS = 30;

    @FXML
    private Label statusDotLabel;
    @FXML
    private Label statusTextLabel;
    @FXML
    private Label countdownLabel;
    @FXML
    private Label tokenValueLabel;
    @FXML
    private Label attendanceCountLabel;
    @FXML
    private ImageView qrImageView;
    @FXML
    private Button markAttendanceButton;
    @FXML
    private Button sessionButton;
    @FXML
    private TableView<Student> attendanceTable;
    @FXML
    private TableColumn<Student, String> studentNameColumn;
    @FXML
    private TableColumn<Student, String> timeMarkedColumn;

    private final AttendanceManager attendanceManager = new AttendanceManager();
    private final ObservableList<Student> attendanceRows = FXCollections.observableArrayList();
    private boolean sessionActive;
    private int remainingSeconds;
    private String currentToken;
    private final LocalScanServer localScanServer = new LocalScanServer(
            () -> sessionActive,
            () -> currentToken,
            this::applyAttendanceAttempt,
            attendanceManager
    );

    private Timeline countdownTimeline;

    @FXML
    private void initialize() {
        studentNameColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(cellData.getValue().getName()));
        timeMarkedColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(cellData.getValue().getTimeMarked()));

        attendanceTable.setItems(attendanceRows);
        attendanceTable.setPlaceholder(new Label("No attendance marked yet"));

        localScanServer.start();
        configureTimeline();
        startSession();
    }

    @FXML
    private void handleMarkAttendance() {
        if (!sessionActive) {
            showAlert(Alert.AlertType.WARNING, "Session Inactive", "Start the session before marking attendance.");
            return;
        }

        AttendanceManager.MarkAttempt attempt = attendanceManager.markRandomAttendance();
        applyAttendanceAttempt(attempt);
    }

    @FXML
    private void handleSessionToggle() {
        if (sessionActive) {
            stopSession();
        } else {
            startSession();
        }
    }

    private void configureTimeline() {
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateCountdown()));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
    }

    private void updateCountdown() {
        if (!sessionActive) {
            return;
        }

        if (remainingSeconds == 0) {
            refreshQrToken();
            return;
        }

        remainingSeconds--;
        updateCountdownLabel();
    }

    private void startSession() {
        sessionActive = true;
        refreshQrToken();
        countdownTimeline.play();

        statusDotLabel.setText("\u25cf");
        statusDotLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 14px;");
        statusTextLabel.setText("Session Active");
        statusTextLabel.setStyle("-fx-text-fill: #0f172a; -fx-font-size: 16px; -fx-font-weight: 700;");
        sessionButton.setText("End Session");
        markAttendanceButton.setDisable(false);
        qrImageView.setOpacity(1.0);
    }

    private void stopSession() {
        sessionActive = false;
        countdownTimeline.stop();

        statusDotLabel.setText("\u25cf");
        statusDotLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 14px;");
        statusTextLabel.setText("Session Inactive");
        statusTextLabel.setStyle("-fx-text-fill: #0f172a; -fx-font-size: 16px; -fx-font-weight: 700;");
        countdownLabel.setText("Countdown paused");
        tokenValueLabel.setText("Session stopped");
        sessionButton.setText("Start Session");
        markAttendanceButton.setDisable(true);
        qrImageView.setOpacity(0.35);
    }

    private void refreshQrToken() {
        currentToken = attendanceManager.generateSessionToken();
        String scanPath = localScanServer.getBaseUrl() + "/scan?token=" + currentToken;
        Image qrImage = QRGenerator.generateQrCode(scanPath, 280, 280);

        qrImageView.setImage(qrImage);
        tokenValueLabel.setText(scanPath);
        remainingSeconds = TOKEN_LIFETIME_SECONDS;
        updateCountdownLabel();
    }

    private void updateCountdownLabel() {
        countdownLabel.setText("Refreshes in " + remainingSeconds + "s");
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void shutdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        localScanServer.stop();
    }

    private void applyAttendanceAttempt(AttendanceManager.MarkAttempt attempt) {
        if (attempt.getStudent() != null) {
            attendanceRows.add(attempt.getStudent());
            attendanceCountLabel.setText("Marked: " + attendanceRows.size());
            attendanceTable.scrollTo(attendanceRows.size() - 1);
            return;
        }

        if (attempt.isDuplicate()) {
            showAlert(Alert.AlertType.INFORMATION, "Duplicate Attendance", attempt.getMessage());
            return;
        }

        showAlert(Alert.AlertType.WARNING, "Attendance Not Marked", attempt.getMessage());
    }
}
