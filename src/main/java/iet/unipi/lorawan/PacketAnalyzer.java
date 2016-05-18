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

    class LastTest {
        private int test;
        private int configuration;

        public LastTest(int test, int configuration) {
            this.test = test;
            this.configuration = configuration;
        }

        public boolean is(int test, int configuration) {
            return (test == this.test && configuration == this.configuration);
        }
    }


    private final BlockingQueue<Message> messages;
    private final Map<String, Map<Integer, Experiment>> test = new HashMap<>();
    private final Map<String, LastTest> lastTest = new HashMap<>();

    // Log
    private final static Logger logPackets = Logger.getLogger("Packet analyzer: received packets");
    private static final String LOG_FILE = "data/received.txt";
    private final static Logger activity = Logger.getLogger("Packet analyzer: activity");
    private static final String ACTIVITY_FILE = "data/analyzer-activity.txt";

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


    @Override
    public void run() {

        int testNumber = -1;

        while (true) {
            try {
                Message message = messages.take();

                // Parse payload
                FrameMessage fm = new FrameMessage(new MACMessage(message.jsonObject.getString("data")));


                if (message.payload.length != 10) {
                    activity.warning("INVALID payload: length != 10 bytes");
                    return;
                }

                ByteBuffer bb = ByteBuffer.wrap(message.payload).order(ByteOrder.LITTLE_ENDIAN);
                byte testN = bb.get();
                byte configuration = bb.get();
                float latitude = bb.getFloat();
                float longitude = bb.getFloat();


                Map<Integer, Experiment> moteExperiments = test.get(message.devAddress);

                // If new test, print result
                if (moteExperiments == null) {
                    moteExperiments = new HashMap<>();
                    test.put(message.devAddress, moteExperiments);
                }


                Experiment ex = moteExperiments.get(testN);

                if (ex == null) {
                    ex = new Experiment(message.devAddress,testN);
                    moteExperiments.put((int) testN, ex);
                }


                // Check datarate, codingrate, power and update statistics
                ex.packets[configuration]++;

                // Update average coordinates
                ex.averageLat = (ex.averageLat*ex.received + latitude) / (ex.received+1);
                ex.averageLong = (ex.averageLong*ex.received + latitude) / (ex.received+1);
                ex.received++;

                //


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
