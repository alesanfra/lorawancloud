package iet.unipi.Lora.NetworkServer;

import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;

/**
 * Created by alessio on 16/03/16.
 */
public class MACMessage {
    // LoRaWAN message types
    public static final int JOIN_REQUEST = 0;
    public static final int JOIN_ACCEPT = 1;
    public static final int UNCONFIRMED_DATA_UP = 2;
    public static final int UNCONFIRMED_DATA_DOWN = 3;
    public static final int CONFIRMED_DATA_UP = 4;
    public static final int CONFIRMED_DATA_DOWN = 5;

    // LoRaWAN version
    public static final int LORAWAN_1 = 0;

    // Constants
    public static final int B0_LEN = 16;

    // MAC Messge fields
    public int type;
    public int lorawanVersion;
    public byte[] payload;
    public int MIC;

    public MACMessage(String m) {
        byte[] data = Base64.getDecoder().decode(m.getBytes());

        // Parsing Header
        this.type = (data[0]&0xE0) >> 5;
        this.lorawanVersion = data[0] & 0x3;

        // Parsing Payload
        this.payload = Arrays.copyOfRange(data, 1, data.length-4);

        System.out.print("MAC Payload: ");
        System.out.println(Arrays.toString(this.payload));

        // Parsing MIC
        ByteBuffer bb = ByteBuffer.wrap(Arrays.copyOfRange(data,data.length-4,data.length));
        bb.order(ByteOrder.LITTLE_ENDIAN);
        this.MIC = bb.getInt();
    }

    public MACMessage(int type, int lorawanVersion, FrameMessage frameMessage, LoraMote mote) {
        this.type = type;
        this.lorawanVersion = lorawanVersion;
        int optLen = (frameMessage.options != null) ? frameMessage.options.length : 0;
        int payloadLen = (frameMessage.payload != null) ? frameMessage.payload.length + 1 : 1;

        // Build Encrypted payload from Frame Message
        ByteBuffer bb = ByteBuffer.allocate(FrameMessage.HEADER_LEN + optLen + payloadLen);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        // Put frame header == 7 bytes
        bb.putInt(frameMessage.devAddress);
        bb.put(frameMessage.control);
        bb.putShort(frameMessage.counter);

        // Put frame options
        if (optLen > 0) {
            bb.put(frameMessage.options);
        }

        // Put frame port and encrypted frame payload
        if (payloadLen > 0) {
            bb.put(frameMessage.port);
            bb.put(frameMessage.getEncryptedPayload(mote.appSessionKey));
        }

        // Get MAC payload
        this.payload = bb.array();

        int frameCounter = (type == MACMessage.CONFIRMED_DATA_UP || type == MACMessage.UNCONFIRMED_DATA_UP || type == MACMessage.JOIN_REQUEST) ? mote.frameCounterUp : mote.frameCounterDown;

        // Calculate MIC
        this.MIC = this.calcMic(mote, frameCounter);
    }

    public int calcMic(LoraMote mote, int frameCounter) {
        byte dir = (byte) ((type == CONFIRMED_DATA_DOWN || type == UNCONFIRMED_DATA_DOWN || type == JOIN_ACCEPT)? 1 : 0);
        ByteBuffer bb = ByteBuffer.allocate(B0_LEN + 1 + payload.length); // 16 bytes B0, 1 byte MAC header,
        bb.order(ByteOrder.LITTLE_ENDIAN);

        // Build B0 and put in byte buffer
        bb.put((byte) 0x49);
        bb.putInt(0x00);
        bb.put(dir);
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

    public boolean checkMIC(LoraMote mote) {
        int frameCounter = (type == MACMessage.CONFIRMED_DATA_UP || type == MACMessage.UNCONFIRMED_DATA_UP || type == MACMessage.JOIN_REQUEST) ? mote.frameCounterUp : mote.frameCounterDown;
        int calculatedMIC = this.calcMic(mote, frameCounter);
        System.out.print("Received MIC: " + Integer.toHexString(this.MIC) + ", Calculated MIC: " + Integer.toHexString(calculatedMIC));
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
