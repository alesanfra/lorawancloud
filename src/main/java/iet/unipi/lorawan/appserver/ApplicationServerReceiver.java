package iet.unipi.lorawan.appserver;

import iet.unipi.lorawan.SimpleDateFormatter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.*;


public class ApplicationServerReceiver implements Runnable {

    private final BufferedReader netServer;
    private final Application application;

    // Logger
    private static final Logger activity = Logger.getLogger("Application Server Receiver: activity");
    private static final String ACTIVITY_FILE = "data/AS_receiver_activity.txt";

    static {
        // Init logger
        activity.setLevel(Level.INFO);
        FileHandler activityFile = null;
        try {
            activityFile = new FileHandler(ACTIVITY_FILE, true);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            activityFile.setFormatter(new SimpleDateFormatter());
            activity.addHandler(activityFile);
        }

        // Change ConsoleHandler behavior
        for (Handler handler: Logger.getLogger("").getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setFormatter(new SimpleDateFormatter());
            }
        }
    }


    public ApplicationServerReceiver(Application application) {
        this.application = application;
        InputStream inputStream = null;
        try {
            inputStream = application.socket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            netServer = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.US_ASCII));
        }
    }


    @Override
    public void run() {
        while (true) {
            try {
                String message = netServer.readLine();
                activity.info(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
