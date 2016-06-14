package iet.unipi.lorawan.netserver;


import iet.unipi.lorawan.Constants;
import iet.unipi.lorawan.Mote;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class NetworkServerListener implements Runnable {

    // Sockets
    private final ServerSocket listener;

    // Hashmap
    private final Map<String,Mote> motes;
    private final Map<String,InetSocketAddress> gateways;
    private final Map<String,Socket> appServers;


    public NetworkServerListener(
            int listeningPort,
            Map<String, Mote> motes,
            Map<String, InetSocketAddress> gateways,
            Map<String, Socket> appServers
    ) {
        this.motes = motes;
        this.gateways = gateways;
        this.appServers = appServers;

        // Creare socket listener
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(listeningPort);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            listener = serverSocket;
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                // Accept new Application Server
                Socket appSocket = listener.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(appSocket.getInputStream(), StandardCharsets.US_ASCII));
                String line = in.readLine();

                // Read Application EUI
                JSONObject message = new JSONObject(line);
                String appEUI = message.getString("appeui");

                if (appEUI.length() != Constants.EUI_LENGTH) {
                    break;
                }

                Socket socket = appServers.get(appEUI);

                if (socket != null && !socket.isClosed()) {
                    // Application was already registered, reject connection
                    appSocket.close();
                    break;
                }

                // Save socket - TODO: controllare che non ci sia già e gestire
                appServers.put(appEUI,appSocket);

                // Start DownstreamForwarder

                /*
                Thread sender = new Thread(new NetworkServerSender(
                        motes,
                        gateways,
                        appSocket
                ));

                // E' inutile executor perché nel caso d'uso std un thread parte e non si ferma più
                executor.execute(sender);

                // Così sono sicuro che parte
                sender.start();*/

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
