import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LocalScanServer {
    private static final int PREFERRED_PORT = 8085;

    private final Supplier<Boolean> sessionActiveSupplier;
    private final Supplier<String> tokenSupplier;
    private final Consumer<AttendanceManager.MarkAttempt> attendanceConsumer;
    private final AttendanceManager attendanceManager;

    private HttpServer httpServer;
    private String baseUrl;

    public LocalScanServer(Supplier<Boolean> sessionActiveSupplier,
                           Supplier<String> tokenSupplier,
                           Consumer<AttendanceManager.MarkAttempt> attendanceConsumer,
                           AttendanceManager attendanceManager) {
        this.sessionActiveSupplier = sessionActiveSupplier;
        this.tokenSupplier = tokenSupplier;
        this.attendanceConsumer = attendanceConsumer;
        this.attendanceManager = attendanceManager;
    }

    public void start() {
        if (httpServer != null) {
            return;
        }

        try {
            httpServer = createHttpServer();
            httpServer.createContext("/scan", new ScanPageHandler());
            httpServer.createContext("/mark", new MarkAttendanceHandler());
            httpServer.setExecutor(null);
            httpServer.start();
            baseUrl = "http://" + resolveLocalIpAddress() + ":" + httpServer.getAddress().getPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start local scan server.", exception);
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    private HttpServer createHttpServer() throws IOException {
        try {
            return HttpServer.create(new InetSocketAddress(PREFERRED_PORT), 0);
        } catch (IOException ignored) {
            return HttpServer.create(new InetSocketAddress(0), 0);
        }
    }

    private String resolveLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (IOException exception) {
            return "127.0.0.1";
        }
    }

    private boolean isValidToken(String token) {
        return token != null && token.equals(tokenSupplier.get()) && sessionActiveSupplier.get();
    }

    private void writeHtml(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] responseBytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }

    private String buildPage(String title, String message, String token, boolean showForm) {
        String safeToken = escapeHtml(token == null ? "" : token);
        String form = showForm
                ? "<form method='post' action='/mark'>" +
                  "<input type='hidden' name='token' value='" + safeToken + "' />" +
                  "<label for='studentName'>Your name</label>" +
                  "<input id='studentName' name='studentName' type='text' placeholder='Enter your full name' required />" +
                  "<button type='submit'>Mark Attendance</button>" +
                  "</form>"
                : "";

        return "<!DOCTYPE html><html><head><meta charset='UTF-8' />" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0' />" +
                "<title>" + escapeHtml(title) + "</title>" +
                "<style>" +
                "body{margin:0;font-family:Segoe UI,Arial,sans-serif;background:linear-gradient(135deg,#e0f2fe,#f8fafc);color:#0f172a;}" +
                ".wrap{max-width:420px;margin:40px auto;padding:28px;background:#ffffff;border-radius:24px;box-shadow:0 20px 60px rgba(15,23,42,.12);}" +
                "h1{margin:0 0 8px;font-size:28px;}p{color:#475569;line-height:1.5;}label{display:block;margin:18px 0 8px;font-weight:600;}" +
                "input{width:100%;padding:14px 16px;border:1px solid #cbd5e1;border-radius:14px;font-size:16px;box-sizing:border-box;}" +
                "button{width:100%;margin-top:16px;padding:14px 16px;border:0;border-radius:14px;background:linear-gradient(90deg,#0f766e,#14b8a6);color:#fff;font-size:16px;font-weight:700;}" +
                ".badge{display:inline-block;padding:8px 12px;border-radius:999px;background:#ecfeff;color:#155e75;font-size:12px;font-weight:700;}" +
                "</style></head><body><div class='wrap'><span class='badge'>Smart Attendance Demo</span>" +
                "<h1>" + escapeHtml(title) + "</h1><p>" + escapeHtml(message) + "</p>" + form + "</div></body></html>";
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private Map<String, String> parseFormData(String formData) {
        Map<String, String> values = new HashMap<>();
        if (formData == null || formData.isBlank()) {
            return values;
        }

        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }

    private class ScanPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> queryValues = parseFormData(exchange.getRequestURI().getQuery());
            String token = queryValues.get("token");

            if (!isValidToken(token)) {
                writeHtml(exchange, 410, buildPage("QR Expired", "This QR code is invalid or has expired. Please scan the latest code from the desktop app.", null, false));
                return;
            }

            writeHtml(exchange, 200, buildPage("Mark Your Attendance", "Enter your name exactly once while this session is active.", token, true));
        }
    }

    private class MarkAttendanceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeHtml(exchange, 405, buildPage("Method Not Allowed", "Use the attendance form to submit your name.", null, false));
                return;
            }

            String formData = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> values = parseFormData(formData);
            String token = values.get("token");

            if (!isValidToken(token)) {
                writeHtml(exchange, 410, buildPage("QR Expired", "This QR code expired before submission. Scan the latest QR code and try again.", null, false));
                return;
            }

            AttendanceManager.MarkAttempt attempt = attendanceManager.markAttendance(values.get("studentName"));
            if (attempt.getStudent() != null) {
                Platform.runLater(() -> attendanceConsumer.accept(attempt));
            }

            String title = attempt.getStudent() != null ? "Attendance Marked" : "Attendance Not Marked";
            writeHtml(exchange, 200, buildPage(title, attempt.getMessage(), null, false));
        }
    }
}
