package iet.unipi.lorawan.messages;

import iet.unipi.lorawan.Constants;
import org.bouncycastle.util.encoders.Hex;

import java.security.SecureRandom;

/**
 * Join accept message wrapper
 */

public class JoinAccept {
    public static final int JOIN_ACCEPT_LENGTH = 12;
    public static final int MAX_CHANNELS = 16;

    public static final byte[] NET_ID = Hex.decode("000000");

    public final byte[] appNonce = new byte[3];
    public final byte[] netID;
    public final byte[] devAddress;
    public final byte DLsettings;
    public final byte RxDelay;
    private byte[] CFList;


    /**
     * Join Accept constructor
     * @param devAddress
     */

    public JoinAccept(byte[] devAddress) {
        SecureRandom random = new SecureRandom();
        random.nextBytes(this.appNonce);
        this.netID = NET_ID;
        this.devAddress = devAddress;
        this.DLsettings = 0; // Offset = 0, RX2 DR = 0
        this.RxDelay = (byte) (Constants.RECEIVE_DELAY1 / 1000000);
        this.CFList = new byte[0];
    }

    /*
    public boolean addChannel() {
        if (this.CFList.length >= MAX_CHANNELS) {
            return false;
        }

        byte[] newChannel = new byte[4];
        // TODO: build channel
        this.CFList = ArrayUtils.addAll(this.CFList,newChannel);

        return true;
    }
    */

    public byte[] getChannels() {
        return this.CFList;
    }
}
