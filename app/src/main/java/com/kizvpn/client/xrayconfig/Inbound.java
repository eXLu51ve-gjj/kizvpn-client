package com.kizvpn.client.xrayconfig;

import java.util.Map;

public class Inbound {
    public String protocol;
    public String listen;
    public int port;
    public Map<String, Object> settings;
    public Sniffing sniffing;
}

