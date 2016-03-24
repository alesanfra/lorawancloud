package iet.unipi.Lora.NetworkServer;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by alessio on 17/03/16.
 */
public class LoraMote {
    public long devEUI;
    public long appEUI;
    public int devAddress;
    public byte[] appKey;
    public byte[] netSessionKey;
    public byte[] appSessionKey;
    public int frameCounterUp;
    public int frameCounterDown;

    public final Queue<GatewayMessage> pullResp = new ConcurrentLinkedQueue<GatewayMessage>();


    public LoraMote(long devEUI, long appEUI, int devAddress, byte[] appKey, byte[] netSessionKey, byte[] appSessionKey) {
        this.devEUI = devEUI;
        this.appEUI = appEUI;
        this.devAddress = devAddress;
        this.appKey = appKey;
        this.netSessionKey = netSessionKey;
        this.appSessionKey = appSessionKey;
    }

    public LoraMote(String devEUI, String appEUI, String devAddress, String appKey, String netSessionKey, String appSessionKey) {
        this(Long.parseLong(devEUI,16), Long.parseLong(appEUI,16), Integer.parseInt(devAddress,16), parseHexKey(appKey), parseHexKey(netSessionKey), parseHexKey(appSessionKey));
    }

    // Convenience constructor
    public LoraMote(int devAddress) {
        this.devAddress = devAddress;
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof LoraMote)) {
            return false;
        }

        LoraMote other = (LoraMote) o;
        return this.devAddress == other.devAddress;
    }

    // Convenience method to parse key string
    // E.g. "0a1b2c3d" ==> { 0x0a, 0x1b, 0x2c, 0x3d }
    private static byte[] parseHexKey(String s) {
        int len = s.length() / 2;
        byte[] data = new byte[len];
        for (int i = 0; i < len; i++) {
            data[i] = (byte) ((Character.digit(s.charAt(2*i), 16) << 4) + Character.digit(s.charAt(2*i+1), 16));
        }
        return data;
    }
}
