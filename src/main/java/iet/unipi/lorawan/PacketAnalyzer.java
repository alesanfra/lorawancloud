package iet.unipi.lorawan;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


public class PacketAnalyzer extends Thread {
    private final BlockingQueue<Message> messages; // Messages to be analyzed
    private final Map<String, LoraMote> motes; // List of all known motes

    private final int[][][][] stats; // dr, dist, len, pw
    private final int[] distances = {5,4,5,2,5,2,1,5,0,5,3,5,5,5,5};


    // Logger
    private static final Logger activity = Logger.getLogger("Packet analyzer: activity");
    private static final String ACTIVITY_FILE = "data/analyzer-activity.txt";


    /**
     * Standard constructor
     * @param messages messages to be analyzed
     * @param motes all known motes
     * @param stats
     */

    public PacketAnalyzer(BlockingQueue<Message> messages, Map<String, LoraMote> motes, int[][][][] stats) {
        this.messages = messages;
        this.motes = motes;
        this.stats = stats;

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
     * Packet Analyzer main function
     */

    @Override
    public void run() {
        activity.info("Starting PacketAnalyzer");
        loop: while (true) {
            try {
                Message msg = messages.take();
                String devAddr = msg.devAddress;

                // Parse payload    | testN | conf | lat | long |
                if (msg.payload.length < 10) {
                    activity.warning("INVALID payload: length < 10 bytes");
                    continue loop;
                }

                ByteBuffer bb = ByteBuffer.wrap(msg.payload).order(ByteOrder.LITTLE_ENDIAN);
                float latitude = bb.getFloat();
                float longitude = bb.getFloat();
                byte testN = bb.get();
                byte configuration = bb.get();

                if (testN == 0x7F) {
                    activity.info("Stop analyzer");
                    break loop;
                }

                // Get mote
                LoraMote mote = motes.get(devAddr);

                // Get experiment
                Experiment exp = mote.experiments[testN];

                // If new test, init Experiment and print last
                if (exp == null) {
                    exp = new Experiment(devAddr,testN);
                    mote.experiments[testN] = exp;

                    if (mote.lastTest >= 0) {
                        Experiment lastExperiment = mote.experiments[mote.lastTest];

                        String str = "";

                        if (lastExperiment.isNotFirst()) {
                            str += lastExperiment.printLastConfiguration();
                        }

                        str += lastExperiment.endExperiment();
                        activity.info(str);
                        lastExperiment.plotData(0);
                    }
                    mote.lastTest = testN;
                }


                // check configuration, if new one print the old one
                if (exp.lastConfigurationWasNot(configuration, msg.payload.length)) {
                    if (exp.isNotFirst()) {
                        activity.info(exp.printLastConfiguration());
                    }
                    exp.saveConfiguration(configuration, msg.payload.length);
                }

                // Add packet to the experiment
                exp.addPacket(configuration,msg.payload.length,latitude,longitude);

                // save packet
                int dist = distances[testN];
                int dataRate = configuration%6;
                int power = (configuration % (5*6)) / 6;
                int len = Experiment.lengthIndexes.get(msg.payload.length);

                int[][] bad = { {10,13}, {10,12}, {50,5}};

                if (testN == 3) {
                    for (int[] entry: bad) {
                        if (msg.payload.length == entry[0] && configuration == entry[1]) {
                            continue loop;
                        }
                    }
                }

                stats[dataRate][dist][len][power]++;

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
