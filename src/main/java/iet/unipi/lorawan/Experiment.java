package iet.unipi.lorawan;


import java.util.HashMap;
import java.util.Map;

public class Experiment {

    private static class Configuration {
        private static final String[] powers = {"14 dBm","11 dBm","8 dBm","5 dBm","2 dBm"};


        public final String dr;
        public final String cr;
        public final String pw;

        public Configuration(int cr, int pw, int dr) {
            this.dr = String.format("SF%dBW125",12-dr);
            this.cr = String.format("4/%d",cr+5);
            this.pw = powers[pw];
        }
    }

    private static final Configuration[] conf; // dictionary of configuration. Ex: 5 => sf7, cr 4/5, pw 14 dBm
    private static final int CONFIGURATION_N = 6*5*4;
    private static final int LENGTH_N = 2;
    private static final int MAX_TEST = 5;

    private static final Map<Integer,Integer> lengthIndexes = new HashMap<>();
    private static final int[] lengths = {10,50};

    private final String devAddress;
    private final int testNumber;
    private final int[][] packets = new int[LENGTH_N][CONFIGURATION_N];

    private int received = 0;
    private float averageLat = 0;
    private float averageLong = 0;

    private int lastConf = -1; // last configuration updated
    private int lastLength = -1;

    static {
        conf = new Configuration[CONFIGURATION_N];

        // index = cr * 5 * 6 + pw * 6 + dr

        for (int i=0; i<conf.length; i++) {
            int dr = i % 6;
            int pw = (i % (5*6)) / 6;
            int cr = i / (5*6);

            conf[i] = new Configuration(cr,pw,dr);
        }

        lengthIndexes.put(10,0);
        lengthIndexes.put(50,1);
    }



    public Experiment(String devAddress, int testNumber) {
        this.devAddress = devAddress;
        this.testNumber = testNumber;
    }



    public String print() {
        StringBuilder sb = new StringBuilder(300);
        sb.append(String.format("\n\tEnd EXPERIMENT %d of mote %s\n",testNumber, devAddress));
        sb.append(String.format("\tReceived packets: %d\n",received));
        sb.append(String.format("\tAverage position: %f %f\n",averageLat,averageLong));
        return sb.toString();
    }




    private String printConfiguration(int configuration, int length) {
        int lengthIndex = lengthIndexes.get(length);
        double per = (1 - (((double)packets[lengthIndex][configuration]) / MAX_TEST)) * 100;
        StringBuilder sb = new StringBuilder(300);
        sb.append(String.format("\n\tEnd CONFIG: %d\t\t  (experiment %d of mote %s)\n",configuration, testNumber, devAddress));
        sb.append(String.format("\tData rate: %s\t  Coding Rate: %s\t  Trasmission power: %s\t  Length: %s\n",conf[configuration].dr, conf[configuration].cr, conf[configuration].pw,length));
        sb.append(String.format("\tReceived pkts: %d\t  PER: %f %%\n",packets[lengthIndex][configuration],per));
        return sb.toString();
    }



    public String printLastConfiguration() {
        return printConfiguration(lastConf,lastLength);
    }




    public void addPacket(int configuration, int length, float latitude, float longitude) {
        int lengthIndex = lengthIndexes.get(length);
        packets[lengthIndex][configuration]++;

        // Update average coordinates
        averageLat = (averageLat * received + latitude) / (received+1);
        averageLong = (averageLong * received + longitude) / (received+1);

        // Update received
        received++;
    }



    public boolean isNotFirst() {
        if (lastConf >= 0 && lastLength >= 0) {
            return true;
        }
        return false;
    }



    public boolean lastConfigurationWasNot(int configuration, int length) {
        if (lastConf != configuration || lastLength != length) {
            return true;
        }
        return false;
    }



    public void saveConfiguration(int configuration, int length) {
        lastConf = configuration;
        lastLength = length;
    }
}



