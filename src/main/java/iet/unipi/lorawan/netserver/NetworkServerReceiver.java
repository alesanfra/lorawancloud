package iet.unipi.lorawan.netserver;


import iet.unipi.lorawan.*;
import iet.unipi.lorawan.messages.GatewayMessage;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.*;

public class NetworkServerReceiver implements Runnable {
    // Constants
    private static final int BUFFER_LEN = 2400;

    // Logger
    private static final Logger activity = Logger.getLogger("Network Server Receiver");
    private static final String ACTIVITY_FILE = Constants.NETSERVER_LOG_PATH + "NS_receiver_activity.txt";

    // Data Structures
    private final Map<String,InetSocketAddress> gateways;
    private final DatagramSocket gatewaySocket;
    private final ExecutorService executor = Executors.newFixedThreadPool(Constants.MAX_HANDLERS);
    private final MoteCollection motes;
    private final Map<String, AppServer> appServers;


    private byte[] buffer = new byte[BUFFER_LEN];


    static {
        // Init logger
        activity.setLevel(Level.INFO);
        try {
            FileHandler activityFile = new FileHandler(ACTIVITY_FILE, true);
            activityFile.setFormatter(new SimpleDateFormatter());
            activity.addHandler(activityFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Change ConsoleHandler behavior
        for (Handler handler: Logger.getLogger("").getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setFormatter(new SimpleDateFormatter());
            }
        }
    }

    public NetworkServerReceiver(int port, MoteCollection motes, Map<String,AppServer> appServers) throws SocketException {
        this.motes = motes;
        this.appServers = appServers;
        this.gateways = new ConcurrentHashMap<>();
        gatewaySocket = new DatagramSocket(port);
        String ip = gatewaySocket.getLocalAddress().getHostAddress();
        activity.info("Listening to: " + ip + " : " + gatewaySocket.getLocalPort());
    }


    @Override
    public void run() {
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                gatewaySocket.receive(packet); // Receive UDP packet
                GatewayMessage gm = new GatewayMessage(packet.getData()); // Create GWMP data structure
                String gateway = new String(Hex.encode(gm.gateway));

                switch (gm.type) {
                    case GatewayMessage.PUSH_DATA:
                        activity.info("PUSH_DATA received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + gateway);
                        //activity.info(gm.payload);
                        // Send PUSH_ACK to gateway
                        GatewayMessage pushAck = new GatewayMessage(GatewayMessage.GWMP_V1, gm.token, GatewayMessage.PUSH_ACK, null, null);
                        gatewaySocket.send(pushAck.getPacket((InetSocketAddress) packet.getSocketAddress()));

                        if (gateways.containsKey(gateway)) {
                            InetSocketAddress gw = gateways.get(gateway);
                            JSONObject payload = new JSONObject(gm.payload);

                            if (!payload.isNull("rxpk")) {
                                JSONArray rxpkArray = payload.getJSONArray("rxpk");
                                for (int i = 0; i < rxpkArray.length(); i++) {
                                    JSONObject message = rxpkArray.getJSONObject(i);
                                    executor.execute(new NetworkServerMoteHandler(message,gateway,gw,motes,appServers,gatewaySocket));
                                }
                            }
                        } else {
                            activity.severe("Gateway PULL_RESP address not found");
                        }
                        break;

                    case GatewayMessage.PULL_DATA:
                        activity.info("PULL_DATA received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + gateway);
                        InetSocketAddress gatewayAddress = (InetSocketAddress) packet.getSocketAddress();

                        // Send PULL_ACK
                        GatewayMessage pullAck = new GatewayMessage(gm.version, gm.token, GatewayMessage.PULL_ACK, gm.gateway, null);
                        gatewaySocket.send(pullAck.getPacket(gatewayAddress));

                        // Save UDP port
                        this.gateways.put(gateway, gatewayAddress);
                        break;

                    case GatewayMessage.TX_ACK:
                        activity.info("TX_ACK received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + gateway);
                        break;

                    default:
                        activity.warning("Unknown GWMP message type received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + gateway);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
