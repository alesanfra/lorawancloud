package iet.unipi.Lora.NetworkServer;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
    public byte[] payload;
    public final int MIC;
    private final byte dir;


    /**
     * Parse physical payload
     * @param physicalPayload
     */

    public MACMessage(String physicalPayload) {
        byte[] data = Base64.getDecoder().decode(physicalPayload.getBytes());

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
        this.dir = (type == CONFIRMED_DATA_UP || type == UNCONFIRMED_DATA_UP || type == JOIN_REQUEST) ? UPSTREAM : DOWNSTREAM;
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
        this.dir = (type == CONFIRMED_DATA_UP || type == UNCONFIRMED_DATA_UP || type == JOIN_REQUEST) ? UPSTREAM : DOWNSTREAM;

        // Calculate MIC
        this.MIC = this.computeMIC(mote);
    }


    /**
     * Compute Message Integrity Code (MIC)
     * @param mote
     * @return
     */

    private int computeMIC(LoraMote mote) {
        int frameCounter = (this.dir == UPSTREAM) ? mote.frameCounterUp : mote.frameCounterDown;

        ByteBuffer bb = ByteBuffer.allocate(B0_LEN + 1 + payload.length); // 16 bytes B0, 1 byte MAC header,
        bb.order(ByteOrder.LITTLE_ENDIAN);

        // Build B0 and put in byte buffer
        bb.put((byte) 0x49);
        bb.putInt(0x00);
        bb.put(this.dir);
        bb.putInt(mote.devAddress);
        bb.putInt(frameCounter);
        bb.put((byte) 0x00);
        bb.put((byte) (this.payload.length + 1));

        // Build MSG and put in byte buffer
        byte mac_header = (byte) (((this.type << 5) + this.lorawanVersion) & 0xFF);
        bb.put(mac_header);
        bb.put(this.payload);

        // Calculate CMAC
        byte[] cmac = new byte[16];
        CMac mac = new CMac(new AESEngine());
        mac.init(new KeyParameter(mote.netSessionKey));
        mac.update(bb.array(), 0, bb.array().length);
        mac.doFinal(cmac, 0);

        // Get first 4 bytes as integer
        ByteBuffer mic = ByteBuffer.wrap(cmac);
        mic.order(ByteOrder.LITTLE_ENDIAN);
        return mic.getInt();
    }


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
        return bb.array();
    }
}
