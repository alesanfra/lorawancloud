package iet.unipi.lorawan.appserver;

import iet.unipi.lorawan.Mote;
import iet.unipi.lorawan.SimpleDateFormatter;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.logging.*;


public class ApplicationServerReceiver implements Runnable {

    private final BufferedReader netServer;
    private final Application application;
    private final byte UPSTREAM_DIRECTION = 0;

    // Logger
    private static final Logger activity = Logger.getLogger("Application Server Receiver: activity");
    private static final String ACTIVITY_FILE = "data/AS_receiver_activity.txt";

    static {
        // Init logger
        activity.setLevel(Level.INFO);
        FileHandler activityFile = null;
        try {
            activityFile = new FileHandler(ACTIVITY_FILE, true);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            activityFile.setFormatter(new SimpleDateFormatter());
            activity.addHandler(activityFile);
        }

        // Change ConsoleHandler behavior
        for (Handler handler: Logger.getLogger("").getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setFormatter(new SimpleDateFormatter());
            }
        }
    }


    public ApplicationServerReceiver(Application application) throws IOException {
        this.application = application;
        this.netServer = new BufferedReader(new InputStreamReader(application.socket.getInputStream(), StandardCharsets.US_ASCII));
    }


    @Override
    public void run() {
        while (true) {
            try {
                String message = netServer.readLine();
                //activity.info(message);

                JSONObject appJson = new JSONObject(message).getJSONObject("app");

                String moteEui = appJson.getString("moteeui");
                Mote mote = application.motes.get(moteEui);

                if (mote == null) {
                    continue;
                }

                JSONObject data = appJson.getJSONObject("userdata");
                int port = data.getInt("port");
                int seqno = data.getInt("seqno");
                activity.info(String.format("Received message from %s, port %d, counter %d",mote.getDevEUI(),port,seqno));

                byte[] payload = decryptPayload(data.getString("payload"), mote, seqno);
                activity.info(String.format("Payload (%d bytes): %s", payload.length, new String(Hex.encode(payload))));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private byte[] decryptPayload(String payload, Mote mote, int counter) {

        if (payload == null || payload.length() == 0) {
            return new byte[0];
        }

        byte[] data = Base64.getDecoder().decode(payload.getBytes());

        int dataSize =  data.length;
        int targetSize = (dataSize % 16 == 0) ? dataSize : ((dataSize/16) + 1) * 16;

        ByteBuffer bb = ByteBuffer.allocate(targetSize).order(ByteOrder.LITTLE_ENDIAN);

        for (int i=1; i<=targetSize/16; i++) {
            bb.put((byte) 1);
            bb.putInt(0);
            bb.put(UPSTREAM_DIRECTION);
            bb.put(mote.devAddress);
            bb.putInt(counter);
            bb.put((byte) 0);
            bb.put((byte) i);
        }

        byte[] A = bb.array();
        byte[] decrypted = new byte[dataSize];

        try {
            // Create key and cipher
            Key aesKey = new SecretKeySpec(mote.appSessionKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

            // Create S
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            byte[] S = cipher.doFinal(A);

            // Encryption
            for (int i=0; i<dataSize; i++) {
                decrypted[i] = (byte) (data[i] ^ S[i]);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return decrypted;
    }




}
