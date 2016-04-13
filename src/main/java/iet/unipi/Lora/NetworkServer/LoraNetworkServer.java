package iet.unipi.Lora.NetworkServer;

import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Main class of the Lora Network Server
 */

public class LoraNetworkServer {
    private static final int BUFFER_LEN = 2400;
    private static final int UDP_PORT = 1700;
    private static final int RFCH = 0;
    private static final int POWER = 27;
    private static final boolean IPOL = true;
    private static final boolean NCRC = true;

    // Temporizing
    public static final long RECEIVE_DELAY1 = 1000000; // microsec
    public static final long RECEIVE_DELAY2 = RECEIVE_DELAY1 + 1000000; // microsec
    public static final long JOIN_ACCEPT_DELAY1 = 5000000;
    public static final long JOIN_ACCEPT_DELAY2 = JOIN_ACCEPT_DELAY1 + 1000000;


    // Address and port of gateway for PULL_RESP
    private InetAddress pull_resp_addr;
    private int pull_resp_port;

    // List of all known motes
    // TODO: implement as HashMap
    private List<LoraMote> motes = new ArrayList<LoraMote>();


    private static final byte FRAME_PORT = 3;
    public static final byte[] NETWORK_ID = Hex.decode("000000");


    /**
     * Main Function of the NetworkServer
     * @throws IOException
     */

    private void run() throws IOException {
        DatagramSocket sock = new DatagramSocket(UDP_PORT);
        System.out.println("Listening to: " + sock.getLocalAddress().getHostAddress() + " : " + sock.getLocalPort());

        // Add one mote (ABP join)
        motes.add(new LoraMote("A1B2C3D400000000", "1112131415161718", "A1B20000", "00000000000000000000000000000000", "01020304050607080910111213141516", "000102030405060708090A0B0C0D0E0F"));
        motes.add(new LoraMote("A1B2C3D400000001", "1112131415161718", "A1B20001", "00000000000000000000000000000000", "01020304050607080910111213141516", "000102030405060708090A0B0C0D0E0F"));


        // Add mote OTAA join
        motes.add(new LoraMote("A1B2030405060708", "1112131415161718", "00000085", "01020304050607080910111213141516", "00", "00"));


        //debugMIC();

        while (true) {
            DatagramPacket packet = new DatagramPacket(new byte[BUFFER_LEN], BUFFER_LEN);

            // Receive packet
            sock.receive(packet);
            long receiveTime = System.currentTimeMillis();
            GatewayMessage gm = new GatewayMessage(packet.getData());
            System.out.println("\nReceived message from: " + packet.getAddress().getHostAddress() + ", Gateway: " + LoraMote.formatEUI(gm.gateway));

            switch (gm.type) {
                case GatewayMessage.PUSH_DATA: {
                    System.out.println("PUSH_DATA received");

                    // Send PUSH_ACK
                    GatewayMessage push_ack = new GatewayMessage(GatewayMessage.GWMP_V1, gm.token, GatewayMessage.PUSH_ACK, null, null);
                    sock.send(push_ack.getDatagramPacket(packet.getAddress(), packet.getPort()));

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
                                    MACMessage mac_ja = new MACMessage(ja,mote);
                                    System.out.println("Join accept payload: " + new String(Hex.encode(mac_ja.payload)));

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

                                    // Send Join Accept
                                    sock.send(gw_ja.getDatagramPacket(pull_resp_addr,pull_resp_port));

                                    // Create keys
                                    mote.createSessionKeys(jr.devNonce, ja.appNonce, ja.netID);
                                    mote.frameCounterDown = 0;
                                    mote.frameCounterUp = 0;
                                    break;
                                }
                                case MACMessage.CONFIRMED_DATA_UP: {
                                    FrameMessage fm = new FrameMessage(mm);
                                    System.out.printf("CONFIRMED_DATA_UP received from address: %s \tport: %d \tframe counter: %d \tack flag: %d\n", fm.getDevAddress(), fm.port, fm.counter, fm.getAck());

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
                                        System.out.println("There are options in the packet: " + Arrays.toString(fm.options));
                                    }

                                    byte[] decrypted = fm.getDecryptedPayload(mote.appSessionKey);
                                    System.out.println("Received payload: " + Arrays.toString(decrypted));


                                    if (pull_resp_addr != null) {
                                        FrameMessage frame_resp = new FrameMessage(fm.devAddress, FrameMessage.ACK, (short) mote.frameCounterDown, null, FRAME_PORT, null, FrameMessage.DOWNSTREAM);
                                        MACMessage mac_resp = new MACMessage(MACMessage.UNCONFIRMED_DATA_DOWN, frame_resp, mote);

                                        String resp_str = GatewayMessage.getTxpk(
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
                                        );

                                        /*
                                        String resp_str = GatewayMessage.getTxpk(
                                                false,
                                                tmst + RECEIVE_DELAY2,
                                                869.525000,
                                                RFCH,
                                                POWER,
                                                "LORA",
                                                "SF9BW125",
                                                "4/5",
                                                true,
                                                mac_resp.getBytes(),
                                                true
                                        );*/

                                        GatewayMessage gw_resp = new GatewayMessage(GatewayMessage.GWMP_V1, (short) 0, GatewayMessage.PULL_RESP, null, resp_str);
                                        sock.send(gw_resp.getDatagramPacket(pull_resp_addr, pull_resp_port));
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
                                    System.out.print("Payload: "+ Arrays.toString(decrypted));

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
                    System.out.println("PULL_DATA received");

                    // Save address and port
                    this.pull_resp_addr = packet.getAddress();
                    this.pull_resp_port = packet.getPort();

                    // Send PULL_ACK
                    GatewayMessage pull_ack = new GatewayMessage(gm.version, gm.token, GatewayMessage.PULL_ACK, gm.gateway, null);
                    sock.send(pull_ack.getDatagramPacket(packet.getAddress(), packet.getPort()));

                    break;
                }
                case GatewayMessage.TX_ACK: {
                    System.out.println("TX_ACK received");
                    System.out.println(gm.payload);
                    break;
                }
                default:
                    System.out.println("Unknown GWMP message type received");
            }
            double elapsedTime = ((double)(System.currentTimeMillis() - receiveTime)) / 1000;
            System.out.println(String.format("Elapsed time %f sec", elapsedTime));
        }

    }

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


    private void debugMIC() {
        LoraMote otaa = new LoraMote("01C2030405060708", "00", "0091F30E", "00", "806AD892D14E93C4EA2EA50F47BEFC21", "548567462AED641486E424B6A28F0EB8");
        otaa.frameCounterDown = 0x0128;

        FrameMessage frameresp = new FrameMessage(otaa.devAddress, FrameMessage.ACK, (short) 0x0128, null, 3, null, FrameMessage.DOWNSTREAM);
        MACMessage macresp = new MACMessage(MACMessage.UNCONFIRMED_DATA_DOWN, frameresp, otaa);

        byte[] tutto = macresp.getBytes();

        for (byte b: tutto) {
            System.out.print(Integer.toHexString(b & 0xFF) + " ");
        }

        //System.out.println(new String(Hex.encode(tutto)));

        System.exit(0);
    }



    /**
     * Entry point
     * @param args
     */

    public static void main(String[] args) {
        try {
            LoraNetworkServer networkServer = new LoraNetworkServer();
            networkServer.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
