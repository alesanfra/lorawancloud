package iet.unipi.Lora.NetworkServer;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.params.KeyParameter;

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
import java.util.Arrays;
import java.util.Base64;


/**
 * Mac Layer Message
 */

public class MACMessage {
    // LoRaWAN message types
    public static final int JOIN_REQUEST = 0;
    public static final int JOIN_ACCEPT = 1;
    public static final int UNCONFIRMED_DATA_UP = 2;
    public static final int UNCONFIRMED_DATA_DOWN = 3;
    public static final int CONFIRMED_DATA_UP = 4;
    public static final int CONFIRMED_DATA_DOWN = 5;

    // Direction
    private static final byte UPSTREAM = 0;
    private static final byte DOWNSTREAM = 1;

    // LoRaWAN version
    public static final int LORAWAN_1 = 0;

    // Constants
    public static final int B0_LEN = 16;

    // MAC Messge fields
    public final int type;
    public final int lorawanVersion;
    public final byte[] payload;
    public final int MIC;
    public final byte dir;


    /**
     * Parse physical payload
     * @param physicalPayload
     */

    public MACMessage(String physicalPayload) {
        byte[] data = Base64.getDecoder().decode(physicalPayload.getBytes());
        System.out.println("PHY Payload: " + Arrays.toString(data));

        // Parsing Header
        this.type = (data[0] & 0xE0) >> 5;
        this.lorawanVersion = data[0] & 0x3;

        // Parsing Payload
        this.payload = Arrays.copyOfRange(data, 1, data.length-4);
        //System.out.println("MAC Payload: " + Arrays.toString(this.payload));

        // Parsing MIC
        ByteBuffer bb = ByteBuffer.wrap(Arrays.copyOfRange(data,data.length-4,data.length));
        bb.order(ByteOrder.LITTLE_ENDIAN);
        this.MIC = bb.getInt();

        // Set dir
        byte direction = -1;

        if (this.type == CONFIRMED_DATA_UP || this.type == UNCONFIRMED_DATA_UP || this.type == JOIN_REQUEST) {
            direction = UPSTREAM;
        } else if (this.type == CONFIRMED_DATA_DOWN || this.type == UNCONFIRMED_DATA_DOWN || this.type == JOIN_ACCEPT) {
            direction = DOWNSTREAM;
        }

        if (direction == -1) {
            System.err.println("MAC messsage type not recognized, dir set to 1 (DOWNSTREAM)");
            this.dir = DOWNSTREAM;
        } else {
            this.dir = direction;
        }
    }


    /**
     * Build a MAC message from scratch
     * @param type
     * @param frameMessage
     * @param mote
     */

    public MACMessage(int type, FrameMessage frameMessage, LoraMote mote) {
        this.type = type & 0x7; // least significant three bits
        this.lorawanVersion = LORAWAN_1;

        int optLen = (frameMessage.options != null) ? frameMessage.options.length : 0;
        int payloadLen = (frameMessage.payload != null) ? frameMessage.payload.length + 1 : 0;

        // Build Encrypted payload from FrameMessage Message
        ByteBuffer bb = ByteBuffer.allocate(FrameMessage.HEADER_LEN + optLen + payloadLen);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        // Put frameMessage header == 7 bytes
        bb.putInt(frameMessage.devAddress);
        bb.put(frameMessage.control);
        bb.putShort(frameMessage.counter);

        // Put frameMessage options
        if (optLen > 0) {
            bb.put(frameMessage.options);
        }

        // Put frameMessage port and encrypted frameMessage payload
        if (payloadLen > 0) {
            bb.put(frameMessage.port);
            bb.put(frameMessage.getEncryptedPayload(mote.appSessionKey));
        }

        // Get MAC payload
        this.payload = bb.array();

        // Set dir
        byte direction = -1;

        if (this.type == CONFIRMED_DATA_UP || this.type == UNCONFIRMED_DATA_UP || this.type == JOIN_REQUEST) {
            direction = UPSTREAM;
        } else if (this.type == CONFIRMED_DATA_DOWN || this.type == UNCONFIRMED_DATA_DOWN || this.type == JOIN_ACCEPT) {
            direction = DOWNSTREAM;
        }

        if (direction == -1) {
            System.err.println("MAC messsage type not recognized, dir set to 1 (DOWNSTREAM)");
            this.dir = DOWNSTREAM;
        } else {
            this.dir = direction;
        }

        // Calculate MIC
        this.MIC = this.computeMIC(mote);

        System.out.println("MAC Payload: " + Arrays.toString(this.payload));
    }


