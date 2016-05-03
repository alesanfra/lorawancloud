package iet.unipi.Lora.NetworkServer;

import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.FileWriter;
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
    private final static Logger log = Logger.getLogger(LoraNetworkServer.class.getName());
    private static final String LOG_FILE = "data/received.txt";

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
            sock = new DatagramSocket(UDP_PORT);
            System.out.println("Listening to: " + sock.getLocalAddress().getHostAddress() + " : " + sock.getLocalPort());

/*
            log.setUseParentHandlers(false);
            log.setLevel(Level.INFO);
            FileHandler fileTxt = new FileHandler("Logging.txt");
            fileTxt.setFormatter(new LogFormatter());
            log.addHandler(fileTxt);
*/
            // Add one mote (ABP join)
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
                        System.out.println("\nPUSH_DATA received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + Util.formatEUI(gm.gateway));

                        // Send PUSH_ACK
                        GatewayMessage push_ack = new GatewayMessage(GatewayMessage.GWMP_V1, gm.token, GatewayMessage.PUSH_ACK, null, null);
                        sock.send(push_ack.getDatagramPacket((InetSocketAddress) packet.getSocketAddress()));

                        // Handle PUSH_DATA
                        System.out.println(gm.payload);
                        JSONObject jsonPayload = new JSONObject(gm.payload);

                        if (!jsonPayload.isNull("rxpk")) {
                            JSONArray rxpkArray = jsonPayload.getJSONArray("rxpk");
                            for (int i = 0; i < rxpkArray.length(); i++) {
                                JSONObject rxpk = rxpkArray.getJSONObject(i);

                                if (rxpk.getInt("stat") != 1) {
                                    System.out.println("CRC not valid, skip packet");
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
                                        System.out.println("Unknown MAC message type: " + Integer.toBinaryString(mm.type));
                                }
                            }

                            try (BufferedWriter log = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
                                log.append(gm.payload).append("\n"); // Scrivo il Log
                            }

                            //log.info(gm.payload);
                        }

                        break;
                    }
                    case GatewayMessage.PULL_DATA: {
                        String gateway = Util.formatEUI(gm.gateway);
                        System.out.println("\nPULL_DATA received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + gateway);

                        // Send PULL_ACK
                        GatewayMessage pull_ack = new GatewayMessage(gm.version, gm.token, GatewayMessage.PULL_ACK, gm.gateway, null);
                        sock.send(pull_ack.getDatagramPacket((InetSocketAddress) packet.getSocketAddress()));

                        // Save UDP port
                        this.gateways.put(gateway, (InetSocketAddress) packet.getSocketAddress());
                        break;
                    }
                    case GatewayMessage.TX_ACK: {
                        System.out.println("\nTX_ACK received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + Util.formatEUI(gm.gateway));
                        System.out.println(gm.payload);
                        break;
                    }
                    default:
                        System.out.println("Unknown GWMP message type received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + Util.formatEUI(gm.gateway));
                }
                double elapsedTime = ((double) (System.nanoTime() - receiveTime)) / 1000000;
                System.out.println(String.format("Elapsed time %f ms", elapsedTime));
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
        System.out.println("JOIN_REQUEST received");
        JoinRequest jr = new JoinRequest(macMessage);
        jr.print();

        // Find mote into Network Server list
        int index = motes.indexOf(new LoraMote(jr.devEUI,null));
        if (index < 0) {
            System.err.println("Join OTA: mote not found into list");
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
            System.err.println("Gateway PULL_RESP address not found");
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

        System.out.printf(
                "%s received from address: %s \tport: %d \tframe counter: %d \tack flag: %d\n",
                type,
                fm.getDevAddress(),
                fm.port,
                fm.counter,
                fm.getAck()
        );

        // Find mote into Network Server list
        int index = motes.indexOf(new LoraMote(null, fm.devAddress));
        if (index < 0) {
            System.err.println("Mote not found into list");
            return;
        }
        LoraMote mote = motes.get(index);

        // Update FrameMessage Counter Up - TODO: Check duplicates!!
        mote.frameCounterUp = fm.counter;

        // Check MIC
        macMessage.checkIntegrity(mote);

                /*
        System.out.printf("Received MIC: %s, Calculated MIC: %s", new String(Hex.encode(this.MIC)), new String(Hex.encode(calculatedMIC)));
        if (validMIC) {
            System.out.println(" ==> VALID MIC");
        } else {
            System.out.println(" ==> NOT VALID MIC");
        }
        */

        if (fm.optLen > 0) {
            System.out.println("There are options in the packet");
            System.out.println(Arrays.toString(fm.options));
        }

        // Decrypt payload
        byte[] decrypted = fm.getDecryptedPayload(mote.appSessionKey);
        System.out.printf("Received payload (%d bytes): %s\n", decrypted.length, new String(Hex.encode(decrypted)));
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
            return;
        }

        ByteBuffer bb = ByteBuffer.wrap(decrypted).order(ByteOrder.LITTLE_ENDIAN);
        float latitude = bb.getFloat();
        float longitude = bb.getFloat();
        System.out.printf("Coordinates: %f %f\n",latitude,longitude);
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
