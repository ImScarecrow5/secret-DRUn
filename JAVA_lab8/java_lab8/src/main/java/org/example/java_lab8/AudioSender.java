package org.example.java_lab8;
import javax.sound.sampled.*;
import java.net.*;

public class AudioSender implements Runnable {
    private final String remoteIp;
    private final int remoteUdpPort;
    private volatile boolean running = false;
    private volatile boolean isTalking = false;
    private TargetDataLine line;

    public AudioSender(String remoteIp, int remoteUdpPort) {
        this.remoteIp = remoteIp;
        this.remoteUdpPort = remoteUdpPort;
    }

    @Override
    public void run() {
        try {
            AudioFormat format = new AudioFormat(
                    AppConfig.AUDIO_SAMPLE_RATE, AppConfig.AUDIO_SAMPLE_SIZE,
                    AppConfig.AUDIO_CHANNELS, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            DatagramSocket socket = new DatagramSocket();
            InetAddress address = InetAddress.getByName(remoteIp);

            // 🔥 Увеличиваем буфер для плавности: 2048 байт (~256 мс аудио при 8kHz/16bit/mono)
            byte[] buffer = new byte[2048];

            while (running) {
                // Всегда читаем микрофон — это держит буфер аудиокарты в актуальном состоянии
                int bytesRead = line.read(buffer, 0, buffer.length);

                if (bytesRead > 0 && isTalking) {
                    // Отправляем только если кнопка зажата
                    DatagramPacket packet = new DatagramPacket(buffer, bytesRead, address, remoteUdpPort);
                    socket.send(packet);
                }
                // 🔥 Убрали Thread.sleep() — теперь цикл работает непрерывно, без рывков
            }
            socket.close();
        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            stop();
        }
    }

    public void start() { running = true; new Thread(this, "AudioSender").start(); }

    public void stop() {
        running = false;
        if (line != null) { line.stop(); line.close(); }
    }

    public void setTalking(boolean talking) { this.isTalking = talking; }
    public boolean isTalking() { return isTalking; }
}