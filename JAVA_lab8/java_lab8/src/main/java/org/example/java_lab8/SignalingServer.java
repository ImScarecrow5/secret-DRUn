package org.example.java_lab8;

import java.io.*;
import java.net.*;
import java.util.function.BiConsumer;

public class SignalingServer implements Runnable {
    private final int tcpPort;
    private final BiConsumer<String, Socket> onCommand;
    private volatile boolean running = false;
    private ServerSocket serverSocket;

    public SignalingServer(int tcpPort, BiConsumer<String, Socket> onCommand) {
        this.tcpPort = tcpPort;
        this.onCommand = onCommand;
    }

    public void start() {
        running = true;
        new Thread(this, "SignalingServer").start();
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(tcpPort);
            while (running) {
                Socket client = serverSocket.accept();
                new Thread(() -> handleClient(client), "SignalingClient").start();
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }

    private void handleClient(Socket client) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                onCommand.accept(line, client);
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); }
        catch (IOException ignored) {}
    }
}