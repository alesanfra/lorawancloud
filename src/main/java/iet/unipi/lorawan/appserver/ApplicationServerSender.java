package iet.unipi.lorawan.appserver;


import iet.unipi.lorawan.SimpleDateFormatter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.logging.*;

public class ApplicationServerSender implements Runnable {

    private final Application application;
    private final PrintWriter netServer;

    // Logger
    private static final Logger activity = Logger.getLogger("Application Server Sender: activity");
    private static final String ACTIVITY_FILE = "data/AS_sender_activity.txt";

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

    public ApplicationServerSender(Application application) throws IOException {
        this.application = application;
        this.netServer = new PrintWriter(new OutputStreamWriter(application.socket.getOutputStream(), StandardCharsets.US_ASCII));
    }

    @Override
    public void run() {
        try {
            while (true) {
                DownstreamMessage message = application.messages.take();
                netServer.println(message.toJSONString());
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
