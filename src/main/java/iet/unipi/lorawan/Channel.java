package iet.unipi.lorawan;


public class Channel {
    public final double freq;
    public final int rfch;
    public final int power;
    public final String modu;
    public final String datr;
    public final String codr;
    public final boolean ncrc;

    public Channel(double freq, int rfch, int power, String modu, String datr, String codr, boolean ncrc) {
        this.freq = freq;
        this.rfch = rfch;
        this.power = power;
        this.modu = modu;
        this.datr = datr;
        this.codr = codr;
        this.ncrc = ncrc;
    }
}

