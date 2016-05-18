package iet.unipi.lorawan;

import org.json.JSONObject;


public class Message {

    public final String devAddress;
    public final JSONObject jsonObject;
    public final byte[] payload;


    public Message(String devAddress, JSONObject jsonObject, byte[] payload) {
        this.devAddress = devAddress;
        this.jsonObject = jsonObject;
        this.payload = payload;
    }
}
