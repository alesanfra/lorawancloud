package iet.unipi.Lora.NetworkServer;

import org.apache.commons.lang.ArrayUtils;
import org.bouncycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * Describes a LoRa Mote
 */

public class LoraMote {
    public static final byte NET_SESSION_KEY = 0x01;
    public static final byte APP_SESSION_KEY = 0x02;



    public final byte[] devEUI;
    public final byte[] appEUI;
    public final byte[] devAddress;
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

    public LoraMote(byte[] devEUI, byte[] appEUI, byte[] devAddress, byte[] appKey, byte[] netSessionKey, byte[] appSessionKey) {
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
        byte[] dev_eui = Hex.decode(devEUI);
        ArrayUtils.reverse(dev_eui); // Store little endian
        this.devEUI = dev_eui;

        byte[] app_eui = Hex.decode(appEUI);
        ArrayUtils.reverse(app_eui); // Store little endian
        this.appEUI = app_eui;

        byte[] dev_address = Hex.decode(devAddress);
        ArrayUtils.reverse(dev_address); // Store little endian
        this.devAddress = dev_address;

        this.appKey = Hex.decode(appKey);
        this.netSessionKey = Hex.decode(netSessionKey);
        this.appSessionKey = Hex.decode(appSessionKey);

        this.frameCounterUp = 0;
        this.frameCounterDown = 0;
    }


    /**
     * Convenience constructor
     * @param devAddress
     */

    public LoraMote(byte[] devAddress) {
        this(null,null,devAddress,null,null,null);
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


    /**
     * Get dev eui as string
     * @return
     */

    public String getDevEUI() {
        return formatEUI(this.devEUI);
    }


    /**
     * Get App eui as string
     * @return
     */

    public String getAppEUI() {
        return formatEUI(this.appEUI);
    }


    /**
     * Format EUI in a readble way
     * @param eui EUI expressed as a number
     * @return EUI String like AA:BB:CC:DD:EE:FF:GG:HH
     */

    private String formatEUI(byte[] eui) {
        //String s = String.format("%016X",eui);
        StringBuilder sb = new StringBuilder(23);

        for (int i=0; i<8; i++) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02X",eui[i]));
        }
        return sb.toString().toUpperCase();
    }


    /**
     * Get device address as string
     * @return
     */

    public String getDevAddress() {
        StringBuilder sb = new StringBuilder(11);

        for (int i=0; i<4; i++) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(String.format("%02X",this.devAddress[i]));
        }
        return sb.toString().toUpperCase();
    }


    @Override
    public boolean equals(Object o) {
        if(!(o instanceof LoraMote)) {
            return false;
        }
        LoraMote other = (LoraMote) o;
        return Arrays.equals(this.devAddress,other.devAddress);
    }
}
