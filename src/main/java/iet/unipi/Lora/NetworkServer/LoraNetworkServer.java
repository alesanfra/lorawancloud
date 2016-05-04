package iet.unipi.Lora.NetworkServer;

import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.logging.*;


/**
 * Main class of the Lora Network Server
 */

public class LoraNetworkServer implements Runnable {

    /**
     * Network Server settings
     */

    private static final int MAX_GATEWAYS = 100;
    private static final int BUFFER_LEN = 2400;
    private static final int UDP_PORT = 1700;


    /**
     * TX settings
     */

    private static final int RFCH = 0;
    private static final int POWER = 27;
    private static final boolean IPOL = true;
    private static final boolean NCRC = true;
    private static final byte FRAME_PORT = 3;
    private static final boolean SEND_ACK = true;
    private static final boolean RX1_ENABLED = true;


    /**
     * Temporization of LoRaWAN downstream messages
     */

    public static final long RECEIVE_DELAY1 = 1000000; // microsec
    public static final long RECEIVE_DELAY2 = RECEIVE_DELAY1 + 1000000; // microsec
    public static final long JOIN_ACCEPT_DELAY1 = 5000000;
    public static final long JOIN_ACCEPT_DELAY2 = JOIN_ACCEPT_DELAY1 + 1000000;


    // Log
    private final static Logger logPackets = Logger.getLogger("Lora Network Server: received packets");
    private static final String LOG_FILE = "data/received.txt";

    private final static Logger activity = Logger.getLogger("Lora Network Server: activity");
    private static final String ACTIVITY_FILE = "data/activity.txt";

    // List of all known motes
    private List<LoraMote> motes = new ArrayList<>();

    // Map of all known gateways
    private Map<String,InetSocketAddress> gateways = new HashMap<>(MAX_GATEWAYS);

    // Socket
    private DatagramSocket sock;


    /**
     * Main function of the network server
     */

