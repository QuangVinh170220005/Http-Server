package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

class RequestHandler implements Runnable {
    private Socket socket;
    private int requestId;
    private static final String WEB_ROOT = "www"; // Thư mục chứa file HTML

    public RequestHandler(Socket socket, int requestId) {
        this.socket = socket;
        this.requestId = requestId;
    }

    @Override
    public void run() {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                socket.close();
                return;
            }

            System.out.println("[" + requestId + "] Request: " + requestLine);

            String[] parts = requestLine.split(" ");
            if (parts.length < 3) {
                sendBadRequest(socket);
                return;
            }

            String method = parts[0];
            String path = parts[1];

            // Đọc headers
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                int colonIndex = line.indexOf(": ");
                if (colonIndex > 0) {
                    String key = line.substring(0, colonIndex);
                    String value = line.substring(colonIndex + 2);
                    headers.put(key, value);
                }
            }

            // Đọc body (nếu là POST)
            String body = "";
            if (method.equals("POST") && headers.containsKey("Content-Length")) {
                int contentLength = Integer.parseInt(headers.get("Content-Length"));
                char[] bodyChars = new char[contentLength];
                in.read(bodyChars, 0, contentLength);
                body = new String(bodyChars);
            }

            // Xử lý request theo method
            switch (method) {
                case "GET":
                    handleGET(socket, path);
                    break;
                case "HEAD":
                    handleHEAD(socket, path);
                    break;
                case "POST":
                    handlePOST(socket, path, body);
                    break;
                default:
                    sendMethodNotAllowed(socket);
            }

            System.out.println("[" + requestId + "] Response sent\n");

        } catch (Exception e) {
            System.err.println("[" + requestId + "] Lỗi: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Đọc file HTML từ hệ thống
    private String readHTMLFile(String filename) throws IOException {
        Path filePath = Paths.get(WEB_ROOT, filename);

        if (!Files.exists(filePath)) {
            return null;
        }

        return new String(Files.readAllBytes(filePath), "UTF-8");
    }

    // Xử lý GET request
    private void handleGET(Socket socket, String path) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        String html;

        // Thử đọc file từ thư mục www
        String fileHtml = readHTMLFile("index.html");
        
        if (fileHtml != null) {
            html = fileHtml;
        } else {
            // Nếu không tìm thấy file, trả về HTML tĩnh
            html = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "    <title>HTTP Server Test Page</title>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <h1>Welcome to HTTP Server</h1>\n" +
                    "    <p>This is a static page served when index.html is not found.</p>\n" +
                    "    <div class=\"container\">\n" +
                    "        <p>You can test different HTTP methods:</p>\n" +
                    "        <ul>\n" +
                    "            <li>GET: Just visit any URL</li>\n" +
                    "            <li>POST: Use a form or API client</li>\n" +
                    "            <li>HEAD: Use an API client</li>\n" +
                    "        </ul>\n" +
                    "    </div>\n" +
                    "</body>\n" +
                    "</html>";
        }

        // Gửi response
        out.print("HTTP/1.1 200 OK\r\n");
        out.print("Content-Type: text/html; charset=UTF-8\r\n");
        out.print("Content-Length: " + html.getBytes("UTF-8").length + "\r\n");
        out.print("Server: MyHTTPServer/1.0\r\n");
        out.print("Date: " + getHTTPDate() + "\r\n");
        out.print("Connection: close\r\n");
        out.print("\r\n");
        out.print(html);
        out.flush();
    }

    // Xử lý HEAD request
    private void handleHEAD(Socket socket, String path) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        Path filePath = Paths.get(WEB_ROOT, "index.html");
        long contentLength;

        if (Files.exists(filePath)) {
            contentLength = Files.size(filePath);
        } else {
            // Nếu file không tồn tại, tính độ dài của HTML tĩnh
            String staticHtml = "<!DOCTYPE html><html><head><title>HTTP Server</title></head><body><h1>Welcome</h1></body></html>";
            contentLength = staticHtml.getBytes("UTF-8").length;
        }

        // CHỈ GỬI HEADER, KHÔNG GỬI BODY
        out.print("HTTP/1.1 200 OK\r\n");
        out.print("Content-Type: text/html; charset=UTF-8\r\n");
        out.print("Content-Length: " + contentLength + "\r\n");
        out.print("Server: MyHTTPServer/1.0\r\n");
        out.print("Date: " + getHTTPDate() + "\r\n");
        if (Files.exists(filePath)) {
            out.print("Last-Modified: " + getHTTPDate(Files.getLastModifiedTime(filePath).toMillis()) + "\r\n");
        }
        out.print("Connection: close\r\n");
        out.print("\r\n");
        out.flush();

        System.out.println("[" + requestId + "] HEAD response (headers only, no body)");
    }

    // Xử lý POST request
    private void handlePOST(Socket socket, String path, String body) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        System.out.println("[" + requestId + "] POST body: " + body);

        // Parse body parameters
        Map<String, String> params = parsePostData(body);

        // Tạo HTML response với dữ liệu POST
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("    <title>POST Response</title>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <style>\n");
        html.append("        body { font-family: Arial, sans-serif; max-width: 800px; margin: 50px auto; padding: 20px; }\n");
        html.append("        .success { color: green; }\n");
        html.append("        .data { background-color: #f0f0f0; padding: 15px; margin: 15px 0; border-radius: 5px; }\n");
        html.append("        button { padding: 10px 20px; margin: 10px 0; cursor: pointer; background-color: #4CAF50; color: white; border: none; border-radius: 4px; }\n");
        html.append("    </style>\n");
        html.append("</head>\n<body>\n");
        html.append("    <h1 class=\"success\">✓ POST Request Received Successfully</h1>\n");
        html.append("    <p>Server đã xử lý POST request của bạn.</p>\n");
        html.append("    <div class=\"data\">\n");
        html.append("        <h2>Data Received:</h2>\n");
        html.append("        <p><strong>Raw body:</strong> ").append(escapeHtml(body)).append("</p>\n");

        if (!params.isEmpty()) {
            html.append("        <h3>Parsed Parameters:</h3>\n");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                html.append("        <p><strong>").append(escapeHtml(entry.getKey()))
                        .append(":</strong> ").append(escapeHtml(entry.getValue())).append("</p>\n");
            }
        }

        html.append("        <p><strong>Total parameters:</strong> ").append(params.size()).append("</p>\n");
        html.append("    </div>\n");
        html.append("    <button onclick=\"window.location.href='/'\">Back to Home</button>\n");
        html.append("    <p>Các tags để test Browser:</p>\n");
        html.append("    <span>Span element 1</span>\n");
        html.append("    <span>Span element 2</span>\n");
        html.append("    <div>Div element</div>\n");
        html.append("    <img src=\"success.png\" alt=\"Success\"/>\n");
        html.append("</body>\n</html>");

        String htmlStr = html.toString();

        // Gửi response
        out.print("HTTP/1.1 200 OK\r\n");
        out.print("Content-Type: text/html; charset=UTF-8\r\n");
        out.print("Content-Length: " + htmlStr.getBytes("UTF-8").length + "\r\n");
        out.print("Server: MyHTTPServer/1.0\r\n");
        out.print("Date: " + getHTTPDate() + "\r\n");
        out.print("Connection: close\r\n");
        out.print("\r\n");
        out.print(htmlStr);
        out.flush();
    }

    // Parse POST data (x-www-form-urlencoded)
    private Map<String, String> parsePostData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isEmpty()) {
            return params;
        }

        String[] pairs = body.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    params.put(key, value);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
        return params;
    }

    // Gửi 404 Not Found
    private void send404(Socket socket, String path) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        String html = "<!DOCTYPE html>\n<html>\n<head><title>404 Not Found</title></head>\n<body>\n" +
                "    <h1>404 - Page Not Found</h1>\n" +
                "    <p>File <strong>" + escapeHtml(path) + "</strong> not found.</p>\n" +
                "    <p>Make sure you have created <strong>www/index.html</strong> file.</p>\n" +
                "</body>\n</html>";

        out.print("HTTP/1.1 404 Not Found\r\n");
        out.print("Content-Type: text/html; charset=UTF-8\r\n");
        out.print("Content-Length: " + html.getBytes("UTF-8").length + "\r\n");
        out.print("Server: MyHTTPServer/1.0\r\n");
        out.print("Date: " + getHTTPDate() + "\r\n");
        out.print("Connection: close\r\n");
        out.print("\r\n");
        out.print(html);
        out.flush();
    }

    // Gửi 400 Bad Request
    private void sendBadRequest(Socket socket) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        String html = "<html><body><h1>400 Bad Request</h1></body></html>";

        out.print("HTTP/1.1 400 Bad Request\r\n");
        out.print("Content-Type: text/html\r\n");
        out.print("Content-Length: " + html.length() + "\r\n");
        out.print("\r\n");
        out.print(html);
        out.flush();
    }

    // Gửi 405 Method Not Allowed
    private void sendMethodNotAllowed(Socket socket) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        String html = "<html><body><h1>405 Method Not Allowed</h1><p>Allowed: GET, HEAD, POST</p></body></html>";

        out.print("HTTP/1.1 405 Method Not Allowed\r\n");
        out.print("Content-Type: text/html\r\n");
        out.print("Allow: GET, HEAD, POST\r\n");
        out.print("Content-Length: " + html.length() + "\r\n");
        out.print("\r\n");
        out.print(html);
        out.flush();
    }

    // Lấy ngày giờ theo định dạng HTTP
    private String getHTTPDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(new Date());
    }

    private String getHTTPDate(long timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(new Date(timestamp));
    }

    // Escape HTML để tránh XSS
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}