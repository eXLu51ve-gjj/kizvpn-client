package com.kizvpn.client.xrayconfig;

public class WireguardPeer {
    public String endpoint;
    public String publicKey;
    public String preSharedKey;
    public String[] allowedIPs = new String[0];
    public int keepAlive = 60;
}

