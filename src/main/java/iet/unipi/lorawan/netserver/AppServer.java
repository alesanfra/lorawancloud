package iet.unipi.lorawan.netserver;

import org.json.JSONObject;

import java.io.IOException;
import java.net.Socket;


public class AppServer {
    public final String eui;
    public final String address;
    public final int port;


    public AppServer(String eui, String address, int port) {
        this.eui = eui;
        this.address = address;
        this.port = port;
    }

    public AppServer(JSONObject hello) {
        this.eui = hello.getString("appeui");
        this.address = hello.getString("addr");
        this.port = hello.getInt("port");
    }

    public Socket connect() throws IOException {
        return new Socket(address,port);
    }
}
