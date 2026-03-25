import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;

public class Controller {
    private static final int TOKEN_LIFETIME_SECONDS = 30;

    @FXML
    private VBox loginPane;
    @FXML
    private BorderPane dashboardPane;
    @FXML
    private Label authTitleLabel;
    @FXML
    private Label authSubtitleLabel;
    @FXML
    private VBox loginFormPane;
    @FXML
    private VBox signupFormPane;
    @FXML
    private TextField teacherIdField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField signupNameField;
    @FXML
    private TextField signupTeacherIdField;
    @FXML
    private PasswordField signupPasswordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label authMessageLabel;
    @FXML
    private Button switchAuthModeButton;
    @FXML
    private Label teacherNameLabel;
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
    private boolean teacherLoggedIn;
    private boolean signupMode;
    private String currentTeacherName = "Teacher";
    private final Map<String, String> teacherPasswords = new HashMap<>();
    private final Map<String, String> teacherNames = new HashMap<>();

    @FXML
    private void initialize() {
        studentNameColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(cellData.getValue().getName()));
        timeMarkedColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(cellData.getValue().getTimeMarked()));

        attendanceTable.setItems(attendanceRows);
        attendanceTable.setPlaceholder(new Label("No attendance marked yet"));
        teacherPasswords.put("teacher", "smart123");
        teacherNames.put("teacher", "Demo Teacher");

        localScanServer.start();
        configureTimeline();
        showLoginView();
        setSignupMode(false);
        teacherIdField.requestFocus();
    }

    @FXML
    private void handleMarkAttendance() {
        if (!teacherLoggedIn) {
            showAlert(Alert.AlertType.WARNING, "Teacher Login Required", "Log in before marking attendance.");
            return;
        }

        if (!sessionActive) {
            showAlert(Alert.AlertType.WARNING, "Session Inactive", "Start the session before marking attendance.");
            return;
        }

        AttendanceManager.MarkAttempt attempt = attendanceManager.markRandomAttendance();
        applyAttendanceAttempt(attempt);
    }

    @FXML
    private void handleSessionToggle() {
        if (!teacherLoggedIn) {
            showAlert(Alert.AlertType.WARNING, "Teacher Login Required", "Log in before managing the session.");
            return;
        }

        if (sessionActive) {
            stopSession();
        } else {
            startSession();
        }
    }

    @FXML
    private void handleTeacherLogin() {
        String teacherId = teacherIdField.getText() == null ? "" : teacherIdField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (teacherId.isEmpty() || password.isEmpty()) {
            showAuthMessage("Enter both teacher ID and password.");
            return;
        }

        String storedPassword = teacherPasswords.get(teacherId);
        if (storedPassword == null || !storedPassword.equals(password)) {
            showAuthMessage("Invalid teacher ID or password.");
            passwordField.clear();
            return;
        }

        teacherLoggedIn = true;
        currentTeacherName = teacherNames.getOrDefault(teacherId, teacherId);
        teacherNameLabel.setText("Teacher: " + currentTeacherName);
        passwordField.clear();
        hideAuthMessage();
        showDashboardView();
        startSession();
    }

    @FXML
    private void handleLogout() {
        teacherLoggedIn = false;
        stopSession();
        teacherIdField.clear();
        passwordField.clear();
        hideAuthMessage();
        setSignupMode(false);
        showLoginView();
        teacherIdField.requestFocus();
    }

    @FXML
    private void handleTeacherSignup() {
        String teacherName = signupNameField.getText() == null ? "" : signupNameField.getText().trim();
        String teacherId = signupTeacherIdField.getText() == null ? "" : signupTeacherIdField.getText().trim();
        String password = signupPasswordField.getText() == null ? "" : signupPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        if (teacherName.isEmpty() || teacherId.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showAuthMessage("Complete all sign-up fields.");
            return;
        }

        if (teacherPasswords.containsKey(teacherId)) {
            showAuthMessage("Teacher ID already exists. Choose a different one.");
            return;
        }

        if (password.length() < 6) {
            showAuthMessage("Password must be at least 6 characters.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showAuthMessage("Passwords do not match.");
            signupPasswordField.clear();
            confirmPasswordField.clear();
            return;
        }

        teacherPasswords.put(teacherId, password);
        teacherNames.put(teacherId, teacherName);

        teacherIdField.setText(teacherId);
        passwordField.clear();
        clearSignupFields();
        setSignupMode(false);
        showAuthMessage("Sign-up complete. Log in with your new teacher account.");
    }

    @FXML
    private void handleSwitchAuthMode() {
        setSignupMode(!signupMode);
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

    private void showLoginView() {
        loginPane.setManaged(true);
        loginPane.setVisible(true);
        dashboardPane.setManaged(false);
        dashboardPane.setVisible(false);
    }

    private void showDashboardView() {
        loginPane.setManaged(false);
        loginPane.setVisible(false);
        dashboardPane.setManaged(true);
        dashboardPane.setVisible(true);
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

    private void showAuthMessage(String message) {
        authMessageLabel.setText(message);
        authMessageLabel.setManaged(true);
        authMessageLabel.setVisible(true);
    }

    private void hideAuthMessage() {
        authMessageLabel.setText("");
        authMessageLabel.setManaged(false);
        authMessageLabel.setVisible(false);
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

    private void setSignupMode(boolean enabled) {
        signupMode = enabled;
        loginFormPane.setManaged(!enabled);
        loginFormPane.setVisible(!enabled);
        signupFormPane.setManaged(enabled);
        signupFormPane.setVisible(enabled);

        if (enabled) {
            authTitleLabel.setText("Teacher Sign Up");
            authSubtitleLabel.setText("Create a new teacher account for this demo session.");
            switchAuthModeButton.setText("Back to Login");
            clearLoginFields();
            hideAuthMessage();
            signupNameField.requestFocus();
            return;
        }

        authTitleLabel.setText("Teacher Login");
        authSubtitleLabel.setText("Sign in to open the attendance dashboard.");
        switchAuthModeButton.setText("Create New Teacher Account");
        clearSignupFields();
        hideAuthMessage();
        teacherIdField.requestFocus();
    }

    private void clearLoginFields() {
        teacherIdField.clear();
        passwordField.clear();
    }

    private void clearSignupFields() {
        signupNameField.clear();
        signupTeacherIdField.clear();
        signupPasswordField.clear();
        confirmPasswordField.clear();
    }
}
