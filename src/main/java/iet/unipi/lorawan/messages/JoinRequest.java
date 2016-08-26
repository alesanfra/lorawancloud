package iet.unipi.lorawan.messages;

import iet.unipi.lorawan.Util;
import org.apache.commons.lang.ArrayUtils;
import org.bouncycastle.util.encoders.Hex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Created by alessio on 30/03/16.
 */
public class JoinRequest {

    public static final int EUI_LENGTH = 8;
    public static final int DEV_NONCE_LENGTH = 2;


    public final byte[] appEUI = new byte[EUI_LENGTH];
    public final byte[] devEUI = new byte[EUI_LENGTH];
    public final byte[] devNonce = new byte[DEV_NONCE_LENGTH];

    public JoinRequest(Packet packet) {
        if (packet.payload.length >= ((2*EUI_LENGTH) + DEV_NONCE_LENGTH)) {
            ByteBuffer bb = ByteBuffer.wrap(packet.payload).order(ByteOrder.LITTLE_ENDIAN);
            bb.get(this.appEUI, 0, EUI_LENGTH);
            bb.get(this.devEUI, 0, EUI_LENGTH);
            bb.get(this.devNonce, 0, DEV_NONCE_LENGTH);
        }
    }

    public void print() {
        byte[] app_eui = Arrays.copyOf(this.appEUI, this.appEUI.length);
        ArrayUtils.reverse(app_eui);

        byte[] dev_eui = Arrays.copyOf(this.devEUI, this.devEUI.length);
        ArrayUtils.reverse(dev_eui);

        byte[] dev_nonce = Arrays.copyOf(this.devNonce, this.devNonce.length);
        ArrayUtils.reverse(dev_nonce);

        System.out.println("App EUI: " + Util.formatEUI(app_eui));
        System.out.println("Dev EUI: " + Util.formatEUI(dev_eui));
        System.out.println("Dev Nonce: " + new String(Hex.encode(dev_nonce)) + "\n");
    }
}
