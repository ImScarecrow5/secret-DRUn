package org.example.java_lab8;

import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class LanDiscovery {
    private final String nickname;
    private final int tcpPort;
    private String localIp;
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private final Consumer<List<PeerInfo>> onPeersUpdated;

    private MulticastSocket socket;
    private InetAddress group;
    private NetworkInterface networkInterface;
    private volatile boolean running = false;
    private ScheduledExecutorService scheduler;

    public LanDiscovery(String nickname, String localIp, int tcpPort,
                        Consumer<List<PeerInfo>> onPeersUpdated) {
        this.nickname = nickname;
        this.localIp = localIp;
        this.tcpPort = tcpPort;
        this.onPeersUpdated = onPeersUpdated;
    }

    public void start() throws Exception {
        group = InetAddress.getByName(AppConfig.MULTICAST_GROUP);
        networkInterface = findNetworkInterface();

        if (networkInterface != null) {
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                    localIp = addr.getHostAddress();
                    break;
                }
            }
        }

        System.out.println("[LanDiscovery] Интерфейс: " +
                (networkInterface != null ? networkInterface.getName() : "null") +
                ", IP: " + localIp);

        socket = new MulticastSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(AppConfig.MULTICAST_PORT));
        if (networkInterface != null) socket.setNetworkInterface(networkInterface);
        socket.setLoopbackMode(false);
        socket.setTimeToLive(4);
        if (networkInterface != null) {
            Enumeration<InetAddress> addrs = networkInterface.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress a = addrs.nextElement();
                if (a instanceof Inet4Address) { socket.setInterface(a); break; }
            }
        }
        socket.joinGroup(new InetSocketAddress(group, AppConfig.MULTICAST_PORT), networkInterface);
        running = true;

        new Thread(this::listenLoop, "LanDiscovery-Listener").start();
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::sendHello, 0, AppConfig.HELLO_INTERVAL_MS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::checkHeartbeats, 5, 5, TimeUnit.SECONDS);
    }

    private NetworkInterface findNetworkInterface() {
        try {
            for (String name : new String[]{"en0", "en1", "en2", "eth0", "wlan0"}) {
                NetworkInterface ni = NetworkInterface.getByName(name);
                if (ni != null && ni.isUp() && !ni.isLoopback() && ni.supportsMulticast()) {
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress a = addrs.nextElement();
                        if (a instanceof Inet4Address && !a.isLoopbackAddress()) return ni;
                    }
                }
            }
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                String n = ni.getName().toLowerCase();
                if (n.startsWith("utun") || n.startsWith("awdl") || n.startsWith("llw") ||
                        n.startsWith("bridge") || n.startsWith("lo") || n.startsWith("gif") ||
                        n.startsWith("stf") || n.startsWith("anpi")) continue;
                if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return ni;
                }
            }
        } catch (SocketException e) { e.printStackTrace(); }
        return null;
    }

    private void sendHello() {
        try {
            String msg = "HELLO\t" + nickname + "\t" + localIp + "\t" + tcpPort;
            byte[] data = msg.getBytes();
            socket.send(new DatagramPacket(data, data.length, group, AppConfig.MULTICAST_PORT));
        } catch (Exception e) { if (running) e.printStackTrace(); }
    }

    private void listenLoop() {
        byte[] buffer = new byte[256];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                handleMessage(new String(packet.getData(), 0, packet.getLength()));
            } catch (Exception e) { if (running) e.printStackTrace(); }
        }
    }

    private void handleMessage(String msg) {
        String[] parts = msg.split("\t");
        if (parts.length < 4 || !parts[0].equals("HELLO")) return;
        String nick = parts[1], ip = parts[2];
        int port;
        try { port = Integer.parseInt(parts[3].trim()); } catch (Exception e) { return; }
        if (ip.equals(localIp) && port == tcpPort) return;

        String key = ip + ":" + port;
        PeerInfo existing = peers.get(key);
        if (existing != null) { existing.updateLastSeen(); }
        else { peers.put(key, new PeerInfo(nick, ip, port)); notifyUpdated(); }
    }

    private void checkHeartbeats() {
        boolean changed = false;
        var it = peers.entrySet().iterator();
        while (it.hasNext()) {
            PeerInfo p = it.next().getValue();
            if (Instant.now().toEpochMilli() - p.getLastSeen().toEpochMilli() > AppConfig.PEER_TIMEOUT_MS) {
                it.remove(); changed = true;
            }
        }
        if (changed) notifyUpdated();
    }

    private void notifyUpdated() {
        if (onPeersUpdated != null) onPeersUpdated.accept(new ArrayList<>(peers.values()));
    }

    public void stop() {
        running = false;
        if (scheduler != null) scheduler.shutdownNow();
        try {
            if (socket != null) {
                socket.leaveGroup(new InetSocketAddress(group, AppConfig.MULTICAST_PORT), networkInterface);
                socket.close();
            }
        } catch (Exception ignored) {}
    }
}