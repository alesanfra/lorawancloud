package iet.unipi.lorawan.messages;

import iet.unipi.lorawan.Constants;
import iet.unipi.lorawan.Mote;
import org.apache.commons.lang.ArrayUtils;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.encoders.Hex;


import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.Key;
import java.util.Arrays;
import java.util.Base64;
import java.util.logging.Logger;


/**
 * Mac Layer Message
 */

public class Packet {
    private static final Logger log = Logger.getLogger("Packet");

    // LoRaWAN message types
    public static final int JOIN_REQUEST = 0;
    public static final int JOIN_ACCEPT = 1;
    public static final int UNCONFIRMED_DATA_UP = 2;
    public static final int UNCONFIRMED_DATA_DOWN = 3;
    public static final int CONFIRMED_DATA_UP = 4;
    public static final int CONFIRMED_DATA_DOWN = 5;
    public static final int RELAY_UNCONFIRMED_DATA_UP = 6; // LoRaWAN relay mode
    public static final int INVALID_TYPE = -1;

    // Direction
    private static final byte UPSTREAM = 0;
    private static final byte DOWNSTREAM = 1;

    // LoRaWAN version
    public static final int LORAWAN_1 = 0;

    // Constants
    public static final int B0_LEN = 16;
    private static final int MIN_LORAWAN_PAYLOAD = 12; // MAC head + addr + Fctrl + Fcnt + MIC

    // MAC Messge fields
    public final int type;
    public final int version;
    public final byte[] payload;
    public final byte[] mic;
    public final byte dir;


    /**
     * Parse physical payload
     * @param physicalPayload Base64 encoded phisical payload
     */

    public Packet(String physicalPayload) {
        byte[] data = Base64.getDecoder().decode(physicalPayload.getBytes());

        if (data.length < MIN_LORAWAN_PAYLOAD) {
            log.warning("NOT LoRaWAN message");
            this.type = INVALID_TYPE;
            this.version = LORAWAN_1;
            this.payload = new byte[0];
            this.mic = new byte[4];
        } else {
            // Parsing Header
            this.type = (data[0] & 0xE0) >> 5;
            this.version = data[0] & 0x3;

            // Parsing Payload
            this.payload = Arrays.copyOfRange(data, 1, data.length - 4);
            this.mic = Arrays.copyOfRange(data, data.length - 4, data.length);
        }


        if (this.type == CONFIRMED_DATA_UP || this.type == UNCONFIRMED_DATA_UP || this.type == JOIN_REQUEST || this.type == RELAY_UNCONFIRMED_DATA_UP) {
            this.dir = UPSTREAM;
        } else if (this.type == CONFIRMED_DATA_DOWN || this.type == UNCONFIRMED_DATA_DOWN || this.type == JOIN_ACCEPT) {
            this.dir = DOWNSTREAM;
        } else if (this.type == INVALID_TYPE) {
            this.dir = -1;
        } else {
            log.warning("MAC messsage type not recognized, dir set to 0 (UPSTREAM)");
            this.dir = UPSTREAM;
        }
    }


    /**
     * Build a MAC message from scratch
     * @param type LoRaWAN type
     * @param frame Frame data structure
     * @param mote End device
     */

    public Packet(int type, Frame frame, Mote mote) {
        this.type = type & 0x7; // least significant three bits
        this.version = LORAWAN_1;

        int optLen = (frame.options != null) ? frame.options.length : 0;
        int payloadLen = (frame.payload != null) ? frame.payload.length + 1 : 0;

        // Build Encrypted payload from FrameMessage Message
        ByteBuffer bb = ByteBuffer.allocate(Frame.HEADER_LEN + optLen + payloadLen).order(ByteOrder.LITTLE_ENDIAN);

        // Put frameMessage header == 7 bytes
        bb.put(frame.devAddress);
        bb.put(frame.control);
        bb.putShort(frame.counter);

        // Put frameMessage options
        if (optLen > 0) {
            bb.put(frame.options);
        }

        // Put frameMessage port and encrypted frameMessage payload
        if (payloadLen > 0) {
            bb.put(frame.port);
            bb.put(frame.payload);
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
            log.warning("MAC messsage type not recognized, dir set to 1 (DOWNSTREAM)");
            this.dir = DOWNSTREAM;
        } else {
            this.dir = direction;
        }

        // Calculate MIC
        this.mic = this.computeMIC(mote,frame.counter);
    }



