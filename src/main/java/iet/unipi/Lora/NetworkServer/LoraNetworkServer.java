package iet.unipi.Lora.NetworkServer;

import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;


/**
 * Main class of the Lora Network Server
 */

public class LoraNetworkServer implements Runnable {
    private static final int MAX_GATEWAYS = 100;
    private static final int BUFFER_LEN = 2400;
    private static final int UDP_PORT = 1700;

    private static final int RFCH = 0;
    private static final int POWER = 27;
    private static final boolean IPOL = true;
    private static final boolean NCRC = true;

    private static final byte FRAME_PORT = 3;

    // Temporizing
    public static final long RECEIVE_DELAY1 = 1000000; // microsec
    public static final long RECEIVE_DELAY2 = RECEIVE_DELAY1 + 1000000; // microsec
    public static final long JOIN_ACCEPT_DELAY1 = 5000000;
    public static final long JOIN_ACCEPT_DELAY2 = JOIN_ACCEPT_DELAY1 + 1000000;


    // Address and port of gateway for PULL_RESP
    //private InetAddress pull_resp_addr;
    //private int pull_resp_port;

    // List of all known motes - TODO: implement as HashMap
    private List<LoraMote> motes = new ArrayList<LoraMote>();

    // Map of all known gateways
    private Map<String,InetSocketAddress> gateways = new HashMap<String, InetSocketAddress>(MAX_GATEWAYS);


    /**
     * Main Function of the NetworkServer
     * @throws IOException
     */

