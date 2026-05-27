package org.example.java_lab8;

import javax.sound.sampled.*;
import java.net.*;

public class AudioReceiver implements Runnable {
    private final int udpPort;
    private volatile boolean running = false;
    private DatagramSocket socket;
    private SourceDataLine line;

    public AudioReceiver(int udpPort) {
        this.udpPort = udpPort;
    }

    @Override
    public void run() {
        try {
            AudioFormat format = new AudioFormat(
                    AppConfig.AUDIO_SAMPLE_RATE, AppConfig.AUDIO_SAMPLE_SIZE,
                    AppConfig.AUDIO_CHANNELS, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            socket = new DatagramSocket(udpPort);
            byte[] buffer = new byte[AppConfig.AUDIO_BUFFER_SIZE];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                line.write(packet.getData(), 0, packet.getLength());
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            stop();
        }
    }

    public void start() {
        running = true;
        new Thread(this, "AudioReceiver").start();
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        if (line != null) { line.drain(); line.close(); }
    }
}