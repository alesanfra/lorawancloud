package iet.unipi.lorawan;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;

public class NetworkServerReceiver implements Runnable {

    private static final int BUFFER_LEN = 2400;
    private static final Logger activity = Logger.getLogger("Network Server Receiver: activity");
    private static final String ACTIVITY_FILE = "data/NS_receiver_activity.txt";

    private final int port;
    private final Map<String,LoraMote> motes;
    private final Map<String,InetSocketAddress> gateways;
    private final Map<String,Socket> appServers;

    // Socket UDP dal quale ricevo da tutti i gateway
    private final DatagramSocket gatewaySocket;

    public NetworkServerReceiver(
            int port,
            Map<String,LoraMote> motes,
            Map<String,InetSocketAddress> gateways,
            Map<String,Socket> appServers
    ) {
        this.port = port;
        this.motes = motes;
        this.gateways = gateways;
        this.appServers = appServers;

        // Init datagram socket
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(-1);
        } finally {
            gatewaySocket = socket;
            activity.info("Listening to: " + gatewaySocket.getLocalAddress().getHostAddress() + " : " + gatewaySocket.getLocalPort());
        }


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


    @Override
    public void run() {
        try {
            while (true) {
                // Receive UDP packet and create GWMP data structure
                DatagramPacket packet = new DatagramPacket(new byte[BUFFER_LEN], BUFFER_LEN);
                gatewaySocket.receive(packet);
                long receiveTime = System.nanoTime();
                GatewayMessage gm = new GatewayMessage(packet.getData());
                String gateway = Util.formatEUI(gm.gateway);

                switch (gm.type) {
                    case GatewayMessage.PUSH_DATA:
                        activity.info("PUSH_DATA received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + gateway);
                        GatewayMessage pushAck = new GatewayMessage(GatewayMessage.GWMP_V1, gm.token, GatewayMessage.PUSH_ACK, null, null);
                        gatewaySocket.send(pushAck.getPacket((InetSocketAddress) packet.getSocketAddress()));


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
                                FrameMessage fm = new FrameMessage(mm);

                                LoraMote mote = motes.get(fm.getDevAddress());

                                // Authentication => check mic
                                if (!mm.checkIntegrity(mote)) {
                                    activity.warning(fm.getDevAddress() + ": MIC NOT VALID");
                                }

                                // Forward message to Application Server
                                Socket toAS = appServers.get(mote.getAppEUI());

                                if (toAS == null) {
                                    activity.warning("App server NOT found");
                                } else {
                                    String message = createMessage(gm,mm,fm);
                                    try(OutputStreamWriter out = new OutputStreamWriter(toAS.getOutputStream(), StandardCharsets.US_ASCII)) {
                                        out.write(message);
                                        out.flush();
                                    }

                                    // Unlock Sender
                                }
                            }
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
                        activity.info("TX_ACK received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + Util.formatEUI(gm.gateway));
                        break;

                    default:
                        activity.warning("Unknown GWMP message type received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + Util.formatEUI(gm.gateway));
                }

                double elapsedTime = ((double) (System.nanoTime() - receiveTime)) / 1000000;
                activity.info(String.format("Elapsed time %f ms\n", elapsedTime));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private String createMessage(GatewayMessage gm, MACMessage mm, FrameMessage fm) {

        return new String();
    }
}
