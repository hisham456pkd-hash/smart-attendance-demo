# Smart Attendance System (Demo Version)

Java desktop demo that simulates a QR-based attendance workflow using JavaFX and ZXing. It is fully local, runs a tiny in-app HTTP server for phone scans, and keeps attendance only in memory.

## Features

- Live QR code encoding `/scan?token=UUID`
- Token auto-refresh every 30 seconds
- Countdown timer
- Random student attendance simulation
- Duplicate prevention with alert
- Session active/inactive toggle
- Phone scan page where students can enter their names
- Modern JavaFX dashboard UI

## Project Structure

```text
SmartAttendanceDemo/
├── pom.xml
├── README.md
└── src/
    ├── Main.java
    ├── Controller.java
    ├── QRGenerator.java
    ├── AttendanceManager.java
    ├── Student.java
    └── UI.fxml
```

## Requirements

- JDK 17 or newer
- Maven 3.9 or newer

## Run

```bash
mvn javafx:run
```

Then scan the QR from a phone on the same Wi-Fi network, open the local URL, and submit the student name.

## Build

```bash
mvn clean package
```
