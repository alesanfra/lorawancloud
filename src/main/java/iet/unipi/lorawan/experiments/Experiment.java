package iet.unipi.lorawan.experiments;


import iet.unipi.lorawan.Constants;
import iet.unipi.lorawan.SimpleDateFormatter;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.*;

public class Experiment {
    public static final int REPETIONS = 100;


    private final String address;
    private final int received[][][][] = new int[256][6][6][2];
    private int[] last = new int[4];

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
        int[] current = {testN, dataRate, power, length};

        if (!Arrays.equals(last,current)) {
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

        StringBuilder sb = new StringBuilder(300);
        sb.append(String.format("\n\tEnd experiment %d of mote %s\n", conf.testN, address));
        sb.append(String.format("\tLength: %s\t\t  Data rate: %s\t  Trasmission power: %s\n", conf.len, conf.dr, conf.pw));
        sb.append(String.format("\tReceived packets: %d\t  PER: %f %%\n", packets, per));
        log.info(sb.toString());
    }


    private static class Configuration {
        private static final String[] powers = {"14 dBm","11 dBm","8 dBm","5 dBm","2 dBm"};
        private static final String[] lengths = {"10 byte","50 byte"};

        public final int testN;
        public final String dr;
        public final String pw;
        public final String len;

        public Configuration(int[] conf) {
            this.testN = conf[0];
            this.dr = String.format("SF%dBW125",12-conf[1]);
            this.pw = powers[conf[2]];
            this.len = lengths[conf[3]];
        }
    }
}
