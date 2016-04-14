package iet.unipi.Lora.NetworkServer;

import org.apache.commons.lang.ArrayUtils;
import org.bouncycastle.util.encoders.Hex;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.Key;
import java.util.Arrays;


/**
 * Describes a LoRa Mote
 */

public class LoraMote {
    // LoRa mote attributes
    public final byte[] devEUI;
    public final byte[] appEUI;
    public final byte[] devAddress;
    public final byte[] appKey;
    public byte[] netSessionKey;
    public byte[] appSessionKey;
    public int frameCounterUp;
    public int frameCounterDown;

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


    /**
     *
     * @param flag
     * @param appNonce
     * @param netID
     * @param devNonce
     * @return
     */

    private byte[] getSessionKey(byte flag, byte[] appNonce, byte[] netID, byte[] devNonce) {
        ByteBuffer bb = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(flag);
        bb.put(appNonce);
        bb.put(netID);
        bb.put(devNonce);

        byte[] pad16Array = {0,0,0,0,0,0,0};
        bb.put(pad16Array);

        byte[] encrypted = new byte[16];

        try {
            // Create key and cipher
            Key aesKey = new SecretKeySpec(this.appKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

            // Create S
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            encrypted = cipher.doFinal(bb.array());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return encrypted;
    }


    /**
     *
     * @param devNonce
     * @param appNonce
     * @param netID
     */

    public void createSessionKeys(byte[] devNonce, byte[] appNonce, byte[] netID) {
        this.netSessionKey = getSessionKey((byte) 0x01, appNonce, netID, devNonce);
        this.appSessionKey = getSessionKey((byte) 0x02, appNonce, netID, devNonce);
    }


    /**
     * Get dev eui as string
     * @return
     */

    public String getDevEUI() {
        byte[] dev_eui = Arrays.copyOf(this.devEUI,this.devEUI.length);
        ArrayUtils.reverse(dev_eui);
        return formatEUI(dev_eui);
    }


    /**
     * Get App eui as string
     * @return
     */

    public String getAppEUI() {
        byte[] app_eui = Arrays.copyOf(this.appEUI,this.appEUI.length);
        ArrayUtils.reverse(app_eui);
        return formatEUI(this.appEUI);
    }


    /**
     * Format EUI in a readble way
     * @param eui EUI expressed as a number
     * @return EUI String like AA:BB:CC:DD:EE:FF:GG:HH
     */

    public static String formatEUI(byte[] eui) {
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
        byte[] dev_addr = Arrays.copyOf(devAddress,devAddress.length);
        ArrayUtils.reverse(dev_addr);

        StringBuilder sb = new StringBuilder(11);

        for (int i=0; i<4; i++) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(String.format("%02X",dev_addr[i]));
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
