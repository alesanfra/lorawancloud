package iet.unipi.lorawan;

import iet.unipi.lorawan.appserver.ApplicationServerReceiver;
import org.bouncycastle.util.encoders.Hex;

import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by alessio on 12/05/16.
 */
public class Application {
    public final byte[] eui;
    public final String name;
    public final Map<String,Mote> motes;

    public Socket sockCS;

    public ApplicationServerReceiver receiver;

    public Application(String eui, String name) {
        this.eui = Hex.decode(eui);
        this.name = name;
        this.motes = new ConcurrentHashMap<>();
    }
}
