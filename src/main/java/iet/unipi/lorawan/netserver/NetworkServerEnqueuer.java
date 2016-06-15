package iet.unipi.lorawan.netserver;


import iet.unipi.lorawan.FrameMessage;
import iet.unipi.lorawan.MACMessage;
import iet.unipi.lorawan.Mote;
import iet.unipi.lorawan.MoteCollection;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class NetworkServerEnqueuer implements Runnable {


    private final MoteCollection motes; // Key must be devEUI

    private BufferedReader fromAS;


    public NetworkServerEnqueuer(Socket socket, MoteCollection motes) {
        this.motes = motes;

        // Init reader
        try {
            fromAS = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        } catch (IOException e) {
            e.printStackTrace();
            //System.exit(-1); // TODO: trovare un modo per cui non faccia crashare tutto il NS
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
                Mote mote = motes.getByEui(devEUI);
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
                mote.messages.put(macMessage);
                mote.frameCounterDown++;

            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}