    public Packet(JoinAccept joinAccept, Mote mote) {
        this.type = JOIN_ACCEPT;
        this.version = LORAWAN_1;
        this.dir = DOWNSTREAM;

        int channels_len = joinAccept.getChannels().length;


        ByteBuffer bb = ByteBuffer.allocate(Constants.JOIN_ACCEPT_LENGTH + channels_len).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(joinAccept.appNonce);
        bb.put(joinAccept.netID);
        bb.put(joinAccept.devAddress);
        bb.put(joinAccept.DLsettings);
        bb.put(joinAccept.RxDelay);

        if (channels_len > 0) {
            bb.put(joinAccept.getChannels());
        }

        this.payload = bb.array();

        this.mic = this.computeJoinAcceptMIC(mote.appKey);
    }


    /**
     * Compute Message Integrity Code (MIC)
     * @param mote
     * @return
     */

    private byte[] computeMIC(Mote mote, int frameCounter) {
        ByteBuffer bb = ByteBuffer.allocate(B0_LEN + 1 + this.payload.length).order(ByteOrder.LITTLE_ENDIAN);

        // Build B0 and put in byte buffer
        bb.put((byte) 0x49);
        bb.putInt(0x00);
        bb.put(this.dir);
        bb.put(mote.devAddress);
        bb.putInt(frameCounter);
        bb.put((byte) 0x00);
        bb.put((byte) ((this.payload.length + 1) & 0xFF));

        // Build MSG and put in byte buffer
        byte mac_header = (byte) (((this.type << 5) + this.version) & 0xFF);
        bb.put(mac_header);
        bb.put(this.payload);
        byte[] stream = bb.array();

        // Calculate CMAC
        byte[] cmac = new byte[16];
        CMac mac = new CMac(new AESEngine());
        mac.init(new KeyParameter(mote.netSessionKey));
        mac.update(stream, 0, stream.length);
        mac.doFinal(cmac, 0);

        return Arrays.copyOfRange(cmac,0,4);
    }


    /**
     * Compute Message Integrity Code (MIC) for Join Accept messages
     * @param appKey
     * @return
     */

    private byte[] computeJoinAcceptMIC(byte[] appKey) {
        ByteBuffer bb = ByteBuffer.allocate(1 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        byte mac_header = (byte) (((this.type << 5) + this.version) & 0xFF);
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
        return Arrays.copyOfRange(cmac,0,4);
    }


    /**
     * Checks the integrity of the message
     * @param mote
     * @return
     */

    public boolean checkIntegrity(Mote mote, int counter) {
        byte[] c = this.computeMIC(mote, counter);
        //System.out.println("MICs: " + new String(Hex.encode(this.mic)) + " " + new String(Hex.encode(c)));
        return Arrays.equals(this.mic, c);
    }


    /**
     * Serialize MAC Message, using LITTLE ENDIAN byte ordering
     * @return
     */

    public byte[] getBytes() {
        ByteBuffer bb = ByteBuffer.allocate(1 + this.payload.length + 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        byte mac_header = (byte) ((this.type << 5) + this.version);
        bb.put(mac_header);
        bb.put(this.payload);
        bb.put(this.mic);
        byte[] res = bb.array();
        return res;
    }


    /**
     *
     * @param key
     * @return
     */

    /*public byte[] getEncryptedJoinAccept(byte[] key) {
        byte[] toEncrypt = ArrayUtils.addAll(this.payload,this.mic);
        byte[] encrypted = new byte[0];

        try {
            // Create key and cipher
            Key aesKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

            // Create S
            cipher.init(Cipher.DECRYPT_MODE, aesKey);
            encrypted = cipher.doFinal(toEncrypt);
        } catch (Exception e) {
            e.printStackTrace();
        }

        byte mac_header = (byte) ((this.type << 5) + this.version);
        byte[] res = ArrayUtils.add(encrypted,0,mac_header);
        return res;
    }*/
}
