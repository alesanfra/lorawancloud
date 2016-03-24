package iet.unipi.Lora.NetworkServer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LoraNetworkServer {
    private static final int BUFFER_LEN = 2400;
    private static final int UDP_PORT = 1700;

    // Temporizing
    private static final long RECEIVE_DELAY1 = 1*1000*1000; // microsec
    private static final long RECEIVE_DELAY2 = RECEIVE_DELAY1 + 1*1000*1000; // microsec

    // Address and port of gateway for PULL_RESP
    private InetAddress pull_resp_addr;
    private int pull_resp_port;

    // List of all known motes
    private List<LoraMote> motes = new ArrayList<LoraMote>();


    private void run() throws IOException {
        // Create socket
        DatagramSocket sock = new DatagramSocket(UDP_PORT);
        System.out.println("Listening to: " + sock.getLocalAddress().getHostAddress() + " : " + sock.getLocalPort());

        // Add one mote (ABP join)
        motes.add(new LoraMote("0102030405060708", "0", "05060708", "", "01020304050607080910111213141516", "000102030405060708090A0B0C0D0E0F"));

        while (true) {
            DatagramPacket packet = new DatagramPacket(new byte[BUFFER_LEN], BUFFER_LEN);

            // Receive packet
            sock.receive(packet);
            long startTime = System.currentTimeMillis();
            GatewayMessage gm = new GatewayMessage(packet.getData());
            System.out.println("\nReceived message from: " + packet.getAddress().toString().substring(1) + ", Gateway: " + Long.toHexString(gm.gateway).toUpperCase());

            switch (gm.type) {
                case GatewayMessage.PUSH_DATA: {
                    System.out.println("PUSH_DATA received");

                    // Send PUSH_ACK
                    GatewayMessage push_ack = new GatewayMessage(GatewayMessage.GWMP_VERSION_1, gm.token, GatewayMessage.PUSH_ACK, 0, null);
                    sock.send(push_ack.getDatagramPacket(packet.getAddress(), packet.getPort()));

                    // Handle PUSH_DATA
                    System.out.println(gm.payload);
                    JSONObject jsonPayload = new JSONObject(gm.payload);

                    if (!jsonPayload.isNull("rxpk")) {
                        JSONArray rxpk = jsonPayload.getJSONArray("rxpk");
                        for (int i = 0; i < rxpk.length(); i++) {
                            JSONObject json = rxpk.getJSONObject(i);
                            String data = json.getString("data");
                            System.out.println(String.format("Timestamp: %d, Size: %d, Data: %s",json.getLong("tmst"),json.getInt("size"),data));

                            MACMessage mm = new MACMessage(data);

                            switch (mm.type) {
                                case MACMessage.JOIN_REQUEST: {
                                    System.out.println("JOIN_REQUEST received");
                                    break;
                                }
                                case MACMessage.CONFIRMED_DATA_UP: {
                                    FrameMessage fm = new FrameMessage(mm);
                                    System.out.println(String.format("CONFIRMED_DATA_UP received from address: %s, port: %d, frame counter: %d", Integer.toHexString(fm.devAddress), fm.port, fm.counter));

                                    // Find mote into Network Server list
                                    int index = motes.indexOf(new LoraMote(fm.devAddress));
                                    if (index < 0) {
                                        System.err.println("Mote not found into list");
                                        break;
                                    }
                                    LoraMote mote = motes.get(index);


                                    // Update Frame Counter Up
                                    // TODO: Check duplicates!!
                                    mote.frameCounterUp = fm.counter;

                                    // Check MIC
                                    mm.checkMIC(mote);

                                    byte[] decrypted = fm.getDecryptedPayload(mote.appSessionKey);
                                    System.out.print("Payload: ");
                                    System.out.println(Arrays.toString(decrypted));


                                    // Provo a mandare dati su RX2
                                    if (pull_resp_addr != null) {
                                        byte[] resp_payload = "Hello".getBytes(StandardCharsets.US_ASCII);
                                        FrameMessage frame_resp = new FrameMessage(fm.devAddress, FrameMessage.ACK, (short) 0, null, 3, resp_payload, FrameMessage.DOWNSTREAM);
                                        MACMessage mac_resp = new MACMessage(MACMessage.UNCONFIRMED_DATA_DOWN, MACMessage.LORAWAN_1, frame_resp, mote);
                                        byte[] resp = mac_resp.getBytes();

                                        long sendTime = json.getLong("tmst") + RECEIVE_DELAY2;
                                        String resp_str = GatewayMessage.getTxpk(false, sendTime, 869.525, 1, 14, "LORA", "SF9BW125", "4/5", false, resp.length, resp);

                                        GatewayMessage gw_resp = new GatewayMessage(gm.version,(short) 0, GatewayMessage.PULL_RESP, 0, resp_str);
                                        sock.send(gw_resp.getDatagramPacket(pull_resp_addr,pull_resp_port));
                                    }


                                    break;
                                }
                                case MACMessage.UNCONFIRMED_DATA_UP: {
                                    FrameMessage fm = new FrameMessage(mm);
                                    System.out.println(String.format("UNCONFIRMED_DATA_UP received from address: %08X \tport: %d \tframe counter: %d \tack flag: %d", fm.devAddress, fm.port, fm.counter, fm.getAck()));

                                    // Find mote into Network Server list
                                    int index = motes.indexOf(new LoraMote(fm.devAddress));
                                    if (index < 0) {
                                        System.err.println("Mote not found into list");
                                        break;
                                    }
                                    LoraMote mote = motes.get(index);

                                    // Update Frame Counter Up
                                    // TODO: Check duplicates!!
                                    mote.frameCounterUp = fm.counter;

                                    // Check MIC
                                    mm.checkMIC(mote);

                                    // Decrypt payload
                                    byte[] decrypted = fm.getDecryptedPayload(mote.appSessionKey);
                                    System.out.print("Payload: ");
                                    System.out.println(Arrays.toString(decrypted));


                                    /*
                                    // Provo a mandare dati
                                    if (pull_resp_addr != null) {
                                        byte[] resp_payload = "hello".getBytes(StandardCharsets.US_ASCII);
                                        FrameMessage frame_resp = new FrameMessage(fm.devAddress,(byte) 0,(byte) 0, null, 3, resp_payload, FrameMessage.DOWNSTREAM);
                                        MACMessage mac_resp = new MACMessage(MACMessage.UNCONFIRMED_DATA_DOWN, MACMessage.LORAWAN_1, frame_resp, mote);
                                        byte[] resp = mac_resp.getBytes();
                                        long sendTime = json.getLong("tmst") + RECEIVE_DELAY1;

                                        //String resp_str = GatewayMessage.getTxpk(true, mm.getDouble("freq"), mm.getInt("rfch"), 14, mm.getString("modu"), mm.getString("datr"), mm.getString("codr"), false, resp.length, resp);
                                        //String resp_str = GatewayMessage.getTxpk(false, sendTime, 868.5, 1, 14, "LORA", mm.getString("datr"), mm.getString("codr"), false, resp.length, resp);
                                        String resp_str = GatewayMessage.getTxpk(false, sendTime, json.getDouble("freq"), json.getInt("rfch"), 14, json.getString("modu"), json.getString("datr"), json.getString("codr"), true, resp.length, resp);

                                        //GatewayMessage gw_resp = new GatewayMessage(gm.version,(short) 0, GatewayMessage.PULL_RESP, 0, "{\"txpk\":{\"modu\":\"LORA\",\"codr\":\"4/5\",\"size\":40,\"data\":\"YQgHBgUAAAADafk4JiwRt2qxxsbXwdqqcSzFy393mmMCIr0WqUzc5Q==\",\"datr\":\"SF7BW125\",\"ipol\":false,\"freq\":868.3,\"powe\":14,\"rfch\":1,\"imme\":true}}");
                                        GatewayMessage gw_resp = new GatewayMessage(gm.version,(short) 0, GatewayMessage.PULL_RESP, 0, resp_str);
                                        //System.out.println(Arrays.toString(gw_resp.getBytes()));

                                        // Aggiungo alla lista da inviare
                                        //mote.pullResp.add(gw_resp);
                                        sock.send(gw_resp.getDatagramPacket(pull_resp_addr,pull_resp_port));
                                        //mote.frameCounterDown++;
                                    }
                                    */

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
                    GatewayMessage pull_ack = new GatewayMessage(gm.version, gm.token, GatewayMessage.PULL_ACK, 0, null);
                    sock.send(pull_ack.getDatagramPacket(packet.getAddress(), packet.getPort()));

                    /*
                    // Send PULL_RESP
                    // TODO: Send only to motes connected to the sender gateway (maybe too complex)
                    for (LoraMote mote: motes) {

                        for (GatewayMessage resp = mote.pullResp.poll(); resp != null; resp = mote.pullResp.poll()) {
                            System.out.println("Send message to mote: " + Long.toHexString(mote.devEUI));
                            sock.send(resp.getDatagramPacket(pull_resp_addr,pull_resp_port));
                            mote.frameCounter++;
                        }

                        // Provo a mandare dati su RX2
                        if (pull_resp_addr != null) {
                            byte[] resp_payload = "a".getBytes(StandardCharsets.US_ASCII);
                            FrameMessage frame_resp = new FrameMessage(mote.devAddress,(byte) 0,(byte) 0, null, 5, resp_payload, FrameMessage.DOWNSTREAM);
                            MACMessage mac_resp = new MACMessage(MACMessage.UNCONFIRMED_DATA_DOWN, MACMessage.LORAWAN_1, frame_resp, mote);
                            byte[] resp = mac_resp.getBytes();
                            String resp_str = GatewayMessage.getTxpk(true, 0, 869.525, 1, 14, "LORA", "SF12BW125", "4/5", false, resp.length, resp);
                            GatewayMessage gw_resp = new GatewayMessage(gm.version,(short) 0, GatewayMessage.PULL_RESP, 0, resp_str);
                            //System.out.println(Arrays.toString(gw_resp.getBytes()));

                            sock.send(gw_resp.getDatagramPacket(pull_resp_addr,pull_resp_port));
                        }
                    }*/

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
            double elapsedTime = ((double)(System.currentTimeMillis() - startTime)) / 1000;
            System.out.println(String.format("Elapsed time %f sec", elapsedTime));
        }

    }

    public static void main(String[] args) {
        try {
            LoraNetworkServer networkServer = new LoraNetworkServer();
            networkServer.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
