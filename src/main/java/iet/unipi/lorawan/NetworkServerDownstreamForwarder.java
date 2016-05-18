package iet.unipi.lorawan;

import java.io.*;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.logging.*;


public class NetworkServerDownstreamForwarder implements Runnable {

    private static final int BUFFER_LEN = 2400;
    private static final Logger activity = Logger.getLogger("Network Server Sender: activity");
    private static final String ACTIVITY_FILE = "data/NS_sender_activity.txt";
    private static final boolean RX1_ENABLED = true;

    private final Map<String,LoraMote> motes; // key must be devEUI
    private final Map<String,InetSocketAddress> gateways;


    private final BlockingQueue<MACMessage> messages;

    private DatagramSocket sockGW;

    public NetworkServerDownstreamForwarder(Map<String, LoraMote> motes, Map<String, InetSocketAddress> gateways, BlockingQueue<MACMessage> messages) {
        this.motes = motes;
        this.gateways = gateways;
        this.messages = messages;


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


        // Init datagram socket
        try {
            sockGW = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }


    @Override
    public void run() {

        while (true) {
            try {

                // Wait for new element in queue
                MACMessage message = messages.take();


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
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


        }



    }
}