    public void run() {
        try {
            DatagramSocket sock = new DatagramSocket(UDP_PORT);
            System.out.println("Listening to: " + sock.getLocalAddress().getHostAddress() + " : " + sock.getLocalPort());

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


            // Add mote OTAA join
            motes.add(new LoraMote(
                    "A1B2030405060708",
                    "1112131415161718",
                    "00000085",
                    "01020304050607080910111213141516",
                    "00000000000000000000000000000000",
                    "00000000000000000000000000000000"
            ));


            while (true) {

                // Receive UDP packet and create GWMP data structure
                DatagramPacket packet = new DatagramPacket(new byte[BUFFER_LEN], BUFFER_LEN);
                sock.receive(packet);
                long receiveTime = System.currentTimeMillis();
                GatewayMessage gm = new GatewayMessage(packet.getData());

                switch (gm.type) {
                    case GatewayMessage.PUSH_DATA: {
                        System.out.println("\nPUSH_DATA received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + LoraMote.formatEUI(gm.gateway));

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
                                long tmst = rxpk.getLong("tmst");
                                MACMessage mm = new MACMessage(rxpk.getString("data"));

                                switch (mm.type) {
                                    case MACMessage.JOIN_REQUEST: {
                                        System.out.println("JOIN_REQUEST received");
                                        JoinRequest jr = new JoinRequest(mm);
                                        jr.print();

                                        // Search mote
                                        LoraMote mote = getMotebyDevEUI(jr.devEUI);
                                        if (mote == null) {
                                            System.err.println("Join OTA: mote non trovato");
                                            break;
                                        }

                                        // Create Join Accept and encapsulate it in Mac Message
                                        JoinAccept ja = new JoinAccept(mote.devAddress);
                                        MACMessage mac_ja = new MACMessage(ja, mote);
                                        //System.out.println("Join accept payload: " + new String(Hex.encode(mac_ja.payload)));

                                        // Create json payload of GWMP message
                                        String txpk = GatewayMessage.getTxpk(
                                                false,
                                                tmst + JOIN_ACCEPT_DELAY1,
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

                                        if (!gateways.containsKey(LoraMote.formatEUI(gm.gateway))) {
                                            System.err.println("Gateway PULL_RESP address not found");
                                            break;
                                        }

                                        InetSocketAddress gw = gateways.get(LoraMote.formatEUI(gm.gateway));

                                        // Send Join Accept
                                        sock.send(gw_ja.getDatagramPacket(gw));

                                        // Create keys
                                        mote.createSessionKeys(jr.devNonce, ja.appNonce, ja.netID);
                                        mote.frameCounterDown = 0;
                                        mote.frameCounterUp = 0;
                                        break;
                                    }
                                    case MACMessage.CONFIRMED_DATA_UP: {
                                        FrameMessage fm = new FrameMessage(mm);
                                        System.out.printf(
                                                "CONFIRMED_DATA_UP received from address: %s \tport: %d \tframe counter: %d \tack flag: %d\n",
                                                fm.getDevAddress(),
                                                fm.port,
                                                fm.counter,
                                                fm.getAck()
                                        );

                                        // Find mote into Network Server list
                                        int index = motes.indexOf(new LoraMote(fm.devAddress));
                                        if (index < 0) {
                                            System.err.println("Mote not found into list");
                                            break;
                                        }
                                        LoraMote mote = motes.get(index);

                                        // Update FrameMessage Counter Up
                                        // TODO: Check duplicates!!
                                        mote.frameCounterUp = fm.counter;

                                        // Check MIC
                                        mm.checkIntegrity(mote);

                                        if (fm.optLen > 0) {
                                            System.out.println("There are options in the packet: " + new String(Hex.encode(fm.options)));
                                        }

                                        byte[] decrypted = fm.getDecryptedPayload(mote.appSessionKey);
                                        System.out.println("Received payload: " + new String(Hex.encode(decrypted)));


                                        if (gateways.containsKey(LoraMote.formatEUI(gm.gateway))) {
                                            FrameMessage frame_resp = new FrameMessage(
                                                    fm.devAddress,
                                                    FrameMessage.ACK,
                                                    (short) mote.frameCounterDown,
                                                    null,
                                                    FRAME_PORT,
                                                    Hex.decode("0F0E0D0C"),
                                                    FrameMessage.DOWNSTREAM
                                            );

                                            MACMessage mac_resp = new MACMessage(
                                                    MACMessage.UNCONFIRMED_DATA_DOWN,
                                                    frame_resp,
                                                    mote
                                            );

                                            /*String resp_str = GatewayMessage.getTxpk(
                                                    false,
                                                    tmst + RECEIVE_DELAY1,
                                                    rxpk.getDouble("freq"),
                                                    RFCH,
                                                    POWER,
                                                    rxpk.getString("modu"),
                                                    rxpk.getString("datr"),
                                                    rxpk.getString("codr"),
                                                    IPOL,
                                                    mac_resp.getBytes(),
                                                    NCRC
                                            ); */

                                            String resp_str = GatewayMessage.getTxpk(
                                                    false,
                                                    tmst + RECEIVE_DELAY2,
                                                    869.525,
                                                    RFCH,
                                                    POWER,
                                                    "LORA",
                                                    "SF12BW125",
                                                    "4/5",
                                                    IPOL,
                                                    mac_resp.getBytes(),
                                                    NCRC
                                            );

                                            GatewayMessage gw_resp = new GatewayMessage(
                                                    GatewayMessage.GWMP_V1,
                                                    (short) 0,
                                                    GatewayMessage.PULL_RESP,
                                                    null,
                                                    resp_str
                                            );

                                            InetSocketAddress gw = gateways.get(LoraMote.formatEUI(gm.gateway));
                                            sock.send(gw_resp.getDatagramPacket(gw));
                                            mote.frameCounterDown++;
                                        }

                                        break;
                                    }
                                    case MACMessage.UNCONFIRMED_DATA_UP: {
                                        FrameMessage fm = new FrameMessage(mm);
                                        System.out.printf("UNCONFIRMED_DATA_UP received from address: %s \tport: %d \tframe counter: %d \tack flag: %d\n", fm.getDevAddress(), fm.port, fm.counter, fm.getAck());

                                        // Find mote into Network Server list
                                        int index = motes.indexOf(new LoraMote(fm.devAddress));
                                        if (index < 0) {
                                            System.err.println("Mote not found into list");
                                            break;
                                        }
                                        LoraMote mote = motes.get(index);

                                        // Update FrameMessage Counter Up
                                        // TODO: Check duplicates!!
                                        mote.frameCounterUp = fm.counter;

                                        // Check MIC
                                        mm.checkIntegrity(mote);

                                        if (fm.optLen > 0) {
                                            System.out.println("There are options in the packet");
                                            System.out.println(Arrays.toString(fm.options));
                                        }

                                        // Decrypt payload
                                        byte[] decrypted = fm.getDecryptedPayload(mote.appSessionKey);
                                        System.out.print("Payload: " + Arrays.toString(decrypted));

                                        break;
                                    }
                                    default:
                                        System.out.println("Unknown MAC message type: " + Integer.toBinaryString(mm.type));
                                }
                            }
                        }

                        break;
                    }
                    case GatewayMessage.PULL_DATA: {
                        String gateway = LoraMote.formatEUI(gm.gateway);
                        System.out.println("\nPULL_DATA received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + gateway);

                        // Save address and port
                        //this.pull_resp_addr = packet.getAddress();
                        //this.pull_resp_port = packet.getPort();

                        this.gateways.put(gateway, (InetSocketAddress) packet.getSocketAddress());

                        // Send PULL_ACK
                        GatewayMessage pull_ack = new GatewayMessage(gm.version, gm.token, GatewayMessage.PULL_ACK, gm.gateway, null);
                        sock.send(pull_ack.getDatagramPacket((InetSocketAddress) packet.getSocketAddress()));

                        break;
                    }
                    case GatewayMessage.TX_ACK: {
                        System.out.println("\nTX_ACK received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + LoraMote.formatEUI(gm.gateway));
                        System.out.println(gm.payload);
                        break;
                    }
                    default:
                        System.out.println("Unknown GWMP message type received from: " + packet.getAddress().getHostAddress() + ", Gateway: " + LoraMote.formatEUI(gm.gateway));
                }
                double elapsedTime = ((double) (System.currentTimeMillis() - receiveTime)) / 1000;
                System.out.println(String.format("Elapsed time %f sec", elapsedTime));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     *
     * @param devEUI
     * @return
     */

    private LoraMote getMotebyDevEUI(byte[] devEUI) {
        // Cerco il mote iterativamente. TODO: cercare soluzione più efficente
        LoraMote mote = null;
        for (LoraMote m: motes) {
            if (Arrays.equals(m.devEUI, devEUI)) {
                mote = m;
                break;
            }
        }
        return mote;
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
