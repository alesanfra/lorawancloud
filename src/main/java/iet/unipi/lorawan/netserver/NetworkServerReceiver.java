package iet.unipi.lorawan.netserver;


import iet.unipi.lorawan.*;
import iet.unipi.lorawan.messages.FrameMessage;
import iet.unipi.lorawan.messages.GatewayMessage;
import iet.unipi.lorawan.messages.MACMessage;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;

public class NetworkServerReceiver implements Runnable {

    private static final int BUFFER_LEN = 2400;
    private static final int MAX_SENDERS = 10;

    // Logger
    private static final Logger activity = Logger.getLogger("Network Server Receiver: activity");
    private static final String ACTIVITY_FILE = "data/NS_receiver_activity.txt";

    // Data Structures
    private final MoteCollection motes;
    private final Map<String,InetSocketAddress> gateways;
    private final Map<String,Socket> appServers;

    // UDP socket
    private final DatagramSocket gatewaySocket;

    // Executor
    private final ExecutorService senderExecutor = Executors.newFixedThreadPool(MAX_SENDERS);


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

    public NetworkServerReceiver(
            int port,
            MoteCollection motes,
            Map<String,InetSocketAddress> gateways,
            Map<String,Socket> appServers
    ) {
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

                                long timestamp = rxpk.getLong("tmst");

                                MACMessage mm = new MACMessage(rxpk.getString("data"));
                                FrameMessage fm = new FrameMessage(mm);

                                Mote mote = motes.get(fm.getDevAddress());

                                // Authentication => check mic
                                if (!mm.checkIntegrity(mote)) {
                                    activity.warning(fm.getDevAddress() + ": MIC NOT VALID");
                                }

                                // Forward message to Application Server
                                Socket toAS = appServers.get(mote.getAppEUI());

                                if (toAS == null) {
                                    activity.warning("App server NOT found");
                                } else {
                                    String message = createMessage(new String(Hex.encode(gm.gateway)),rxpk,mm,fm);
                                    try(OutputStreamWriter out = new OutputStreamWriter(toAS.getOutputStream(), StandardCharsets.US_ASCII)) {
                                        out.write(message);
                                        out.flush();
                                    }


                                    // Create sender task
                                    Channel channel = new Channel(
                                            rxpk.getDouble("freq"),
                                            0,
                                            27,
                                            rxpk.getString("modu"),
                                            rxpk.getString("datr"),
                                            rxpk.getString("codr"),
                                            false
                                    );

                                    if (!gateways.containsKey(gateway)) {
                                        activity.severe("Gateway PULL_RESP address not found");
                                    } else {
                                        InetSocketAddress gw = gateways.get(gateway);
                                        boolean ack = (mm.type == MACMessage.CONFIRMED_DATA_UP);
                                        NetworkServerSender sender = new NetworkServerSender(mote, timestamp, ack, channel,gw);

                                        // Execute Sender
                                        senderExecutor.submit(sender);
                                    }
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

    private String createMessage(String gateway, JSONObject rxpk, MACMessage mm, FrameMessage fm) {
        JSONObject message = new JSONObject();

        switch (mm.type) {
            case MACMessage.JOIN_REQUEST:
                activity.warning("JOIN REQUEST not implemented yet");
                break;

            case MACMessage.CONFIRMED_DATA_UP:
            case MACMessage.UNCONFIRMED_DATA_UP: {
                activity.warning("DATA UP not implemented yet");
                JSONObject userdata = new JSONObject();
                userdata.put("seqno",fm.counter);
                userdata.put("port",fm.port);
                userdata.put("payload",fm.payload);

                JSONObject motetx = new JSONObject();
                motetx.put("freq",rxpk.getInt("freq"));
                motetx.put("modu",rxpk.getString("modu"));
                motetx.put("codr",rxpk.getString("codr"));
                motetx.put("adr", (fm.control & 0x80) > 0);

                userdata.put("motetx",motetx);

                JSONObject gwrx = new JSONObject();
                gwrx.put("eui",gateway);
                gwrx.put("time", rxpk.getString("time"));
                gwrx.put("timefromgateway",true);
                gwrx.put("chan",rxpk.getInt("chan"));
                gwrx.put("rfch",rxpk.getInt("rfch"));
                gwrx.put("rssi",rxpk.getInt("rssi"));
                gwrx.put("lsnr",rxpk.getInt("lsnr"));

                JSONArray gwrxArray = new JSONArray();
                gwrxArray.put(gwrx);

                JSONObject app = new JSONObject();
                app.put("moteeui",motes.get(fm.getDevAddress()).getDevEUI().toLowerCase());
                app.put("dir","up");
                app.put("userdata",userdata);
                app.put("gwrx",gwrxArray);

                message.put("app",app);

                break;
            }
            default:
                activity.warning("Unknown message type");
        }

        return message.toString();
    }
}
