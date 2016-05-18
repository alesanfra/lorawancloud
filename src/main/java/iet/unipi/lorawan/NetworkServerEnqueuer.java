package iet.unipi.lorawan;


import org.bouncycastle.util.encoders.Hex;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class NetworkServerEnqueuer extends Thread {

    private final BlockingQueue<MACMessage> messages;
    private final Map<String,LoraMote> motes; // Key must be devEUI

    private BufferedReader fromAS;


    public NetworkServerEnqueuer(BlockingQueue<MACMessage> messages, Socket socket, Map<String, LoraMote> motes) {
        this.messages = messages;
        this.motes = motes;

        // Init reader
        try {
            fromAS = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }


    @Override
    public void run() {

        while (true) {
            try {
                String line = fromAS.readLine();

                // Build frame
                JSONObject msg = new JSONObject(line).getJSONObject("app");
                JSONObject userdata = msg.getJSONObject("userdata");
                String devEUI = msg.getString("moteeui");
                LoraMote mote = motes.get(devEUI);
                byte[] payload = Base64.getDecoder().decode(userdata.getString("payload").getBytes());

                MACMessage macMessage = new MACMessage(
                        MACMessage.UNCONFIRMED_DATA_DOWN, // Non c'è nel protocollo di semtech
                        new FrameMessage(
                                mote.devAddress,
                                FrameMessage.ACK,
                                (short) msg.get("seqno"),
                                null,
                                userdata.getInt("port"),
                                payload,
                                FrameMessage.DOWNSTREAM
                        ),
                        mote
                );

                // Enqueue message
                messages.put(macMessage);

            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}
