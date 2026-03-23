public class Student {
    private final String name;
    private final String timeMarked;

    public Student(String name, String timeMarked) {
        this.name = name;
        this.timeMarked = timeMarked;
    }

    public String getName() {
        return name;
    }

    public String getTimeMarked() {
        return timeMarked;
    }
}
