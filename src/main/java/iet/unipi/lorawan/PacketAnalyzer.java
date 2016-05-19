package iet.unipi.lorawan;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


public class PacketAnalyzer extends Thread {
    private final BlockingQueue<Message> messages;
    private final Map<String, LoraMote> motes;
    //private final Map<String, Map<Integer, Experiment>> testData = new HashMap<>();
    //private final Map<String, Integer> lastTest = new HashMap<>();

    // Log
    //private final static Logger logPackets = Logger.getLogger("Packet analyzer: received packets");
    //private static final String LOG_FILE = "data/received.txt";
    private final static Logger activity = Logger.getLogger("Packet analyzer: activity");
    private static final String ACTIVITY_FILE = "data/analyzer-activity.txt";


    /**
     *
     * @param messages
     */

    public PacketAnalyzer(BlockingQueue<Message> messages, Map<String,LoraMote> motes) {
        this.messages = messages;
        this.motes = motes;

        /*
        // Init logger for received packets
        logPackets.setUseParentHandlers(false);
        logPackets.setLevel(Level.INFO);
        try {
            FileHandler logPacketsFile = new FileHandler(LOG_FILE, true);
            logPacketsFile.setFormatter(new LogFormatter());
            logPackets.addHandler(logPacketsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }*/


        // Init logger for server activity
        activity.setLevel(Level.INFO);
        try {
            FileHandler activityFile = new FileHandler(ACTIVITY_FILE, true);
            activityFile.setFormatter(new SimpleDateFormatter());
            activity.addHandler(activityFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     *
     */

    @Override
    public void run() {
        activity.info("Starting PacketAnalyzer");
        while (true) {
            try {
                Message msg = messages.take();
                String devAddr = msg.devAddress;

                // Parse payload    | testN | conf | lat | long |
                if (msg.payload.length < 10) {
                    activity.warning("INVALID payload: length < 10 bytes");
                    break;
                }
                ByteBuffer bb = ByteBuffer.wrap(msg.payload).order(ByteOrder.LITTLE_ENDIAN);
                float latitude = bb.getFloat();
                float longitude = bb.getFloat();
                byte testN = bb.get();
                byte configuration = bb.get();

                LoraMote mote = motes.get(devAddr);

                Experiment exp = mote.experiments[testN];

                // If new test, init Experiment and print last
                if (exp == null) {
                    exp = new Experiment(devAddr,testN);
                    mote.experiments[testN] = exp;


                    if (mote.lastTest >= 0) {
                        Experiment lastExperiment = mote.experiments[mote.lastTest];

                        //activity.info("nessun last experiment");

                        if (lastExperiment.lastConf >= 0) {
                            activity.info(lastExperiment.printLastConfiguration());
                        }
                        activity.info(lastExperiment.print());
                    }
                    mote.lastTest = testN;
                }


                // check configuration, if new one print the old one
                if (configuration != exp.lastConf) {
                    if (exp.lastConf >= 0) {
                        //activity.info("cambiata configurazione");
                        activity.info(exp.printLastConfiguration());
                    }
                    exp.lastConf = configuration;
                }

                // Aggiungo pacchetto all'esperimento
                exp.addPacket(configuration,latitude,longitude);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Print coordinates
     * @param decrypted binary encoded coordinates (two 32-bit float)
     */

    private void printCoordinates(byte[] decrypted) {
        if (decrypted.length < 8) {
            activity.warning("INVALID coordinates: length < 8 bytes");
            return;
        }

        ByteBuffer bb = ByteBuffer.wrap(decrypted).order(ByteOrder.LITTLE_ENDIAN);
        float latitude = bb.getFloat();
        float longitude = bb.getFloat();
        activity.info(String.format("Coordinates: %f %f",latitude,longitude));
        return;
    }
}
