package iet.unipi.Lora.NetworkServer;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * Describes a LoRa Mote
 */

public class LoraMote {
    public static final byte NET_SESSION_KEY = 0x01;
    public static final byte APP_SESSION_KEY = 0x02;



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

    public static byte[] parseHexKey(String key) {
        int len = key.length() / 2;
        byte[] data = new byte[len];
        for (int i = 0; i < len; i++) {
            data[i] = (byte) ((Character.digit(key.charAt(2*i), 16) << 4) + Character.digit(key.charAt(2*i+1), 16));
        }
        return data;
    }

    /*
    public static byte[] getSessionKey(byte[] appKey, byte flag, byte[] appNonce, byte[] netID, short devNonce) {

        ByteBuffer bb = ByteBuffer.allocate().order(ByteOrder.LITTLE_ENDIAN);

        bb.put(flag);
        bb.put(appNonce);
        bb.put(netID);
        bb.putShort(devNonce);

        try {
            // Create key and cipher
            Key aesKey = new SecretKeySpec(appKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB");

            // Create S
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            byte[] S = cipher.doFinal(A);

            byte[] decrypted = new byte[payloadSize];

            for (int i=0; i<payloadSize; i++) {
                decrypted[i] = (byte) (this.payload[i] ^ S[i]);
            }

            return decrypted;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
    }*/


    @Override
    public boolean equals(Object o) {
        if(!(o instanceof LoraMote)) {
            return false;
        }
        LoraMote other = (LoraMote) o;
        return this.devAddress == other.devAddress;
    }
}
