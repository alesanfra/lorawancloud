package iet.unipi.lorawan.netserver;


import iet.unipi.lorawan.Mote;
import iet.unipi.lorawan.MoteCollection;
import iet.unipi.lorawan.SimpleDateFormatter;
import iet.unipi.lorawan.appserver.Application;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.*;

public class NetworkServerAppHandler implements Runnable {
    private static final String FILE_HEADER = "data/NS_app_handler_";

    private final MoteCollection motes;
    private final String appEui;
    private BufferedReader appServer;

    private final Logger activity;

    static {
        // Change ConsoleHandler behavior
        for (Handler handler: Logger.getLogger("").getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setFormatter(new SimpleDateFormatter());
            }
        }
    }

    public NetworkServerAppHandler(String appEui, Socket socket, MoteCollection motes) throws IOException {
        this.appEui = appEui;
        this.motes = motes;
        appServer = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));

        // Init logger
        this.activity = Logger.getLogger("Network Server App Handler: " + appEui);
        activity.setLevel(Level.INFO);

        try {
            FileHandler activityFile = new FileHandler(FILE_HEADER + appEui + ".txt", true);
            activityFile.setFormatter(new SimpleDateFormatter());
            activity.addHandler(activityFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void run() {
        activity.info("Start AppHandler: " + appEui);
        try {
            while (true) {
                String line = appServer.readLine();

                if (line == null) {
                    activity.info(appEui + ": socket closed");
                    return;
                }

                String devEUI = new JSONObject(line).getJSONObject("app").getString("moteeui");
                Mote mote = motes.getByEui(devEUI);

                try {
                    mote.messages.put(line);
                    activity.info("Enqueued message for mote: " + devEUI);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
