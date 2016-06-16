package iet.unipi.lorawan.appserver;

import iet.unipi.lorawan.Mote;
import iet.unipi.lorawan.appserver.ApplicationServerReceiver;
import org.bouncycastle.util.encoders.Hex;

import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class Application {
    public final byte[] eui;
    public final String name;

    public Socket socket;

    public final Map<String,Mote> motes;

    public ApplicationServerSender sender;
    public ApplicationServerReceiver receiver;

    public Application(String eui, String name) {
        this.eui = Hex.decode(eui);
        this.name = name;
        this.motes = new ConcurrentHashMap<>();
    }
}
