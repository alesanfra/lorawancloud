package iet.unipi.lorawan;

import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.*;


/**
 * Main class of the lorawan Network Server
 */

public class NetworkServer {

    // Network Server settings
    private static final int BUFFER_LEN = 2400;
    private static final int UDP_PORT = 1700;

    // TX Settings
    private static final int RFCH = 0;
    private static final int POWER = 27;
    private static final boolean IPOL = true;
    private static final boolean NCRC = true;
    private static final byte FRAME_PORT = 3;
    private static final boolean SEND_ACK = true;
    private static final boolean RX1_ENABLED = true;


    // Temporization of LoRaWAN downstream messages
    public static final long RECEIVE_DELAY1 = 1000000; // microsec
    public static final long RECEIVE_DELAY2 = RECEIVE_DELAY1 + 1000000; // microsec
    public static final long JOIN_ACCEPT_DELAY1 = 5000000;
    public static final long JOIN_ACCEPT_DELAY2 = JOIN_ACCEPT_DELAY1 + 1000000;


    // Log
    private final static Logger logPackets = Logger.getLogger("Network Server: received packets");
    private static final String LOG_FILE = "data/received.txt";
    private final static Logger activity = Logger.getLogger("Network Server: activity");
    private static final String ACTIVITY_FILE = "data/netserver-activity.txt";

    // Conf file
    public static final String MOTES_CONF = "conf/motes.json";

    // Data Structures
    private final Map<String,LoraMote> motes;
    private final Map<String,InetSocketAddress> gateways;
    private final BlockingQueue<Message> messages;

    // Socket
    private DatagramSocket sock;


    /**
     * Standard constructor:
     *  - init motes, gateways and messages
     *  - int logggers
     *  - start packet analyzer thread
     */

