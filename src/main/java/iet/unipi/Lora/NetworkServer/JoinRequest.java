package iet.unipi.Lora.NetworkServer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Created by alessio on 30/03/16.
 */
public class JoinRequest {

    public final long devEUI;
    public final long appEUI;
    public final short devNonce;

    public JoinRequest(MACMessage macMessage) {
        byte[] data = macMessage.payload;
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        this.devEUI = bb.getLong();
        this.appEUI = bb.getLong();
        this.devNonce = bb.getShort();
    }
}
