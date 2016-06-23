package iet.unipi.lorawan.appserver;

import iet.unipi.lorawan.Mote;
import iet.unipi.lorawan.appserver.ApplicationServerReceiver;
import iet.unipi.lorawan.messages.MACMessage;
import org.bouncycastle.util.encoders.Hex;

import java.net.Socket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;


public class Application {
    public final byte[] eui;
    public final String name;
    public final BlockingQueue<DownstreamMessage> messages = new LinkedBlockingQueue<>();
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
