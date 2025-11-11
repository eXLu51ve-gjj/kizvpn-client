package com.kizvpn.client.xrayconfig;

import java.util.ArrayList;
import java.util.List;

public class RoutingRule {
    public String type = "field";
    public List<String> domain;
    public List<String> ip;
    public String outboundTag;
    public String network;
    public List<String> source;
    public List<String> port;
    public List<String> sourcePort;
    public List<String> user;
    public List<String> inboundTag;
    public List<String> protocol;
    public String attrs;
}

