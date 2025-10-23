package org.example;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;
class RequestHandler implements Runnable {
    private Socket socket;
    private int requestId;

    public RequestHandler(Socket socket, int requestId) {
        this.socket = socket;
        this.requestId = requestId;
    }

    @Override
    public void run() {
        try {
            // Đọc request từ client
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );

            // Đọc dòng đầu tiên: GET /path HTTP/1.1
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                socket.close();
                return;
            }

            System.out.println("[" + requestId + "] Request: " + requestLine);

            // Parse method và path
            String[] parts = requestLine.split(" ");
            if (parts.length < 3) {
                sendBadRequest(socket);
                return;
            }

            String method = parts[0];
            String path = parts[1];
            String protocol = parts[2];

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

    // Xử lý GET request
    private void handleGET(Socket socket, String path) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        String html = "";

        if (path.equals("/") || path.equals("/index.html")) {
            // Trang chủ với nhiều tags để test
            html = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "    <title>HTTP Server Test Page</title>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <h1>Welcome to HTTP Server</h1>\n" +
                    "    <p>This is the first paragraph for testing.</p>\n" +
                    "    <p>This is the second paragraph.</p>\n" +
                    "    <p>This is the third paragraph with <span>inline span</span>.</p>\n" +
                    "    <div class=\"container\">\n" +
                    "        <div class=\"content\">First div content</div>\n" +
                    "        <div class=\"content\">Second div content</div>\n" +
                    "    </div>\n" +
                    "    <span>Standalone span element</span>\n" +
                    "    <span>Another span element</span>\n" +
                    "    <img src=\"image1.jpg\" alt=\"Image 1\"/>\n" +
                    "    <img src=\"image2.png\" alt=\"Image 2\"/>\n" +
                    "    <img src=\"image3.gif\" alt=\"Image 3\"/>\n" +
                    "    <p>Fourth paragraph at the end.</p>\n" +
                    "    <div>Third div element</div>\n" +
                    "</body>\n" +
                    "</html>";
        } else if (path.equals("/info")) {
            html = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head><title>Server Info</title></head>\n" +
                    "<body>\n" +
                    "    <h1>Server Information</h1>\n" +
                    "    <p>Server: MyHTTPServer/1.0</p>\n" +
                    "    <p>Java Version: " + System.getProperty("java.version") + "</p>\n" +
                    "    <p>Time: " + new Date() + "</p>\n" +
                    "    <div>Server is running on port 8080</div>\n" +
                    "</body>\n" +
                    "</html>";
        } else {
            // 404 Not Found
            send404(socket, path);
            return;
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

        // Tính content length giả định
        int contentLength = 500;
        if (path.equals("/")) {
            contentLength = 800;
        }

        // CHỈ GỬI HEADER, KHÔNG GỬI BODY
        out.print("HTTP/1.1 200 OK\r\n");
        out.print("Content-Type: text/html; charset=UTF-8\r\n");
        out.print("Content-Length: " + contentLength + "\r\n");
        out.print("Server: MyHTTPServer/1.0\r\n");
        out.print("Date: " + getHTTPDate() + "\r\n");
        out.print("Last-Modified: " + getHTTPDate() + "\r\n");
        out.print("Connection: close\r\n");
        out.print("\r\n");
        // KHÔNG có body!
        out.flush();

        System.out.println("[" + requestId + "] HEAD response (headers only, no body)");
    }

    // Xử lý POST request
    private void handlePOST(Socket socket, String path, String body) throws IOException {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        System.out.println("[" + requestId + "] POST body: " + body);

        // Parse body parameters
        Map<String, String> params = parsePostData(body);

        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>POST Response</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>POST Request Received Successfully</h1>\n" +
                "    <p>Server processed your POST request.</p>\n" +
                "    <div class=\"data-received\">\n" +
                "        <h2>Data Received:</h2>\n" +
                "        <p>Raw body: " + escapeHtml(body) + "</p>\n";

        if (!params.isEmpty()) {
            html += "        <div class=\"parameters\">\n" +
                    "            <h3>Parsed Parameters:</h3>\n";
            for (Map.Entry<String, String> entry : params.entrySet()) {
                html += "            <p><span>" + escapeHtml(entry.getKey()) +
                        "</span> = " + escapeHtml(entry.getValue()) + "</p>\n";
            }
            html += "        </div>\n";
        }

        html += "    </div>\n" +
                "    <div class=\"footer\">\n" +
                "        <p>Total parameters received: " + params.size() + "</p>\n" +
                "    </div>\n" +
                "    <img src=\"success.png\" alt=\"Success\"/>\n" +
                "</body>\n" +
                "</html>";

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

        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head><title>404 Not Found</title></head>\n" +
                "<body>\n" +
                "    <h1>404 - Page Not Found</h1>\n" +
                "    <p>The requested path <span>" + escapeHtml(path) + "</span> was not found on this server.</p>\n" +
                "    <div>Available paths:</div>\n" +
                "    <p>GET /</p>\n" +
                "    <p>GET /info</p>\n" +
                "    <p>HEAD /info</p>\n" +
                "    <p>POST /submit</p>\n" +
                "</body>\n" +
                "</html>";

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
