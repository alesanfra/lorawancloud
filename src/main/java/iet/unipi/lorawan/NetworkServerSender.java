package iet.unipi.lorawan;

import org.bouncycastle.util.encoders.Hex;
import org.json.JSONObject;

import java.io.*;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.*;

/**
 * Created by alessio on 17/05/16.
 */
public class NetworkServerSender implements Runnable {

    private static final int BUFFER_LEN = 2400;
    private static final Logger activity = Logger.getLogger("Network Server Sender: activity");
    private static final String ACTIVITY_FILE = "data/NS_sender_activity.txt";
    private static final boolean RX1_ENABLED = true;

    private final Map<String,LoraMote> motes;
    private final Map<String,InetSocketAddress> gateways;
    private final Socket sockAS;

    private DatagramSocket sockGW;
    private BufferedReader fromAS;

    public NetworkServerSender(Map<String,LoraMote> motes, Map<String,InetSocketAddress> gateways, Socket sockAS) {
        this.motes = motes;
        this.gateways = gateways;
        this.sockAS = sockAS;


        // Init logger
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

        // Init reader
        try {
            fromAS = new BufferedReader(new InputStreamReader(sockAS.getInputStream(), StandardCharsets.US_ASCII));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }


        // Init datagram socket
        try {
            sockGW = new DatagramSocket();
            activity.info("Listening to: " + sockGW.getLocalAddress().getHostAddress() + " : " + sockGW.getLocalPort());
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }


    @Override
    public void run() {

        while (true) {

            try {
                // Ricevo pacchetto dal AS
                String line = fromAS.readLine();

                // Costrisco il frame
                JSONObject msg = new JSONObject(line).getJSONObject("app");
                JSONObject userdata = msg.getJSONObject("userdata");
                String devEUI = msg.getString("moteeui");
                LoraMote mote = motes.get(devEUI);

                FrameMessage frameResp = new FrameMessage(
                        mote.devAddress,
                        FrameMessage.ACK,
                        (short) msg.get("seqno"),
                        null,
                        userdata.getInt("port"),
                        Hex.decode(userdata.getString("payload")),
                        FrameMessage.DOWNSTREAM
                );

                MACMessage mac_resp = new MACMessage(
                        MACMessage.UNCONFIRMED_DATA_DOWN, // Non c'è nel protocollo
                        frameResp,
                        mote
                );

                String txpk;

                if (RX1_ENABLED) {
                    txpk = GatewayMessage.getTxpk(
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

                // Mando il frame al gateway
                InetSocketAddress gw = gateways.get(gateway);
                sockGW.send(gw_resp.getPacket(gw));
                mote.frameCounterDown++;



            } catch (IOException e) {
                e.printStackTrace();
            }



        }



    }
}
