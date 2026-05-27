package org.example.java_lab8;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * Signaling Server для удалённого подключения.
 * Запускается на VPS или компьютере с белым IP.
 * Не передаёт аудио, только помогает клиентам найти друг друга.
 */
public class RemoteSignalingServer {
    private static final int PORT = AppConfig.REMOTE_SERVER_PORT;
    private static final ConcurrentHashMap<String, PeerRecord> registry = new ConcurrentHashMap<>();

    // PeerRecord хранит информацию о зарегистрированном клиенте
    private static class PeerRecord {
        final String nickname;
        final String publicIp;
        final int tcpPort;
        final int udpPort;
        final long registeredAt;

        PeerRecord(String nickname, String publicIp, int tcpPort, int udpPort) {
            this.nickname = nickname;
            this.publicIp = publicIp;
            this.tcpPort = tcpPort;
            this.udpPort = udpPort;
            this.registeredAt = System.currentTimeMillis();
        }

        boolean isAlive() {
            // Считаем клиента активным, если зарегистрировался менее 5 минут назад
            return System.currentTimeMillis() - registeredAt < 5 * 60 * 1000;
        }
    }

    public static void main(String[] args) {
        System.out.println("🚀 Remote Signaling Server запущен на порту " + PORT);
        System.out.println("   Ожидание подключений...");

        // Фоновая задача: очистка устаревших записей
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
        cleaner.scheduleAtFixedRate(() -> {
            registry.entrySet().removeIf(e -> !e.getValue().isAlive());
            System.out.println("🧹 Очистка: в реестре " + registry.size() + " активных узлов");
        }, 60, 60, TimeUnit.SECONDS);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket client = serverSocket.accept();
                String clientInfo = client.getInetAddress().getHostAddress() + ":" + client.getPort();
                System.out.println("🔌 Подключение от: " + clientInfo);
                new Thread(() -> handleClient(client, clientInfo), "RemoteHandler-" + clientInfo).start();
            }
        } catch (IOException e) {
            System.err.println("❌ Ошибка сервера: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleaner.shutdown();
        }
    }

    private static void handleClient(Socket socket, String clientInfo) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length < 1) continue;

                String cmd = parts[0].trim().toUpperCase();

                switch (cmd) {
                    case "REGISTER" -> {
                        // REGISTER\tNick\tPublicIP\tTcpPort\tUdpPort
                        if (parts.length >= 5) {
                            String nick = parts[1];
                            String publicIp = parts[2];
                            int tcpPort = Integer.parseInt(parts[3]);
                            int udpPort = Integer.parseInt(parts[4]);

                            registry.put(nick, new PeerRecord(nick, publicIp, tcpPort, udpPort));
                            System.out.println("✅ REGISTER: " + nick + " → " + publicIp + ":" + tcpPort);
                            out.println("OK\tRegistered as " + nick);
                        } else {
                            out.println("ERROR\tInvalid REGISTER format");
                        }
                    }

                    case "LOOKUP" -> {
                        // LOOKUP\tNick
                        if (parts.length >= 2) {
                            String targetNick = parts[1];
                            PeerRecord record = registry.get(targetNick);

                            if (record != null && record.isAlive()) {
                                // FOUND\tNick\tPublicIP\tTcpPort\tUdpPort
                                out.println("FOUND\t" + record.nickname + "\t" + record.publicIp + "\t" + record.tcpPort + "\t" + record.udpPort);
                                System.out.println("🔍 LOOKUP: " + targetNick + " → найден");
                            } else {
                                out.println("NOT_FOUND\t" + targetNick);
                                System.out.println("❌ LOOKUP: " + targetNick + " → не найден");
                            }
                        } else {
                            out.println("ERROR\tInvalid LOOKUP format");
                        }
                    }

                    case "UNREGISTER" -> {
                        // UNREGISTER\tNick
                        if (parts.length >= 2) {
                            String nick = parts[1];
                            registry.remove(nick);
                            System.out.println("🗑️ UNREGISTER: " + nick);
                            out.println("OK\tUnregistered");
                        }
                    }

                    case "PING" -> out.println("PONG");

                    default -> out.println("ERROR\tUnknown command: " + cmd);
                }
            }
        } catch (IOException e) {
            System.out.println("⚠️ Клиент отключился: " + clientInfo);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}