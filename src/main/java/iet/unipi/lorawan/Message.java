package iet.unipi.lorawan;

public class Message {

    public final String devAddress;
    public final byte[] payload;


    public Message(String devAddress, byte[] payload) {
        this.devAddress = devAddress;
        this.payload = payload;
    }
}
