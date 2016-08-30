package iet.unipi.lorawan.messages;

import iet.unipi.lorawan.Constants;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class JoinRequest {
    public final byte[] appEUI = new byte[Constants.EUI_BYTE_LENGTH];
    public final byte[] devEUI = new byte[Constants.EUI_BYTE_LENGTH];
    public final byte[] devNonce = new byte[Constants.DEV_NONCE_LENGTH];
    public final boolean isValid;

    public JoinRequest(Packet packet) {
        if (packet.payload.length >= ((2* Constants.EUI_BYTE_LENGTH) + Constants.DEV_NONCE_LENGTH)) {
            ByteBuffer bb = ByteBuffer.wrap(packet.payload).order(ByteOrder.LITTLE_ENDIAN);
            bb.get(this.appEUI, 0, Constants.EUI_BYTE_LENGTH);
            bb.get(this.devEUI, 0, Constants.EUI_BYTE_LENGTH);
            bb.get(this.devNonce, 0, Constants.DEV_NONCE_LENGTH);
            this.isValid = true;
        } else {
            this.isValid = false;
        }
    }
}