    /*
    public MACMessage(JoinAccept joinAccept, LoraMote mote) {
        this.type = JOIN_ACCEPT;
        this.lorawanVersion = LORAWAN_1;
        this.dir = DOWNSTREAM;

        ByteBuffer bb = ByteBuffer.allocate(JoinAccept.JOIN_ACCEPT_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(joinAccept.appNonce);
        bb.put(joinAccept.netID);
        bb.putInt(joinAccept.devAddress);
        bb.put(joinAccept.DLsettings);
        bb.put(joinAccept.RxDelay);
        this.payload = bb.array();

        this.MIC = computeJoinAcceptMIC(mote.appKey);
    }*/


    /**
     * Compute Message Integrity Code (MIC)
     * @param mote
     * @return
     */

    private int computeMIC(LoraMote mote) {
        int frameCounter = (this.dir == UPSTREAM) ? mote.frameCounterUp : mote.frameCounterDown;

        ByteBuffer bb = ByteBuffer.allocate(B0_LEN + 1 + this.payload.length).order(ByteOrder.LITTLE_ENDIAN);

        // Build B0 and put in byte buffer
        bb.put((byte) 0x49);
        bb.putInt(0x00);
        bb.put(this.dir);
        bb.putInt(mote.devAddress);
        bb.putInt(frameCounter);
        bb.put((byte) 0x00);
        bb.put((byte) ((this.payload.length + 1) & 0xFF));

        // Build MSG and put in byte buffer
        byte mac_header = (byte) (((this.type << 5) + this.lorawanVersion) & 0xFF);
        bb.put(mac_header);
        bb.put(this.payload);
        byte[] stream = bb.array();
        System.out.println("Stream input: " + Arrays.toString(stream));


        // Calculate CMAC
        byte[] cmac = new byte[16];
        CMac mac = new CMac(new AESEngine());
        mac.init(new KeyParameter(mote.netSessionKey));
        mac.update(stream, 0, stream.length);
        mac.doFinal(cmac, 0);

        // Get first 4 bytes as integer
        ByteBuffer mic = ByteBuffer.wrap(cmac).order(ByteOrder.LITTLE_ENDIAN);
        return mic.getInt();
    }


    /*
    private int computeJoinAcceptMIC(byte[] appKey) {
        ByteBuffer bb = ByteBuffer.allocate(1 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        byte mac_header = (byte) (((this.type << 5) + this.lorawanVersion) & 0xFF);
        bb.put(mac_header);
        bb.put(this.payload);
        byte[] stream = bb.array();

        // Calculate CMAC
        byte[] cmac = new byte[16];
        CMac mac = new CMac(new AESEngine());
        mac.init(new KeyParameter(appKey));
        mac.update(stream, 0, stream.length);
        mac.doFinal(cmac, 0);

        // Get first 4 bytes as integer
        ByteBuffer mic = ByteBuffer.wrap(cmac).order(ByteOrder.LITTLE_ENDIAN);
        return mic.getInt();
    }*/


    /**
     * Checks the integrity of the message
     * @param mote
     * @return
     */

    public boolean checkIntegrity(LoraMote mote) {
        int calculatedMIC = this.computeMIC(mote);
        System.out.printf("Received MIC: %08X, Calculated MIC: %08X", this.MIC, calculatedMIC);
        boolean validMIC = (calculatedMIC == this.MIC);

        if (validMIC) {
            System.out.println(" ==> VALID MIC");
        } else {
            System.out.println(" ==> NOT VALID MIC");
        }

        return validMIC;
    }


    /**
     * Serialize MAC Message, using LITTLE ENDIAN byte ordering
     * @return
     */

    public byte[] getBytes() {
        ByteBuffer bb = ByteBuffer.allocate(1 + this.payload.length + 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        byte mac_header = (byte) ((this.type << 5) + this.lorawanVersion);
        bb.put(mac_header);
        bb.put(this.payload);
        bb.putInt(this.MIC);
        byte[] res = bb.array();
        System.out.println("PHY Payload: " + Arrays.toString(res));
        return res;
    }

    /*
    public byte[] getEncryptedJoinAccept(byte[] key) {
        ByteBuffer bb = ByteBuffer.allocate(this.payload.length + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(this.payload);
        bb.putInt(this.MIC);

        byte[] encrypted = new byte[0];
        try {
            // Create key and cipher
            Key aesKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

            // Create S
            cipher.init(Cipher.DECRYPT_MODE, aesKey);
            encrypted = cipher.doFinal(bb.array());
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

        bb = ByteBuffer.allocate(1 + encrypted.length);

        byte mac_header = (byte) ((this.type << 5) + this.lorawanVersion);
        bb.put(mac_header);
        bb.put(encrypted);
        byte[] res = bb.array();
        //System.out.println("PHY Payload: " + Arrays.toString(res));
        return res;
    }*/
}