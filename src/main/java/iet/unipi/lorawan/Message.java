package iet.unipi.lorawan;

import org.json.JSONObject;


public class Message {

    public final JSONObject jsonObject;
    public final byte[] payload;

    public Message(JSONObject jsonObject, byte[] payload) {
        this.jsonObject = jsonObject;
        this.payload = payload;
    }
}
