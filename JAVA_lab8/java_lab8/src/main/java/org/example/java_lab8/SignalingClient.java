package org.example.java_lab8;

import java.io.*;
import java.net.Socket;

public class SignalingClient {
    private Socket socket;
    private PrintWriter writer;

    public SignalingClient() {}

    public SignalingClient(Socket socket, PrintWriter writer) {
        this.socket = socket;
        this.writer = writer;
    }

    public void connect(String ip, int port) throws IOException {
        socket = new Socket(ip, port);
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    public void sendCommand(String command) {
        if (writer != null) writer.println(command);
    }

    public void disconnect() {
        try { if (socket != null) socket.close(); }
        catch (IOException ignored) {}
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }
}