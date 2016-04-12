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
    private static final int POWER = 15;

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
    public static final byte[] NETWORK_ID = Hex.decode("020100");


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
                            String data = rxpk.getString("data");
                            long tmst = rxpk.getLong("tmst");
                            //System.out.println(String.format("Timestamp: %d, Size: %d, Data: %s",tmst,rxpk.getInt("size"),data));

                            MACMessage mm = new MACMessage(data);

                            switch (mm.type) {
                                case MACMessage.JOIN_REQUEST: {
                                    System.out.println("JOIN_REQUEST received");
                                    JoinRequest jr = new JoinRequest(mm);
                                    jr.print();

                                    // Cerco il mote
                                    // TODO: cercare soluzione più efficente
                                    LoraMote mote = null;
                                    for (LoraMote m: motes) {
                                        if (Arrays.equals(m.devEUI,jr.devEUI)) {
                                            mote = m;
                                            break;
                                        }
                                    }

                                    if (mote == null) {
                                        System.err.println("Join OTA: mote non trovato");
                                        break;
                                    }

                                    JoinAccept ja = new JoinAccept(mote.devAddress);
                                    MACMessage mac_ja = new MACMessage(ja,mote);
                                    String txpk = GatewayMessage.getTxpk(false,tmst+JOIN_ACCEPT_DELAY1, rxpk.getDouble("freq"), RFCH, POWER, rxpk.getString("modu"), rxpk.getString("datr"), rxpk.getString("codr"), false, mac_ja.getEncryptedJoinAccept(mote.appKey));
                                    GatewayMessage gw_ja = new GatewayMessage(GatewayMessage.GWMP_V1, (short) 0,GatewayMessage.PULL_RESP,null,txpk);
                                    sock.send(gw_ja.getDatagramPacket(pull_resp_addr,pull_resp_port));

                                    // Creo le chiavi
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
                                        MACMessage mac_resp = new MACMessage(MACMessage.CONFIRMED_DATA_DOWN, frame_resp, mote);
                                        //String resp_str = GatewayMessage.getTxpk(false, tmst + RECEIVE_DELAY1 , rxpk.getDouble("freq"), RFCH, POWER, rxpk.getString("modu"), rxpk.getString("datr"), rxpk.getString("codr"), false, mac_resp.getBytes());
                                        String resp_str = GatewayMessage.getTxpk(false, tmst + RECEIVE_DELAY2, 869.525, RFCH, POWER, "LORA", "SF12BW125", "4/5", false, mac_resp.getBytes());

                                        GatewayMessage gw_resp = new GatewayMessage(GatewayMessage.GWMP_V1, (short) 0, GatewayMessage.PULL_RESP, null, resp_str);
                                        //sock.send(gw_resp.getDatagramPacket(pull_resp_addr, pull_resp_port));
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


                                    /*
                                    // Provo a mandare dati su RX1
                                    if (pull_resp_addr != null) {
                                        byte[] resp_payload = "hello".getBytes(StandardCharsets.US_ASCII);
                                        FrameMessage frame_resp = new FrameMessage(fm.devAddress,(byte) 0,(byte) 0, null, 3, resp_payload, FrameMessage.DOWNSTREAM);
                                        MACMessage mac_resp = new MACMessage(MACMessage.UNCONFIRMED_DATA_DOWN, frame_resp, mote);
                                        byte[] resp = mac_resp.getBytes();

                                        long sendTime = rxpk.getLong("tmst") + RECEIVE_DELAY1;
                                        String resp_str = GatewayMessage.getTxpk(false, sendTime, rxpk.getDouble("freq"), RFCH, POWER, rxpk.getString("modu"), rxpk.getString("datr"), rxpk.getString("codr"), false, resp.length, resp);

                                        GatewayMessage gw_resp = new GatewayMessage(gm.version,(short) 0, GatewayMessage.PULL_RESP, 0, resp_str);
                                        //System.out.println(Arrays.toString(gw_resp.getBytes()));

                                        sock.send(gw_resp.getDatagramPacket(pull_resp_addr,pull_resp_port));

                                        // Aggiungo alla lista da inviare
                                        //mote.pullResp.add(gw_resp);
                                        //mote.frameCounterDown++;
                                    }
                                    // Provo a mandare dati su RX2
                                    if (pull_resp_addr != null) {
                                        byte[] resp_payload = "hello".getBytes(StandardCharsets.US_ASCII);
                                        //byte[] options = FrameMessage.getRXParamSetupReq(0,0,869525000);

                                        FrameMessage frame_resp = new FrameMessage(fm.devAddress, (byte) 0, (short) 0, null, 3, resp_payload, FrameMessage.DOWNSTREAM);
                                        MACMessage mac_resp = new MACMessage(MACMessage.UNCONFIRMED_DATA_DOWN, frame_resp, mote);
                                        byte[] resp = mac_resp.getBytes();

                                        long sendTime = rxpk.getLong("tmst") + RECEIVE_DELAY2;
                                        String resp_str = GatewayMessage.getTxpk(false, sendTime, 869.525, RFCH, POWER, "LORA", "SF12BW125", "4/5", false, resp);

                                        GatewayMessage gw_resp = new GatewayMessage(gm.version,(short) 0, GatewayMessage.PULL_RESP, 0, resp_str);
                                        sock.send(gw_resp.getDatagramPacket(pull_resp_addr,pull_resp_port));
                                    }*/

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
