package iet.unipi.lorawan.appserver;

import iet.unipi.lorawan.Constants;
import iet.unipi.lorawan.Mote;
import iet.unipi.lorawan.SimpleDateFormatter;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.*;


public class Application {
    private static final String FILE_HEADER = Constants.APPSERVER_LOG_PATH + "AS_handler_";

    // Properties
    public final String eui;
    public final String name;
    public final String address;
    public final int port;

    // Data structures
    public final BlockingQueue<DownstreamMessage> messages = new LinkedBlockingQueue<>();
    public final Map<String,Mote> motes; // key is eui
    public final Logger log;

    public Application(String eui, String name, String address, int port) {
        this.address = address;
        this.port = port;
        this.eui = eui;
        this.name = name;
        this.motes = new ConcurrentHashMap<>();

        // Init logger
        this.log = Logger.getLogger("Application Server: " + eui);
        this.log.setLevel(Level.INFO);

        try {
            FileHandler activityFile = new FileHandler(FILE_HEADER + eui + ".txt", true);
            activityFile.setFormatter(new SimpleDateFormatter());
            log.addHandler(activityFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Change ConsoleHandler behavior
        for (Handler handler: Logger.getLogger("").getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setFormatter(new SimpleDateFormatter());
            }
        }
    }
}
