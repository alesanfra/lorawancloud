package iet.unipi.lorawan.netserver;


import iet.unipi.lorawan.Constants;
import iet.unipi.lorawan.Mote;
import iet.unipi.lorawan.MoteCollection;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkServerListener implements Runnable {

    // Sockets
    private final ServerSocket listener;

    // Hashmap
    private final MoteCollection motes;
    private final Map<String,Socket> appServers;

    // Executors
    private ExecutorService executor = Executors.newCachedThreadPool();


    public NetworkServerListener(int listeningPort, MoteCollection motes, Map<String, Socket> appServers) {
        this.motes = motes;
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
                    // AppEUI not valid
                    appSocket.close();
                    continue;
                }

                Socket socket = appServers.get(appEUI);

                if (socket != null && !socket.isClosed()) {
                    // Application was already registered, reject connection
                    appSocket.close();
                    continue;
                }

                appServers.put(appEUI,appSocket);

                // Start Enqueuer
                executor.execute(new NetworkServerEnqueuer(appSocket,motes));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
