package org.example.java_lab8;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.io.*;
import java.net.*;
import java.util.List;

public class HelloController {
    // Настройки
    @FXML private TextField nicknameField;
    @FXML private TextField tcpPortField;
    @FXML private TextField udpPortField;
    @FXML private Button startBtn;

    // Ручной вызов
    @FXML private TextField remoteIpField;
    @FXML private TextField remotePortField;
    @FXML private Button callBtn;
    @FXML private Button endCallBtn;

    // Push-to-Talk (Вариант: удержание кнопки)
    @FXML private Button pushToTalkBtn;

    // LAN список
    @FXML private ListView<PeerInfo> peersListView;
    @FXML private Button callSelectedBtn;

    // Статус и лог
    @FXML private Label statusLabel;
    @FXML private TextArea logArea;

    // Сетевые компоненты
    private SignalingServer signalingServer;
    private SignalingClient signalingClient;
    private AudioSender audioSender;
    private AudioReceiver audioReceiver;
    private LanDiscovery lanDiscovery;

    private String localIp;
    private int myTcpPort, myUdpPort;
    private volatile boolean inCall = false;
    private PrintWriter activePeerWriter;

    @FXML
    public void initialize() {
        tcpPortField.setText("5000");
        udpPortField.setText("5001");
        nicknameField.setText("User");

        callBtn.setDisable(true);
        endCallBtn.setDisable(true);
        pushToTalkBtn.setDisable(true);
        callSelectedBtn.setDisable(true);

        peersListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, peer) ->
                        callSelectedBtn.setDisable(peer == null || inCall));
    }

    @FXML
    private void onStart() {
        try {
            myTcpPort = Integer.parseInt(tcpPortField.getText().trim());
            myUdpPort = Integer.parseInt(udpPortField.getText().trim());
            String nick = nicknameField.getText().trim();
            if (nick.isEmpty()) nick = "User";
            localIp = InetAddress.getLocalHost().getHostAddress();

            signalingServer = new SignalingServer(myTcpPort, this::handleCommand);
            signalingServer.start();

            lanDiscovery = new LanDiscovery(nick, localIp, myTcpPort, this::updatePeers);
            lanDiscovery.start();

            setStatus("● Ожидание  |  " + localIp + ":" + myTcpPort);
            startBtn.setDisable(true);
            callBtn.setDisable(false);
            log("Запущено. Ник: " + nick + "  TCP: " + myTcpPort + "  UDP: " + myUdpPort);
        } catch (Exception e) {
            showError("Ошибка: " + e.getMessage());
        }
    }

    @FXML
    private void onCall() {
        String ip = remoteIpField.getText().trim();
        String port = remotePortField.getText().trim();
        if (ip.isEmpty() || port.isEmpty()) { showError("Введите IP и порт"); return; }
        try { initiateCall(ip, Integer.parseInt(port)); }
        catch (NumberFormatException e) { showError("Неверный порт"); }
    }

    @FXML
    private void onCallSelected() {
        PeerInfo peer = peersListView.getSelectionModel().getSelectedItem();
        if (peer != null) initiateCall(peer.getIp(), peer.getTcpPort());
    }

    private void initiateCall(String ip, int port) {
        new Thread(() -> {
            try {
                Socket socket = new Socket(ip, port);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));

                signalingClient = new SignalingClient(socket, writer);
                writer.println("CALL_START\t" + myUdpPort);

                Platform.runLater(() -> {
                    setStatus("● Звоним → " + ip + ":" + port);
                    callBtn.setDisable(true);
                    endCallBtn.setDisable(false);
                });
                log("Исходящий вызов → " + ip + ":" + port);

                String line;
                while ((line = reader.readLine()) != null) {
                    handleCommand(line, socket);
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Не удалось подключиться: " + e.getMessage());
                    callBtn.setDisable(false);
                    endCallBtn.setDisable(true);
                });
            }
        }, "CallInitiator").start();
    }

    private void handleCommand(String command, Socket peerSocket) {
        if (command == null || command.isBlank()) return;
        String[] parts = command.split("\t");
        switch (parts[0]) {
            case "CALL_START" -> {
                int remoteUdp = parts.length > 1 ? safeInt(parts[1], myUdpPort + 10) : myUdpPort + 10;
                String remoteIp = peerSocket.getInetAddress().getHostAddress();
                try {
                    activePeerWriter = new PrintWriter(peerSocket.getOutputStream(), true);
                    activePeerWriter.println("CALL_ACCEPTED\t" + myUdpPort);
                } catch (IOException ignored) {}
                Platform.runLater(() -> {
                    setStatus("● Разговор с " + remoteIp);
                    endCallBtn.setDisable(false);
                    pushToTalkBtn.setDisable(false);   // Push-to-Talk: разблокируем кнопку
                    callBtn.setDisable(true);
                });
                startAudio(remoteIp, remoteUdp);
                log("Входящий вызов от " + remoteIp + " — принят");
            }
            case "CALL_ACCEPTED" -> {
                int remoteUdp = parts.length > 1 ? safeInt(parts[1], myUdpPort + 10) : myUdpPort + 10;
                String remoteIp = peerSocket.getInetAddress().getHostAddress();
                Platform.runLater(() -> {
                    setStatus("● Разговор с " + remoteIp);
                    pushToTalkBtn.setDisable(false);   // Push-to-Talk: разблокируем кнопку
                });
                startAudio(remoteIp, remoteUdp);
                log("Вызов принят — аудио запущено");
            }
            case "CALL_END" -> {
                stopAudio();
                Platform.runLater(() -> {
                    setStatus("● Разговор завершён");
                    endCallBtn.setDisable(true);
                    pushToTalkBtn.setDisable(true);
                    pushToTalkBtn.setText("🎤 Удерживайте для разговора");
                    pushToTalkBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; " +
                            "-fx-background-radius: 6; -fx-padding: 12; -fx-font-weight: bold; " +
                            "-fx-cursor: hand; -fx-border-width: 2; -fx-border-color: transparent;");
                    callBtn.setDisable(false);
                });
                log("Собеседник завершил вызов");
            }
        }
    }

    @FXML
    private void onEndCall() {
        isPushActive = false;
        if (signalingClient != null && signalingClient.isConnected())
            signalingClient.sendCommand("CALL_END");
        if (activePeerWriter != null) { activePeerWriter.println("CALL_END"); activePeerWriter = null; }
        stopAudio();
        setStatus("Ожидание");
        endCallBtn.setDisable(true);
        pushToTalkBtn.setDisable(true);
        pushToTalkBtn.setText("🎤 Удерживайте для разговора");
        pushToTalkBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-padding: 12; -fx-font-weight: bold; " +
                "-fx-cursor: hand; -fx-border-width: 2; -fx-border-color: transparent;");
        callBtn.setDisable(false);
        log("Вызов завершён");
    }
    private boolean isPushActive = false;
    // ── Push-to-Talk: удержание кнопки ──────────────────────────────────────
    @FXML
    private void handlePushStart() {
        if (audioSender != null && inCall && !isPushActive) {
            audioSender.setTalking(true);
            isPushActive = true;
            pushToTalkBtn.setText("ГОВОРИТЕ...");
            log("🎤 Микрофон активен (удержание)");
        }
    }

    @FXML
    private void handlePushEnd() {
        if (audioSender != null && isPushActive){
            audioSender.setTalking(false);
            isPushActive = false;
            pushToTalkBtn.setText("Удерживайте для разговора");
            log("🔇 Микрофон выключен");
        }
    }

    // ── Аудио ─────────────────────────────────────────────────────────────────
    private void startAudio(String remoteIp, int remoteUdp) {
        if (inCall) return;
        inCall = true;
        audioSender = new AudioSender(remoteIp, remoteUdp);
        audioSender.start();
        audioReceiver = new AudioReceiver(myUdpPort);
        audioReceiver.start();
    }

    private void stopAudio() {
        inCall = false;
        if (audioSender != null)   { audioSender.stop();   audioSender = null; }
        if (audioReceiver != null) { audioReceiver.stop(); audioReceiver = null; }
    }

    // ── Peers ─────────────────────────────────────────────────────────────────
    private void updatePeers(List<PeerInfo> peers) {
        Platform.runLater(() -> {
            peersListView.getItems().setAll(peers);
            PeerInfo sel = peersListView.getSelectionModel().getSelectedItem();
            callSelectedBtn.setDisable(sel == null || inCall);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void setStatus(String text) {
        Platform.runLater(() -> statusLabel.setText(text));
    }

    private void log(String text) {
        Platform.runLater(() -> logArea.appendText(text + "\n"));
    }

    private void showError(String msg) {
        Platform.runLater(() ->
                new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait());
    }

    private int safeInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    public void shutdown() {
        stopAudio();
        if (signalingServer != null) signalingServer.stop();
        if (signalingClient != null) signalingClient.disconnect();
        if (lanDiscovery != null)    lanDiscovery.stop();
    }
}