package iet.unipi.Lora.NetworkServer;

import org.apache.commons.lang.ArrayUtils;
import org.bouncycastle.util.encoders.Hex;
import org.jetbrains.annotations.NotNull;

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

/**
 * Frame Layer Message
 */

public class FrameMessage {

    public static final int HEADER_LEN = 7;
    public static final int UPSTREAM = 0;
    public static final int DOWNSTREAM = 1;

    // FrameMessage Control flags
    public static final byte ADR = (byte) 0x80;
    public static final byte ADRACKReq = 0x40;
    public static final byte ACK = 0x20;
    public static final byte FPending = 0x10;

    public final byte[] devAddress;
    public final byte control;
    public final short counter;
    public final int optLen;
    public final byte[] options;
    public final byte port;
    public final byte[] payload;
    public final byte dir;


    /**
     * Build frame message from scratch
     * @param devAddress
     * @param control
     * @param counter
     * @param options
     * @param port
     * @param payload
     * @param dir
     */

    public FrameMessage(byte[] devAddress, byte control, short counter, byte[] options, int port, byte[] payload, int dir) {

        // Check options
        if (options != null && options.length > 15) {
            this.options = null;
            System.err.println("Options array too long");
        } else {
            this.options = options;
        }
        this.optLen = (this.options != null) ? this.options.length : 0;

        // Initialize device address
        if (devAddress == null) {
            System.err.println("devAddress is null, Device address set to 00000000");
            this.devAddress = Hex.decode("00000000");
        } else if (devAddress.length < 4) {
            this.devAddress = new byte[4];
            System.arraycopy(devAddress,0,this.devAddress,0,devAddress.length);
            System.err.printf("devAddress len is %d, Device Address set to %s", devAddress.length, Hex.encode(this.devAddress));
        } else if (devAddress.length > 4) {
            this.devAddress = new byte[4];
            System.arraycopy(devAddress,0,this.devAddress,0,4);
            System.err.printf("devAddress len is %d, Device Address set to %s", devAddress.length, Hex.encode(this.devAddress));
        } else {
            this.devAddress = devAddress;
        }

        // Initialize other fields
        this.control = (byte) ((control & 0xF0) + this.optLen);
        this.counter = counter;
        this.port = (byte) (port & 0xFF);
        this.payload = payload;
        this.dir = (byte) (dir & 0x1);

        //System.out.println("Frame Payload: " + Arrays.toString(this.payload));
    }


    /**
     * Parse raw data
     * @param macMessage
     */

    public FrameMessage(MACMessage macMessage) {
        this.dir = macMessage.dir;

        byte[] data = macMessage.payload;
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        this.devAddress = Arrays.copyOfRange(data, 0, 4);
        this.control = bb.get(4);
        //System.out.println("Control: " + String.format("%16s", Integer.toBinaryString(this.control)).replace(' ', '0'));
        this.counter = bb.getShort(5);
        this.optLen = control & 0xF;

        if (this.optLen > 0) {
            this.options = Arrays.copyOfRange(data, 7, 7+this.optLen);
        } else {
            this.options = null;
        }
        
        if (data.length > 7+this.optLen) {
            this.port = bb.get(7+this.optLen);
            this.payload = Arrays.copyOfRange(data, 8+this.optLen, data.length);
        } else {
            this.port = 0;
            this.payload = null;
        }
    }


    /**
     *
     * @param key
     * @return
     */

    public byte[] getEncryptedPayload(byte[] key) {
        if (this.payload == null || this.payload.length == 0) {
            return new byte[0];
        }

        int payloadSize =  this.payload.length;
        int targetSize = (payloadSize % 16 == 0) ? payloadSize : ((payloadSize/16) + 1) * 16;

        ByteBuffer bb = ByteBuffer.allocate(targetSize).order(ByteOrder.LITTLE_ENDIAN);

        for (int i=1; i<=targetSize/16; i++) {
            bb.put((byte) 1);
            bb.putInt(0);
            bb.put(this.dir);
            bb.put(this.devAddress);
            bb.putInt((int) this.counter);
            bb.put((byte) 0);
            bb.put((byte) i);
        }

        byte[] A = bb.array();
        //System.out.println(Arrays.toString(A));
        byte[] decrypted = new byte[payloadSize];

        try {
            // Create key and cipher
            Key aesKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

            // Create S
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            byte[] S = cipher.doFinal(A);
            //System.out.println("S: " +  Arrays.toString(S));

            // Encryption
            for (int i=0; i<payloadSize; i++) {
                decrypted[i] = (byte) (this.payload[i] ^ S[i]);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return decrypted;
    }


    /**
     * Conveninece method (encryption == decryption)
     * @param key
     * @return
     */

    public byte[] getDecryptedPayload(byte[] key) {
        return this.getEncryptedPayload(key);
    }

    /**
     * Returns ACK bit
     * @return ACK bit
     */

    public int getAck() {
        return (this.control & 0x20) >> 5;
    }


    /**
     * Build RXParamSetupReq MAC Command: set RX1 data rate offset, set RX2 data rate, set RX2 Frequency
     * @param RX1DRoffset Offset between uplink  and downlink data rate
     * @param RX2DataRate RX2 data rate
     * @param frequency RX2 center frequency expressed in hz
     * @return byte array to be put in frame option field
     */

    @NotNull
    public static byte[] getRXParamSetupReq(int RX1DRoffset, int RX2DataRate, int frequency) {
        byte DLsettings = (byte) (((RX1DRoffset & 0x7) << 4) + (RX2DataRate & 0xF));
        ByteBuffer bb = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(DLsettings);
        bb.putInt(frequency/100);
        byte[] req = bb.array();
        System.out.println("RXParamSetupReq: " + Arrays.toString(req));
        return Arrays.copyOfRange(req,0,4);
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

}
