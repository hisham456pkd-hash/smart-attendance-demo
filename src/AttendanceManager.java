import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class AttendanceManager {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm:ss a");

    private final List<String> studentNames = Arrays.asList(
            "Aarav Sharma",
            "Ananya Patel",
            "Rohit Verma",
            "Sneha Reddy",
            "Vikram Singh",
            "Priya Nair",
            "Karan Mehta",
            "Isha Kapoor",
            "Rahul Das",
            "Meera Joshi",
            "Neha Kulkarni",
            "Arjun Iyer"
    );

    private final Random random = new Random();
    private final Set<String> markedStudents = new HashSet<>();

    public String generateSessionToken() {
        return UUID.randomUUID().toString();
    }

    public MarkAttempt markRandomAttendance() {
        String selectedName = studentNames.get(random.nextInt(studentNames.size()));
        return markAttendance(selectedName);
    }

    public MarkAttempt markAttendance(String studentName) {
        String normalizedName = studentName == null ? "" : studentName.trim();
        if (normalizedName.isEmpty()) {
            return new MarkAttempt(false, normalizedName, null, "Please enter your name.");
        }

        if (!markedStudents.add(normalizedName.toLowerCase())) {
            return new MarkAttempt(true, normalizedName, null, "Attendance already marked!");
        }

        String timeMarked = LocalDateTime.now().format(TIME_FORMATTER);
        Student student = new Student(normalizedName, timeMarked);
        return new MarkAttempt(false, normalizedName, student, "Attendance marked successfully.");
    }

    public static class MarkAttempt {
        private final boolean duplicate;
        private final String studentName;
        private final Student student;
        private final String message;

        public MarkAttempt(boolean duplicate, String studentName, Student student, String message) {
            this.duplicate = duplicate;
            this.studentName = studentName;
            this.student = student;
            this.message = message;
        }

        public boolean isDuplicate() {
            return duplicate;
        }

        public String getStudentName() {
            return studentName;
        }

        public Student getStudent() {
            return student;
        }

        public String getMessage() {
            return message;
        }
    }
}
