package iet.unipi.lorawan.messages;

import org.apache.commons.lang.ArrayUtils;
import org.bouncycastle.util.encoders.Hex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Frame Layer Message
 */

public class Frame {

    private static final Logger log = Logger.getLogger("Frame");

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
    public final byte[] options;
    public final byte port;
    public final byte[] payload;
    public final byte dir;


    /**
     * Build frame message from scratch
     * @param devAddress LoRaWAN address of the device
     * @param control Fctrl field
     * @param counter Fcounter field
     * @param options Fopt field
     * @param port Fport field
     * @param payload Fpayload
     * @param dir 0 if upstream, 1 if downstream
     */

    public Frame(byte[] devAddress, byte control, short counter, byte[] options, int port, byte[] payload, int dir) {

        // Check options
        if (options == null) {
            this.options = new byte[0];
        } else if (options.length > 15) {
            this.options = new byte[0];
            log.warning("Options array too long");
        } else {
            this.options = options;
        }

        // Initialize device address
        if (devAddress == null) {
            log.warning("devAddress is null, Device address set to 00000000");
            this.devAddress = Hex.decode("00000000");
        } else if (devAddress.length < 4) {
            this.devAddress = new byte[4];
            System.arraycopy(devAddress,0,this.devAddress,0,devAddress.length);
            log.warning(String.format("devAddress len is %d, Device Address set to %s", devAddress.length, new String(Hex.encode(this.devAddress))));
        } else if (devAddress.length > 4) {
            this.devAddress = new byte[4];
            System.arraycopy(devAddress,0,this.devAddress,0,4);
            log.warning(String.format("devAddress len is %d, Device Address set to %s", devAddress.length, new String(Hex.encode(this.devAddress))));
        } else {
            this.devAddress = devAddress;
        }

        // Initialize other fields
        this.control = (byte) ((control & 0xF0) + this.options.length);
        this.counter = counter;
        this.port = (byte) (port & 0xFF);
        this.payload = (payload == null) ? new byte[0] : payload;
        this.dir = (byte) (dir & 0x1);
    }


    /**
     * Parse raw data
     * @param packet Get the frame fields from a received Packet
     */

    public Frame(Packet packet) {
        this.dir = packet.dir;

        byte[] data = packet.payload;
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        this.devAddress = Arrays.copyOfRange(data, 0, 4);
        this.control = bb.get(4);
        this.counter = bb.getShort(5);
        int optLen = control & 0xF;

        if (optLen > 0) {
            this.options = Arrays.copyOfRange(data, 7, 7+optLen);
        } else {
            this.options = new byte[0];
        }
        
        if (data.length > 7 + optLen) {
            this.port = bb.get(7 + optLen);
            this.payload = Arrays.copyOfRange(data, 8 + optLen, data.length);
        } else {
            this.port = 0;
            this.payload = new byte[0];
        }
    }


    /**
     * Returns ACK bit
     * @return ACK bit
     */

    public int getAck() {
        return (this.control & 0x20) >> 5;
    }


    /**
     * Returns ADR bit
     * @return true if ADR is set, false otherwise
     */

    public boolean getADR() {
        return ((this.control & 0x80) > 0);
    }


    /**
     * Get device address as string
     * @return address as hexadecimal string
     */

    public String getDevAddress() {
        byte[] dev_addr = Arrays.copyOf(devAddress,devAddress.length);
        ArrayUtils.reverse(dev_addr);
        return new String(Hex.encode(dev_addr));
    }
}
