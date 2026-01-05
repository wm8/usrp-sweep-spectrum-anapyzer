package jspectrumanalyzer.core;

import com.google.gson.annotations.SerializedName;

public class MainAddress {
    @SerializedName("address")
    public String address;

    @SerializedName("port")
    public int port;

    @SerializedName("servergadalkaId")
    public String serverGadalkaId;

    public MainAddress(String address, int port, String serverGadalkaId) {
        this.address = address;
        this.port = port;
        this.serverGadalkaId = serverGadalkaId;
    }
}
