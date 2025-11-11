package com.kizvpn.client.xrayconfig;

import java.util.List;

public class DNSServer {
    public String tag;
    public String address;
    public int port;
    public List<String> domains;
    public boolean skipFallback = false;
}

