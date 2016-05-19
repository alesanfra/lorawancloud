package iet.unipi.lorawan;


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

    private static final Configuration[] conf;
    private static final int MAX_COMBINATIONS = 128;
    private static final int MAX_TEST = 10;

    private final String devAddress;
    private final int testNumber;
    private final int[] packets = new int[MAX_COMBINATIONS];

    private int received = 0;
    private float averageLat = 0;
    private float averageLong = 0;

    public byte lastConf = -1;

    static {
        conf = new Configuration[MAX_COMBINATIONS];

        // index = cr * 5 * 6 + pw * 6 + dr

        for (int i=0; i<conf.length; i++) {
            int dr = i % 6;
            int pw = (i % (5*6)) / 6;
            int cr = i / (5*6);

            conf[i] = new Configuration(cr,pw,dr);
        }
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

    private String printConfiguration(int configuration) {
        double per = (1 - (((double)packets[configuration]) / MAX_TEST)) * 100;
        StringBuilder sb = new StringBuilder(300);
        sb.append(String.format("\n\tEnd CONFIG: %d\t\t  (experiment %d of mote %s)\n",configuration, testNumber, devAddress));
        sb.append(String.format("\tData rate: %s\t  Coding Rate: %s\t  Trasmission power: %s\n",conf[configuration].dr, conf[configuration].cr, conf[configuration].pw));
        sb.append(String.format("\tReceived pkts: %d\t  PER: %f %%\n",packets[configuration],per));
        return sb.toString();
    }

    public String printLastConfiguration() {
        return printConfiguration(this.lastConf);
    }

    public void addPacket(int configuration, float latitude, float longitude) {
        packets[configuration]++;

        // Update average coordinates
        averageLat = (averageLat * received + latitude) / (received+1);
        averageLong = (averageLong * received + longitude) / (received+1);

        // Update received
        received++;
    }
}