    public void run() {
        try {
            // Init logger for received packets
            logPackets.setUseParentHandlers(false);
            logPackets.setLevel(Level.INFO);
            FileHandler logPacketsFile = new FileHandler(LOG_FILE, true);
            logPacketsFile.setFormatter(new LogFormatter());
            logPackets.addHandler(logPacketsFile);

            // Init logger for server activity
            activity.setLevel(Level.INFO);
            FileHandler activityFile = new FileHandler(ACTIVITY_FILE, true);
            activityFile.setFormatter(new SimpleDateFormatter());
            activity.addHandler(activityFile);

            // Change ConsoleHandler behavior
            for (Handler handler: Logger.getLogger("").getHandlers()) {
                if (handler instanceof ConsoleHandler) {
                    handler.setFormatter(new SimpleDateFormatter());
                }
            }

            sock = new DatagramSocket(UDP_PORT);
            activity.info("Listening to: " + sock.getLocalAddress().getHostAddress() + " : " + sock.getLocalPort());

            // Add motes
            motes.add(new LoraMote(
                    "A1B2C3D400000000",
                    "1112131415161718",
                    "A1B20000",
                    "00000000000000000000000000000000",
                    "01020304050607080910111213141516",
                    "000102030405060708090A0B0C0D0E0F"
            ));

            motes.add(new LoraMote(
                    "A1B2C3D400000001",
                    "1112131415161718",
                    "A1B20001",
                    "00000000000000000000000000000000",
                    "01020304050607080910111213141516",
                    "000102030405060708090A0B0C0D0E0F"
            ));

            motes.add(new LoraMote(
                    "A1B2C3D400000002",
                    "1112131415161718",
                    "A1B20002",
                    "00000000000000000000000000000000",
                    "01020304050607080910111213141516",
                    "000102030405060708090A0B0C0D0E0F"
            ));

            motes.add(new LoraMote(
                    "A1B2C3D400000003",
                    "1112131415161718",
                    "A1B20003",
                    "00000000000000000000000000000000",
                    "01020304050607080910111213141516",
                    "000102030405060708090A0B0C0D0E0F"
            ));

            motes.add(new LoraMote(
                    "A1B2C3D400000004",
                    "1112131415161718",
                    "A1B20004",
                    "00000000000000000000000000000000",
                    "01020304050607080910111213141516",
                    "000102030405060708090A0B0C0D0E0F"
            ));


            while (true) {

                // Receive UDP packet and create GWMP data structure
                DatagramPacket packet = new DatagramPacket(new byte[BUFFER_LEN], BUFFER_LEN);
                sock.receive(packet);
                long receiveTime = System.nanoTime();
                GatewayMessage gm = new GatewayMessage(packet.getData());

                switch (gm.type) {
                    case GatewayMessage.PUSH_DATA: {
                        activity.info(
                                "PUSH_DATA received from: " + packet.getAddress().getHostAddress()
                                + ", Gateway: " + Util.formatEUI(gm.gateway)
                        );

                        // Send PUSH_ACK
                        GatewayMessage push_ack = new GatewayMessage(GatewayMessage.GWMP_V1, gm.token, GatewayMessage.PUSH_ACK, null, null);
                        sock.send(push_ack.getDatagramPacket((InetSocketAddress) packet.getSocketAddress()));

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
                        String gateway = Util.formatEUI(gm.gateway);
                        activity.info("PULL_DATA received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + gateway);

                        // Send PULL_ACK
                        GatewayMessage pull_ack = new GatewayMessage(gm.version, gm.token, GatewayMessage.PULL_ACK, gm.gateway, null);
                        sock.send(pull_ack.getDatagramPacket((InetSocketAddress) packet.getSocketAddress()));

                        // Save UDP port
                        this.gateways.put(gateway, (InetSocketAddress) packet.getSocketAddress());
                        break;
                    }
                    case GatewayMessage.TX_ACK: {
                        activity.info("TX_ACK received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + Util.formatEUI(gm.gateway));
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

        // Find mote into Network Server list
        int index = motes.indexOf(new LoraMote(jr.devEUI,null));
        if (index < 0) {
            activity.warning("Join OTA: mote not found into list");
            return;
        }
        LoraMote mote = motes.get(index);

        // Create Join Accept and encapsulate it in Mac Message
        JoinAccept ja = new JoinAccept(mote.devAddress);
        MACMessage mac_ja = new MACMessage(ja, mote);

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
                mac_ja.getEncryptedJoinAccept(mote.appKey),
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
            return;
        }

        InetSocketAddress gw = gateways.get(gateway);

        // Send Join Accept
        sock.send(gw_ja.getDatagramPacket(gw));

        // Create keys
        mote.createSessionKeys(jr.devNonce, ja.appNonce, ja.netID);
        mote.frameCounterDown = 0;
        mote.frameCounterUp = 0;
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
        int index = motes.indexOf(new LoraMote(null, fm.devAddress));
        if (index < 0) {
            activity.warning("Mote not found into list");
            return;
        }
        LoraMote mote = motes.get(index);

        // Update FrameMessage Counter Up - TODO: Check duplicates!!
        mote.frameCounterUp = fm.counter;

        // Check MIC
        boolean micValid = macMessage.checkIntegrity(mote);

        if (!micValid) {
            activity.warning("NOT VALID MIC");
        }

        if (fm.optLen > 0) {
            activity.info("There are options in the packet: " + new String(Hex.decode(fm.options)));
        }

        // Decrypt payload
        byte[] decrypted = fm.getDecryptedPayload(mote.appSessionKey);
        activity.info(String.format("Received payload (%d bytes): %s", decrypted.length, new String(Hex.encode(decrypted))));
        printCoordinates(decrypted);

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
            sock.send(gw_resp.getDatagramPacket(gw));
            mote.frameCounterDown++;
        }
    }


    /**
     * Print coordinates
     * @param decrypted binary encoded coordinates (two 32-bit float)
     */

    private void printCoordinates(byte[] decrypted) {
        if (decrypted.length < 8) {
            activity.warning("INVALID coordinates: length < 8 bytes");
            return;
        }

        ByteBuffer bb = ByteBuffer.wrap(decrypted).order(ByteOrder.LITTLE_ENDIAN);
        float latitude = bb.getFloat();
        float longitude = bb.getFloat();
        activity.info(String.format("Coordinates: %f %f",latitude,longitude));
        return;
    }


    /**
     * Entry point
     * @param args
     */

    public static void main(String[] args) {
        LoraNetworkServer networkServer = new LoraNetworkServer();
        networkServer.run();
    }
}
