package iet.unipi.lorawan;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;

public class NetworkServerReceiver implements Runnable {

    private static final int BUFFER_LEN = 2400;

    private final int port;
    private final Map<String,LoraMote> motes;
    private final Map<String,InetSocketAddress> gateways;


    private final static Logger activity = Logger.getLogger("Network Server Receiver: activity");
    private static final String ACTIVITY_FILE = "data/NS_receiver_activity.txt";


    private DatagramSocket sock;

    public NetworkServerReceiver(int port, ConcurrentHashMap<String,LoraMote> motes, ConcurrentHashMap<String,InetSocketAddress> gateways) {
        this.port = port;
        this.motes = motes;
        this.gateways = gateways;

        /**
         * Init Logger
         */

        activity.setLevel(Level.INFO);
        FileHandler activityFile = null;
        try {
            activityFile = new FileHandler(ACTIVITY_FILE, true);
            activityFile.setFormatter(new SimpleDateFormatter());
            activity.addHandler(activityFile);

            // Change ConsoleHandler behavior
            for (Handler handler: Logger.getLogger("").getHandlers()) {
                if (handler instanceof ConsoleHandler) {
                    handler.setFormatter(new SimpleDateFormatter());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void run() {

        // Create socket
        try {
            sock = new DatagramSocket(port);
            activity.info("Listening to: " + sock.getLocalAddress().getHostAddress() + " : " + sock.getLocalPort());

            while (true) {
                // Receive UDP packet and create GWMP data structure
                DatagramPacket packet = new DatagramPacket(new byte[BUFFER_LEN], BUFFER_LEN);
                sock.receive(packet);
                long receiveTime = System.nanoTime();
                GatewayMessage gm = new GatewayMessage(packet.getData());
                String gateway = Util.formatEUI(gm.gateway);

                switch (gm.type) {
                    case GatewayMessage.PUSH_DATA:
                        activity.info("PUSH_DATA received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + gateway);
                        GatewayMessage pushAck = new GatewayMessage(GatewayMessage.GWMP_V1, gm.token, GatewayMessage.PUSH_ACK, null, null);
                        sock.send(pushAck.getPacket((InetSocketAddress) packet.getSocketAddress()));


                        JSONObject jsonPayload = new JSONObject(gm.payload);

                        if (!jsonPayload.isNull("rxpk")) {

                            JSONArray rxpkArray = jsonPayload.getJSONArray("rxpk");
                            for (int i = 0; i < rxpkArray.length(); i++) {
                                JSONObject rxpk = rxpkArray.getJSONObject(i);

                                if (rxpk.getInt("stat") != 1) {
                                    activity.warning("CRC not valid, skip packet");
                                    continue;
                                }

                                long tmst = rxpk.getLong("tmst");

                                MACMessage mm = new MACMessage(rxpk.getString("data"));

                                // Authentication => check mic


                                // Forward message to Application Server


                                // Unlock Sender


                                // Forward Message to NetController Proxy


                            }
                        }

                        break;

                    case GatewayMessage.PULL_DATA:
                        activity.info("PULL_DATA received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + gateway);
                        InetSocketAddress gatewayAddress = (InetSocketAddress) packet.getSocketAddress();

                        // Send PULL_ACK
                        GatewayMessage pullAck = new GatewayMessage(gm.version, gm.token, GatewayMessage.PULL_ACK, gm.gateway, null);
                        sock.send(pullAck.getPacket(gatewayAddress));

                        // Save UDP port
                        this.gateways.put(gateway, gatewayAddress);
                        break;

                    case GatewayMessage.TX_ACK:
                        activity.info("TX_ACK received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + Util.formatEUI(gm.gateway));
                        break;

                    default:
                        activity.warning("Unknown GWMP message type received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + Util.formatEUI(gm.gateway));
                }

                double elapsedTime = ((double) (System.nanoTime() - receiveTime)) / 1000000;
                activity.info(String.format("Elapsed time %f ms\n", elapsedTime));
            }


        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
