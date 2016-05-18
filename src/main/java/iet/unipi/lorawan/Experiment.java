package iet.unipi.lorawan;


public class Experiment {

    public static final Configuration[] conf;

    private static final int MAX_COMBINATIONS = 128;
    private static final int MAX_TEST = 100;

    public final String devEUI;
    public final int testNumber;
    public final int[] packets = new int[MAX_COMBINATIONS];

    public int received = 0;
    float averageLat = 0, averageLong = 0;

    static {
        conf = new Configuration[128];

        // index = cr * 5 * 6 + pw * 6 + dr

        for (int i=0; i<conf.length; i++) {
            int dr = i % 6;
            int pw = (i % (5*6)) / 6;
            int cr = i / (5*6);

            conf[i] = new Configuration(cr,pw,dr);
        }
    }


    public Experiment(String devEUI, int testNumber) {
        this.devEUI = devEUI;
        this.testNumber = testNumber;
    }

    public String print() {
        StringBuilder sb = new StringBuilder(300);
        sb.append(String.format("\n\tEnd experiment %d of mote %s\n",testNumber,devEUI));
        sb.append(String.format("\tReceived packets: %d",received));
        sb.append(String.format("\tAverage position: %f %f\n\n",averageLat,averageLong));
        return sb.toString();
    }

    public String printConfiguration(int configuration) {
        double per = ((double)packets[configuration]) / MAX_TEST;

        StringBuilder sb = new StringBuilder(300);
        sb.append(String.format("\n\tExperiment %d of mote %s\n",testNumber,devEUI));
        sb.append(String.format("\tData rate: %s\tCoding Rate: %s\tTrasmission power: %s",conf[configuration].dr, conf[configuration].cr, conf[configuration].dr));
        sb.append(String.format("\tReceived packets: %d\tPER: %f\n\n",packets[configuration],per));
        return sb.toString();
    }
}


class Configuration {
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