    public NetworkServer() {
        this.motes = loadMotesFromFile(MOTES_CONF);
        this.gateways = new HashMap<>();
        this.messages = new LinkedBlockingQueue<>();

        // Init logger for received packets
        logPackets.setUseParentHandlers(false);
        logPackets.setLevel(Level.INFO);
        try {
            FileHandler logPacketsFile = new FileHandler(LOG_FILE, true);
            logPacketsFile.setFormatter(new LogFormatter());
            logPackets.addHandler(logPacketsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }


        // Init logger for server activity
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

        // Start packet analyzer
        PacketAnalyzer packetAnalyzer = new PacketAnalyzer(messages,motes);
        packetAnalyzer.start();
    }



    /**
     * Main function of the network server
     */

    protected void run() {
        try {
            sock = new DatagramSocket(UDP_PORT);
            activity.info("Listening to: " + sock.getLocalAddress().getHostAddress() + " : " + sock.getLocalPort());

            while (true) {
                DatagramPacket packet = new DatagramPacket(new byte[BUFFER_LEN], BUFFER_LEN);
                sock.receive(packet);
                long receiveTime = System.nanoTime();
                GatewayMessage gm = new GatewayMessage(packet.getData());
                String gateway = Util.formatEUI(gm.gateway);

                switch (gm.type) {
                    case GatewayMessage.PUSH_DATA: {
                        activity.info("PUSH_DATA received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + gateway);
                        GatewayMessage pushAck = new GatewayMessage(GatewayMessage.GWMP_V1, gm.token, GatewayMessage.PUSH_ACK, null, null);
                        sock.send(pushAck.getPacket((InetSocketAddress) packet.getSocketAddress()));

                        // Handle PUSH_DATA
                        activity.info(gm.payload);
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

                                switch (mm.type) {
                                    case MACMessage.JOIN_REQUEST:
                                        handleJoin(rxpk, mm, Util.formatEUI(gm.gateway), tmst);
                                        break;

                                    case MACMessage.CONFIRMED_DATA_UP:
                                        handleMessage(rxpk, mm, Util.formatEUI(gm.gateway), true, tmst, "FF");
                                        break;

                                    case MACMessage.UNCONFIRMED_DATA_UP:
                                        handleMessage(rxpk, mm, Util.formatEUI(gm.gateway), SEND_ACK, tmst, "FF");
                                        break;

                                    default:
                                        activity.warning("Unknown MAC message type: " + Integer.toBinaryString(mm.type));
                                }
                            }
                            logPackets.info(gm.payload);
                        }

                        break;
                    }
                    case GatewayMessage.PULL_DATA: {
                        activity.info("PULL_DATA received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + gateway);
                        GatewayMessage pull_ack = new GatewayMessage(gm.version, gm.token, GatewayMessage.PULL_ACK, gm.gateway, null);
                        sock.send(pull_ack.getPacket((InetSocketAddress) packet.getSocketAddress()));

                        // Save UDP port
                        this.gateways.put(gateway, (InetSocketAddress) packet.getSocketAddress());
                        break;
                    }
                    case GatewayMessage.TX_ACK: {
                        activity.info("TX_ACK received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + gateway);
                        break;
                    }
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


    /**
     * Load all mote parameters from a json file
     * @param motesConf Path of the configuration file
     * @return Hashmap with key == device addess and value == LoraMote
     */

    private Map<String,LoraMote> loadMotesFromFile(String motesConf) {
        String file = "{}";
        Map<String, LoraMote> map = new ConcurrentHashMap<>();

        // Read json from file
        try {
            Path path = Paths.get(motesConf);
            file = new String(Files.readAllBytes(path), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            e.printStackTrace();
        }

        JSONArray motes = new JSONObject(file).getJSONArray("motes");

        // Scorro tutti i motes
        for (int i=0; i<motes.length(); i++) {
            JSONObject mote = motes.getJSONObject(i);

            String appEUI = mote.getString("appeui");
            String devEUI = mote.getString("deveui");
            String devAddr = mote.getString("devaddr").toLowerCase();

            // Creo un istanza di LoraMote e la aggiungo alla lista di motes
            LoraMote newMote;

            if (mote.getString("join").equals("OTA")) {
                String appKey = mote.getString("appkey");
                newMote = new LoraMote(
                        devEUI,
                        appEUI,
                        devAddr,
                        appKey,
                        "",
                        ""
                );
            } else {
                String netSessKey = mote.getString("netsesskey");
                String appSessKey = mote.getString("appsesskey");
                newMote = new LoraMote(
                        devEUI,
                        appEUI,
                        devAddr,
                        "",
                        netSessKey,
                        appSessKey
                );
            }

            // Aggiungo il nuovo mote alla lista
            map.put(devAddr,newMote);
        }

        return map;
    }


    /**
     *
     * @param rxpk
     * @param macMessage
     * @param gateway
     * @param timestamp
     * @throws IOException
     */

    private void handleJoin(JSONObject rxpk, MACMessage macMessage, String gateway, long timestamp) throws IOException {
        activity.info("JOIN_REQUEST received");
        JoinRequest jr = new JoinRequest(macMessage);
        jr.print();

        LoraMote mote = null;

        // Find mote into Network Server list
        for (LoraMote m: motes.values()) {
            if (Arrays.equals(mote.devEUI, jr.devEUI)) {
                mote = motes.get(m.getDevEUI());
                break;
            }
        }

        if (mote == null) {
            activity.warning("Join OTA: mote not found into list");
            return;
        }

        // Create Join Accept and encapsulate it in Mac Message
        JoinAccept ja = new JoinAccept(mote.devAddress);
        MACMessage macJA = new MACMessage(ja, mote);

        // Create json payload of GWMP message
        String txpk = GatewayMessage.getTxpk(
                false,
                timestamp + JOIN_ACCEPT_DELAY1,
                rxpk.getDouble("freq"),
                RFCH,
                POWER,
                rxpk.getString("modu"),
                rxpk.getString("datr"),
                rxpk.getString("codr"),
                IPOL,
                macJA.getEncryptedJoinAccept(mote.appKey),
                NCRC
        );

        // Create GWMP message
        GatewayMessage gw_ja = new GatewayMessage(
                GatewayMessage.GWMP_V1,
                (short) 0,
                GatewayMessage.PULL_RESP,
                null,
                txpk
        );

        if (!gateways.containsKey(gateway)) {
            activity.severe("Gateway PULL_RESP address not found");
        } else {
            InetSocketAddress gw = gateways.get(gateway);

            // Send Join Accept
            sock.send(gw_ja.getPacket(gw));

            // Create keys
            mote.createSessionKeys(jr.devNonce, ja.appNonce, ja.netID);
            mote.frameCounterDown = 0;
            mote.frameCounterUp = 0;
        }
    }


    /**
     *
     * @param rxpk
     * @param macMessage
     * @param gateway
     * @param sendAck
     * @param timestamp
     * @param response
     * @throws IOException
     */

    private void handleMessage(JSONObject rxpk, MACMessage macMessage, String gateway, boolean sendAck, long timestamp, String response) throws IOException {
        FrameMessage fm = new FrameMessage(macMessage);
        String type = (macMessage.type == MACMessage.CONFIRMED_DATA_UP) ? "CONFIRMED_DATA_UP" : "UNCONFIRMED_DATA_UP";

        activity.info( String.format(
                "%s received from address: %s \tport: %d \tframe counter: %d \tack flag: %d",
                type,
                fm.getDevAddress(),
                fm.port,
                fm.counter,
                fm.getAck()
        ));

        // Find mote into Network Server list
        LoraMote mote = motes.get(fm.getDevAddress());
        if (mote == null) {
            activity.warning("Mote not found into list");
            return;
        }

        // Update FrameMessage Counter Up - TODO: Check duplicates!!
        mote.frameCounterUp = fm.counter;


        // Authentication => check mic
        if (!macMessage.checkIntegrity(mote)) {
            activity.warning(fm.getDevAddress() + ": MIC NOT VALID");
        }

        if (fm.optLen > 0) {
            activity.info("There are options in the packet: " + new String(Hex.decode(fm.options)));
        }

        // Decrypt payload
        byte[] decrypted = fm.getDecryptedPayload(mote.appSessionKey);
        activity.info(String.format("Received payload (%d bytes): %s", decrypted.length, new String(Hex.encode(decrypted))));

        // Add message to queue
        messages.add(new Message(fm.getDevAddress(), decrypted));


        // Send ack if needed
        if (sendAck && gateways.containsKey(gateway)) {
            FrameMessage frame_resp = new FrameMessage(
                    fm.devAddress,
                    FrameMessage.ACK,
                    (short) mote.frameCounterDown,
                    null,
                    FRAME_PORT,
                    Hex.decode(response),
                    FrameMessage.DOWNSTREAM
            );

            MACMessage mac_resp = new MACMessage(
                    MACMessage.UNCONFIRMED_DATA_DOWN,
                    frame_resp,
                    mote
            );

            String gw_payload;

            if (RX1_ENABLED) {
                gw_payload = GatewayMessage.getTxpk(
                        false,
                        timestamp + RECEIVE_DELAY1,
                        rxpk.getDouble("freq"),
                        RFCH,
                        POWER,
                        rxpk.getString("modu"),
                        rxpk.getString("datr"),
                        rxpk.getString("codr"),
                        IPOL,
                        mac_resp.getBytes(),
                        NCRC
                );
            } else {
                gw_payload = GatewayMessage.getTxpk(
                        false,
                        timestamp + RECEIVE_DELAY2,
                        869.525,
                        RFCH,
                        POWER,
                        "LORA",
                        "SF12BW125",
                        rxpk.getString("codr"),
                        IPOL,
                        mac_resp.getBytes(),
                        NCRC
                );
            }

            GatewayMessage gw_resp = new GatewayMessage(
                    GatewayMessage.GWMP_V1,
                    (short) 0,
                    GatewayMessage.PULL_RESP,
                    null,
                    gw_payload
            );

            InetSocketAddress gw = gateways.get(gateway);
            sock.send(gw_resp.getPacket(gw));
            mote.frameCounterDown++;
        }
    }

    /**
     * Entry point
     * @param args
     */

    public static void main(String[] args) {
        NetworkServer networkServer = new NetworkServer();
        networkServer.run();
    }
}
