package iet.unipi.lorawan;


/**
 * Convinience class to build a dictionary
 * Ex: 5 => sf7, cr 4/5, pw 14 dBm
 */

public class Configuration {
    // Reverse dictionaries
    //private static final int[] lengths = {10,50};
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
