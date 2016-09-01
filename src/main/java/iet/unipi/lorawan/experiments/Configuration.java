package iet.unipi.lorawan.experiments;

public class Configuration {
    private static final String[] lengths = {"10 byte","50 byte"};

    public final int testN;
    public final String dr;
    public final String pw;
    public final String len;

    public Configuration(int[] conf) {
        this(conf[0], conf[1], conf[2], conf[3]);
    }

    public Configuration(int testN, int dr, int pw, int len) {
        this.testN = testN;
        this.dr = String.format("SF%dBW125",12-dr);
        this.pw = String.format("%d dBm",pw);
        this.len = lengths[len];
    }

}
