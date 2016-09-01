package iet.unipi.lorawan.experiments;


import iet.unipi.lorawan.Constants;
import iet.unipi.lorawan.SimpleDateFormatter;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.*;

public class Experiment {
    public static final int REPETIONS = 100;
    private static final int MAX_TESTN = 256;
    private static final int MAX_DATA_RATES = 6;
    private static final int MAX_TX_POWERS = 15;
    private static final int MAX_LENGTHS = 2;


    private final String address;
    private final int received[][][][] = new int[MAX_TESTN][MAX_DATA_RATES][MAX_TX_POWERS][MAX_LENGTHS];
    private int[] last;

    private static final Logger log = Logger.getLogger("Experiment");
    private static final String ACTIVITY_FILE = Constants.APPSERVER_LOG_PATH + "experiments.txt";

    static {

        // Init logger
        log.setLevel(Level.INFO);
        FileHandler activityFile;
        try {
            activityFile = new FileHandler(ACTIVITY_FILE, true);
            activityFile.setFormatter(new SimpleDateFormatter());
            log.addHandler(activityFile);

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

    public Experiment(String address) {
        this.address = address;
    }


    public synchronized void add(int testN, int dataRate, int power, int length) {
        int[] current = new int[]{testN, dataRate, power, length};

        if (last != null && !Arrays.equals(last,current)) {
            print(last);
        }

        received[testN][dataRate][power][length]++;
        last = current;
    }



    public int get(int testN, int dataRate, int power, int length) {
        return received[testN][dataRate][power][length];
    }


    /**
     * Print received packets
     * @param params array with {test number, data rate, power, length}
     */

    private void print(int[] params) {
        int packets = received[params[0]][params[1]][params[2]][params[3]];
        double per = (1 - (((double) packets) / REPETIONS)) * 100;

        Configuration conf = new Configuration(params);

        String sb = String.format("\n\tEnd configuration\tMote %s\tExperiment %d\n\tLength: %s\t\t  Data rate: %s\t  Trasmission power: %s\n\tReceived packets: %d\t  PER: %f %%\n", address, conf.testN, conf.len, conf.dr, conf.pw, packets, per);
        log.info(sb);
    }
}
