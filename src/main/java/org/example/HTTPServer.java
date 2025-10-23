package org.example;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class HTTPServer {
    private static final int PORT = 8080;
    private static int requestCount = 0;

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("========================================");
            System.out.println("HTTP Server đang chạy ở port " + PORT);
            System.out.println("========================================");
            System.out.println("Test với Postman:");
            System.out.println("  GET:  http://localhost:8080/");
            System.out.println("  HEAD: http://localhost:8080/info");
            System.out.println("  POST: http://localhost:8080/submit");
            System.out.println("========================================\n");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                requestCount++;
                System.out.println("[" + requestCount + "] Nhận kết nối từ: " +
                        clientSocket.getInetAddress().getHostAddress());

                // Xử lý mỗi request trong thread riêng
                new Thread(new RequestHandler(clientSocket, requestCount)).start();
            }
        } catch (IOException e) {
            System.err.println("Lỗi Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
