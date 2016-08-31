package iet.unipi.lorawan.appserver;

import iet.unipi.lorawan.Mote;
import iet.unipi.lorawan.experiments.Experiment;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;


public class ApplicationServerHandler implements Runnable {
    private static final byte UPSTREAM_DIRECTION = 0;

    // Variables
    private final BufferedReader socket;
    private final Application application;
    private static int token = 0;

    public ApplicationServerHandler(Application application, Socket socket) throws IOException {
        this.application = application;
        this.socket = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
    }


    @Override
    public void run() {
        application.log.info("Start AppServer Receiver: " + application.name + ", eui: " + new String(Hex.encode(application.eui)));

        while (true) {
            try {
                String message = socket.readLine();
                if(message == null) {
                    return;
                }

                application.log.info("Messaggio: " + message);

                JSONObject m = new JSONObject(message);
                JSONObject appJson = m.getJSONObject("app");


                String moteEui = appJson.getString("moteeui");
                Mote mote = application.motes.get(moteEui);

                if (mote == null) {
                    application.log.warning("Mote not found");
                    continue;
                }

                application.messages.add(new DownstreamMessage(mote, token++, 4, "aabb"));

                JSONObject data = appJson.getJSONObject("userdata");
                int port = data.getInt("port");
                int seqno = data.getInt("seqno");
                application.log.info(String.format("Received message from %s, port %d, counter %d",mote.getDevEUI(),port,seqno));

                byte[] payload = decryptPayload(data.getString("payload"), mote, seqno);
                application.log.info(String.format("Payload (%d bytes): %s", payload.length, new String(Hex.encode(payload))));

                // Analyze
                updateStitistics(mote, payload);

            } catch (SocketException e) {
                if (e.getMessage().equals("Connection reset")){
                    e.printStackTrace();
                    return;
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Experiment only
     * @param mote device
     * @param payload message payload
     */

    private void updateStitistics(Mote mote, byte[] payload) {
        // Parse payload
        if (payload.length < 10) {
            application.log.warning("INVALID payload: length < 10 bytes");
            return;
        }

        int length = 0;
        if (payload.length == 50) {
            length = 1;
        } else if (payload.length != 10) {
            application.log.warning("INVALID payload: length not equal to 10 bytes or 50 bytes");
            return;
        }

        ByteBuffer bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        int iteration = bb.getInt();
        byte testN = bb.get();
        byte dataRate = bb.get();
        byte power = bb.get();
        byte index = bb.get();

        mote.experiment.add(testN,dataRate, power, length);
    }


    /**
     *
     * @param payload
     * @param mote
     * @param counter
     * @return
     */

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
