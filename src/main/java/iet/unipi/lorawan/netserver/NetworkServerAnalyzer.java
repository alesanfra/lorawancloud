package iet.unipi.lorawan.netserver;


import iet.unipi.lorawan.Mote;
import iet.unipi.lorawan.SimpleDateFormatter;
import iet.unipi.lorawan.messages.FrameMessage;
import iet.unipi.lorawan.messages.MacMessage;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.logging.*;

public class NetworkServerAnalyzer implements Runnable {

    private static final Logger activity = Logger.getLogger("Network Server Analyzer: activity");
    private static final String ACTIVITY_FILE = "data/NS_downstream_forwarder_activity.txt";


    static {
        // Init logger
        activity.setLevel(Level.INFO);
        FileHandler activityFile;
        try {
            activityFile = new FileHandler(ACTIVITY_FILE, true);
            activityFile.setFormatter(new SimpleDateFormatter());
            activity.addHandler(activityFile);

            // Change ConsoleHandler behavior
            for (Handler handler : Logger.getLogger("").getHandlers()) {
                if (handler instanceof ConsoleHandler) {
                    handler.setFormatter(new SimpleDateFormatter());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final BlockingQueue<JSONObject> messages;

    public NetworkServerAnalyzer(BlockingQueue<JSONObject> messages) {
        this.messages = messages;
    }

    @Override
    public void run() {


        while (true) {
            try {
                JSONObject message = messages.take();


                if (message.getInt("stat") != 1) {
                    activity.warning("CRC not valid, skip packet");
                    return;
                }

                long timestamp = message.getLong("tmst");

                MacMessage mm = new MacMessage(message.getString("data"));
                FrameMessage fm = new FrameMessage(mm);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

    }
}
