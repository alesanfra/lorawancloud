package iet.unipi.lorawan.netserver;

import java.util.Arrays;


public class BoundDevice {
    public static final double[] DEFAULT_FREQUENCIES = {868.1, 868.3, 868.5};

    public final byte[] devAddress;
    public final int dataRateMin;
    public final int dataRateMax;
    public final double[] frequencies;


    public BoundDevice(byte[] devAddress, int dataRateMin, int dataRateMax, double[] frequencies) {
        this.devAddress = devAddress;
        this.dataRateMin = dataRateMin;
        this.dataRateMax = dataRateMax;

        if (frequencies == null || frequencies.length < 3) {
            this.frequencies = DEFAULT_FREQUENCIES;
        } else if (frequencies.length > 3) {
            this.frequencies = Arrays.copyOfRange(frequencies,0,3);
        } else {
            this.frequencies = frequencies;
        }
    }
}
