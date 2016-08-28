package iet.unipi.lorawan.appserver;

import iet.unipi.lorawan.Mote;
import org.bouncycastle.util.encoders.Hex;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;


public class Application {
    // Properties
    public final byte[] eui;
    public final String name;
    public final String address;
    public final int port;

    // Data structures
    public final BlockingQueue<DownstreamMessage> messages = new LinkedBlockingQueue<>();
    public final Map<String,Mote> motes; // key is eui
    //public Socket socket;

    // Threads
    //public ApplicationServerSender sender;
    //public ApplicationServerReceiver receiver;


    public Application(String eui, String name, String address, int port) {
        this.address = address;
        this.port = port;
        this.eui = Hex.decode(eui);
        this.name = name;
        this.motes = new ConcurrentHashMap<>();
    }
}
