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
    private final Map<String, Map<Integer, Experiment>> test = new HashMap<>();
    private final Map<String, Experiment> lastTest = new HashMap<>();

    // Log
    private final static Logger logPackets = Logger.getLogger("Packet analyzer: received packets");
    private static final String LOG_FILE = "data/received.txt";
    private final static Logger activity = Logger.getLogger("Packet analyzer: activity");
    private static final String ACTIVITY_FILE = "data/analyzer-activity.txt";

    /**
     *
     * @param messages
     */

    public PacketAnalyzer(BlockingQueue<Message> messages) {
        this.messages = messages;

        // Init logger for received packets
        logPackets.setUseParentHandlers(false);
        logPackets.setLevel(Level.INFO);
        try {
            FileHandler logPacketsFile = new FileHandler(LOG_FILE, true);
            logPacketsFile.setFormatter(new LogFormatter());
            logPackets.addHandler(logPacketsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }


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

        while (true) {
            try {
                Message message = messages.take();
                String devAddr = message.devAddress;

                // Parse payload
                if (message.payload.length != 10) {
                    activity.warning("INVALID payload: length != 10 bytes");
                    break;
                }

                ByteBuffer bb = ByteBuffer.wrap(message.payload).order(ByteOrder.LITTLE_ENDIAN);
                byte testN = bb.get();
                byte configuration = bb.get();
                float latitude = bb.getFloat();
                float longitude = bb.getFloat();


                Map<Integer, Experiment> moteExperiments = test.get(devAddr);

                // If new mote, init structures
                if (moteExperiments == null) {
                    moteExperiments = new HashMap<>();
                    test.put(devAddr, moteExperiments);
                }


                Experiment ex = moteExperiments.get(testN);

                // If new test, init Experiment and print last
                if (ex == null) {
                    ex = new Experiment(devAddr,testN);
                    moteExperiments.put((int) testN, ex);

                    Experiment lastExperiment = lastTest.get(devAddr);
                    if (lastExperiment != null) {
                        if (lastExperiment.lastConfiguration >= 0) {
                            activity.info(lastExperiment.printLastConfiguration());
                        }

                        activity.info(lastExperiment.print());
                    }
                    lastTest.put(devAddr, ex);
                }

                // check configuration, if new one print the old one
                if (configuration != ex.lastConfiguration) {
                    if (ex.lastConfiguration >= 0) {
                        activity.info(ex.printLastConfiguration());
                    }

                    ex.lastConfiguration = configuration;
                }


                // Check datarate, codingrate, power and update statistics
                ex.packets[configuration]++;

                // Update average coordinates
                ex.averageLat = (ex.averageLat*ex.received + latitude) / (ex.received+1);
                ex.averageLong = (ex.averageLong*ex.received + longitude) / (ex.received+1);
                ex.received++;
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
