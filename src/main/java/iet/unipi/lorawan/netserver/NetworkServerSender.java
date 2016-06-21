package iet.unipi.lorawan.netserver;

import iet.unipi.lorawan.*;
import iet.unipi.lorawan.messages.GatewayMessage;
import iet.unipi.lorawan.messages.MACMessage;

import java.io.*;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;


public class NetworkServerSender implements Runnable {

    // TX Settings
    private static final Channel rx2Channel;
    private static final boolean IPOL = true;
    private static final int TIMEOUT = 2000; // RX_DELAY2

    // Logger
    private static final Logger activity = Logger.getLogger("Network Server Downstream Forwarder: activity");
    private static final String ACTIVITY_FILE = "data/NS_downstream_forwarder_activity.txt";

    // Data structures
    private final Mote mote;
    private final long timestamp;
    private final Channel channel;
    private final InetSocketAddress gateway;

    static {
        rx2Channel = new Channel(869.525,0,27,"LORA","SF12BW125","4/5",true);

        // Init logger
        activity.setLevel(Level.INFO);
        FileHandler activityFile;
        try {
            activityFile = new FileHandler(ACTIVITY_FILE, true);
            activityFile.setFormatter(new SimpleDateFormatter());
            activity.addHandler(activityFile);

            // Change ConsoleHandler behavior
            for (Handler handler : Logger.getLogger("").getHandlers()) {
                if (handler instanceof ConsoleHandler) {
                    handler.setFormatter(new SimpleDateFormatter());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public NetworkServerSender(Mote mote, long timestamp, Channel channel, InetSocketAddress gateway) {
        this.mote = mote;
        this.timestamp = timestamp;
        this.channel = channel;
        this.gateway = gateway;
    }


    @Override
    public void run() {
        // UDP socket to gateway
        DatagramSocket socket;

        // Init datagram socket TODO: is it efficent?
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        }

        // Wait message to send
        MACMessage message = null;

        try {
            message = mote.messages.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (message == null) {
            activity.info("Timeout, no message in queue to send to " + mote.getDevAddress());
            return;
        }


        // If there there is one message in queue, send it
        GatewayMessage response;

        if (mote.rx1Enabled) {
            response = new GatewayMessage(
                    GatewayMessage.GWMP_V1,
                    (short) 0,
                    GatewayMessage.PULL_RESP,
                    null,
                    false,
                    timestamp + Constants.RECEIVE_DELAY1,
                    channel,
                    IPOL,
                    message.getBytes()
            );
        } else {
            response = new GatewayMessage(
                    GatewayMessage.GWMP_V1,
                    (short) 0,
                    GatewayMessage.PULL_RESP,
                    null,
                    false,
                    timestamp + Constants.RECEIVE_DELAY2,
                    rx2Channel,
                    IPOL,
                    message.getBytes()
            );
        }

        try {
            socket.send(response.getPacket(gateway));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
