package iet.unipi.Lora.NetworkServer;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * Describes a LoRa Mote
 */

public class LoraMote {
    public final long devEUI;
    public final long appEUI;
    public final int devAddress;
    public final byte[] appKey;
    public final byte[] netSessionKey;
    public final byte[] appSessionKey;
    public int frameCounterUp;
    public int frameCounterDown;

    public final Queue<GatewayMessage> pullResp = new ConcurrentLinkedQueue<GatewayMessage>();


    /**
     * Standard constructor
     * @param devEUI
     * @param appEUI
     * @param devAddress
     * @param appKey
     * @param netSessionKey
     * @param appSessionKey
     */

    public LoraMote(long devEUI, long appEUI, int devAddress, byte[] appKey, byte[] netSessionKey, byte[] appSessionKey) {
        this.devEUI = devEUI;
        this.appEUI = appEUI;
        this.devAddress = devAddress;
        this.appKey = appKey;
        this.netSessionKey = netSessionKey;
        this.appSessionKey = appSessionKey;
        this.frameCounterUp = 0;
        this.frameCounterDown = 0;
    }


    /**
     * Parse string values and call standard contructor
     * @param devEUI
     * @param appEUI
     * @param devAddress
     * @param appKey
     * @param netSessionKey
     * @param appSessionKey
     */

    public LoraMote(String devEUI, String appEUI, String devAddress, String appKey, String netSessionKey, String appSessionKey) {
        this(Long.parseLong(devEUI,16), Long.parseLong(appEUI,16), Integer.parseInt(devAddress,16), parseHexKey(appKey), parseHexKey(netSessionKey), parseHexKey(appSessionKey));
    }


    /**
     * Convenience constructor
     * @param devAddress
     */

    public LoraMote(int devAddress) {
        this(0,0,devAddress,null,null,null);
    }


    /**
     * Convenience method to parse key string
     * E.g. "0a1b2c3d" ==> { 0x0a, 0x1b, 0x2c, 0x3d }
     * @param key
     * @return
     */

    private static byte[] parseHexKey(String key) {
        int len = key.length() / 2;
        byte[] data = new byte[len];
        for (int i = 0; i < len; i++) {
            data[i] = (byte) ((Character.digit(key.charAt(2*i), 16) << 4) + Character.digit(key.charAt(2*i+1), 16));
        }
        return data;
    }


    @Override
    public boolean equals(Object o) {
        if(!(o instanceof LoraMote)) {
            return false;
        }
        LoraMote other = (LoraMote) o;
        return this.devAddress == other.devAddress;
    }
}
