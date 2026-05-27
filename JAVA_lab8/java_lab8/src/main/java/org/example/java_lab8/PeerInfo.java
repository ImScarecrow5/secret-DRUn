package org.example.java_lab8;

import java.time.Instant;

public class PeerInfo {
    private final String nickname;
    private final String ip;
    private final int tcpPort;
    private Instant lastSeen;

    public PeerInfo(String nickname, String ip, int tcpPort) {
        this.nickname = nickname;
        this.ip = ip;
        this.tcpPort = tcpPort;
        this.lastSeen = Instant.now();
    }

    public String getNickname() { return nickname; }
    public String getIp()       { return ip; }
    public int getTcpPort()     { return tcpPort; }
    public Instant getLastSeen(){ return lastSeen; }
    public void updateLastSeen(){ this.lastSeen = Instant.now(); }

    @Override
    public String toString() { return nickname + " (" + ip + ":" + tcpPort + ")"; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PeerInfo p)) return false;
        return ip.equals(p.ip) && tcpPort == p.tcpPort;
    }

    @Override
    public int hashCode() { return ip.hashCode() * 31 + tcpPort; }
}