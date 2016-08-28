package iet.unipi.lorawan.netserver;


import iet.unipi.lorawan.Constants;
import iet.unipi.lorawan.MoteCollection;
import iet.unipi.lorawan.SimpleDateFormatter;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;

public class NetworkServerListener implements Runnable {

    // Sockets
    private final ServerSocket listener;

    // Hashmap
    private final MoteCollection motes;
    private final Map<String,AppServer> appServers;

    // Executors
    private ExecutorService executor = Executors.newCachedThreadPool();

    //Logger
    private static final String FILE_LOGGER = "data/NS_listener.txt";
    private final Logger activity;

    static {
        // Change ConsoleHandler behavior
        for (Handler handler: Logger.getLogger("").getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setFormatter(new SimpleDateFormatter());
            }
        }
    }

    public NetworkServerListener(int port, MoteCollection motes, Map<String, AppServer> appServers) throws IOException {
        this.motes = motes;
        this.appServers = appServers;
        this.listener = new ServerSocket(port);

        // Init logger
        this.activity = Logger.getLogger("Network Server Listener");
        activity.setLevel(Level.INFO);

        try {
            FileHandler activityFile = new FileHandler(FILE_LOGGER);
            activityFile.setFormatter(new SimpleDateFormatter());
            activity.addHandler(activityFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        activity.info("Start Network Server Listener");
        while (true) {
            try {
                // Accept new Application Server
                Socket socket = listener.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                String line = in.readLine();
                activity.info("New application: " + line);

                if (line == null) {
                    continue;
                }

                // Read Application EUI
                JSONObject message = new JSONObject(line);
                AppServer appServer = new AppServer(message.getJSONObject("appserver"));


                if (appServer.eui.length() != Constants.EUI_LENGTH) {
                    // AppEUI not valid
                    activity.info("Invalid App EUI: " + appServer.eui);
                    socket.close();
                    continue;
                }


                appServers.put(appServer.eui,appServer);

                // Start Enqueuer
                try {
                    executor.execute(new NetworkServerEnqueuer(appServer.eui, socket, motes));
                } catch (IOException e) {
                    e.printStackTrace();
                    appServers.remove(appServer.eui);
                    socket.close();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
