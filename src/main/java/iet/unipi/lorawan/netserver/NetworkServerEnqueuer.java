package iet.unipi.lorawan.netserver;


import iet.unipi.lorawan.Mote;
import iet.unipi.lorawan.MoteCollection;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class NetworkServerEnqueuer implements Runnable {

    private final MoteCollection motes;
    private BufferedReader appServer;

    public NetworkServerEnqueuer(Socket socket, MoteCollection motes) throws IOException {
        this.motes = motes;
        appServer = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
    }


    @Override
    public void run() {
        while (true) {
            try {
                String line = appServer.readLine();
                String devEUI = new JSONObject(line).getJSONObject("app").getString("moteeui");
                Mote mote = motes.getByEui(devEUI);
                mote.messages.put(line);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
