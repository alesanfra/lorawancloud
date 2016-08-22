package iet.unipi.lorawan.messages;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class MacCommand {


    // Relay management mac commands
    public static final int RelaySetupReq = 0x80;
    public static final int RelaySetupAns = 0x81;
    public static final int RelayStatusReq = 0x82;
    public static final int RelayStatusAns = 0x83;


    public final int type;
    public final byte[] payload;
    public final boolean optField;

    public MacCommand(int type, byte[] payload, boolean optField) {
        this.type = type;
        this.payload = payload;
        this.optField = optField;
    }

    public MacCommand(byte[] command, boolean optField) {
        this.type = command[0] & 0xFF;
        this.payload = Arrays.copyOfRange(command,1,command.length);
        this.optField = optField;
    }

    public byte[] getBytes() {
        ByteBuffer bb = ByteBuffer.allocate(1 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) (type & 0xFF));
        bb.put(payload);
        return bb.array();
    }

    public static MacCommand getRelayStatusAns() {

    }
}
