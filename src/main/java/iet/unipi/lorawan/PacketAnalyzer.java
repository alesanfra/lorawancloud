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
    private final BlockingQueue<Message> messages; // Messages to be analyzed
    private final Map<String, LoraMote> motes; // List of all known motes

    // Logger
    private final static Logger activity = Logger.getLogger("Packet analyzer: activity");
    private static final String ACTIVITY_FILE = "data/analyzer-activity.txt";


    /**
     * Standard constructor
     * @param messages messages to be analyzed
     * @param motes all known motes
     */

    public PacketAnalyzer(BlockingQueue<Message> messages, Map<String,LoraMote> motes) {
        this.messages = messages;
        this.motes = motes;

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
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
