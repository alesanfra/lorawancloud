package iet.unipi.lorawan;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;

/**
 * Created by alessio on 17/05/16.
 */
public class NetworkServerSender implements Runnable {

    private static final int BUFFER_LEN = 2400;
    private static final Logger activity = Logger.getLogger("Network Server Sender: activity");
    private static final String ACTIVITY_FILE = "data/NS_sender_activity.txt";

    private final int port;
    private final Map<String,LoraMote> motes;
    private final Map<String,InetSocketAddress> gateways;
    private final Socket sockAS;


    private DatagramSocket toGateway;

    public NetworkServerSender(int port, Map<String,LoraMote> motes, Map<String,InetSocketAddress> gateways, Socket sockAS) {
        this.port = port;
        this.motes = motes;
        this.gateways = gateways;
        this.sockAS = sockAS;

        /**
         * Init Logger
         */

        activity.setLevel(Level.INFO);
        FileHandler activityFile = null;
        try {
            activityFile = new FileHandler(ACTIVITY_FILE, true);
            activityFile.setFormatter(new SimpleDateFormatter());
            activity.addHandler(activityFile);

            // Change ConsoleHandler behavior
            for (Handler handler: Logger.getLogger("").getHandlers()) {
                if (handler instanceof ConsoleHandler) {
                    handler.setFormatter(new SimpleDateFormatter());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void run() {


    }
}
