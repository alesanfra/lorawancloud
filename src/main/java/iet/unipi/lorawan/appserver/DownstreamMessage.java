package iet.unipi.lorawan.appserver;

import iet.unipi.lorawan.Mote;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;


public class DownstreamMessage {
    // Direction
    private static final byte DOWNSTREAM = 1;

    // Fields
    private final Mote mote;
    private final int token;
    private final int seqno;
    private final int port;
    private final String payload;



    public DownstreamMessage(Mote mote, int token, int port, String payload) {
        this.mote = mote;
        this.token = token;
        this.seqno = mote.getFrameCounterDown();
        mote.incrementFrameCounterDown();
        this.port = port;
        this.payload = payload;
    }


    public String toJSONString() {
        JSONObject userdata = new JSONObject();
        userdata.put("dir","dn");
        userdata.put("seqno",seqno);
        userdata.put("port",port);
        userdata.put("payload",encrypt(mote.appSessionKey,payload));

        JSONObject app = new JSONObject();
        app.put("moteeui",mote.getDevEUI());
        app.put("seqno",seqno);
        app.put("token",token);
        app.put("userdata",userdata);

        JSONObject message = new JSONObject();
        message.put("app",app);

        return app.toString().trim();
    }

    private String encrypt(byte[] key, String data) {

        byte[] payload = Base64.getDecoder().decode(data.getBytes(StandardCharsets.US_ASCII));
        if (payload == null || payload.length == 0) {
            return new String();
        }

        int payloadSize =  payload.length;
        int targetSize = (payloadSize % 16 == 0) ? payloadSize : ((payloadSize/16) + 1) * 16;

        ByteBuffer bb = ByteBuffer.allocate(targetSize).order(ByteOrder.LITTLE_ENDIAN);

        for (int i=1; i<=targetSize/16; i++) {
            bb.put((byte) 1);
            bb.putInt(0);
            bb.put(DOWNSTREAM);
            bb.put(this.mote.devAddress);
            bb.putInt(seqno);
            bb.put((byte) 0);
            bb.put((byte) i);
        }

        byte[] A = bb.array();
        byte[] encrypted = new byte[payloadSize];

        try {
            // Create key and cipher
            Key aesKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

            // Create S
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            byte[] S = cipher.doFinal(A);

            // Encryption
            for (int i=0; i<payloadSize; i++) {
                encrypted[i] = (byte) (payload[i] ^ S[i]);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Base64.getEncoder().encodeToString(encrypted);
    }


}
