package iet.unipi.lorawan;

public class Message {

    public final String devAddress;
    public final byte[] payload;
    public final boolean crc;

    public Message(String devAddress, byte[] payload) {
        this(devAddress,payload,true);
    }

    public Message(String devAddress, byte[] payload, boolean crc) {
        this.devAddress = devAddress;
        this.payload = payload;
        this.crc = crc;
    }
}
