package iet.unipi.Lora.NetworkServer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;

/**
 * Created by alessio on 30/03/16.
 */
public class JoinAccept {
    public static final int JOIN_ACCEPT_LENGTH = 12;

    public final byte[] appNonce = new byte[3];
    public final byte[] netID;
    public final int devAddress;
    public final byte DLsettings;
    public final byte RxDelay;
    //public byte[] CFList;


    private JoinAccept(int devAddress) {
        SecureRandom random = new SecureRandom();
        random.nextBytes(this.appNonce);
        this.netID = LoraNetworkServer.netID;
        this.devAddress = devAddress;
        this.DLsettings = 0; // Offset = 0, RX2 DR = 0
        this.RxDelay = (byte) (LoraNetworkServer.RECEIVE_DELAY1 / 1000000);




    }
}
