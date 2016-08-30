package iet.unipi.lorawan.netserver;

import iet.unipi.lorawan.*;
import iet.unipi.lorawan.messages.Frame;
import iet.unipi.lorawan.messages.GatewayMessage;
import iet.unipi.lorawan.messages.MacCommand;
import iet.unipi.lorawan.messages.Packet;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;


public class NetworkServerMoteHandler implements Runnable {

    // TX Settings
    private static final Channel rx2Channel;
    private static final boolean IPOL = true;
    private static final int TIMEOUT = 700; // RX_DELAY2

    // Logger
    private static final Logger activity = Logger.getLogger("Network Server Mote Handler");
    private static final String ACTIVITY_FILE = Constants.NETSERVER_LOG_PATH + "NS_mote_handler.txt";

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

    private final JSONObject message;
    private final String gateway;
    private final InetSocketAddress gatewayAddr;
    private final MoteCollection motes;
    private final Map<String, AppServer> appServers;
    private final DatagramSocket socket;


    /**
     * Constructor
     * @param message
     * @param gatewayAddr
     * @param motes
     * @param appServers
     */

    public NetworkServerMoteHandler(
            JSONObject message,
            String gateway,
            InetSocketAddress gatewayAddr,
            MoteCollection motes,
            Map<String,AppServer> appServers,
            DatagramSocket socket
    ) {
        this.message = message;
        this.gateway = gateway;
        this.gatewayAddr = gatewayAddr;
        this.motes = motes;
        this.appServers = appServers;
        this.socket = socket;
    }


    @Override
    public void run() {
        if (message.getInt("stat") != 1) {
            activity.warning("CRC not valid, skip packet");
            return;
        }

        Packet packet = new Packet(message.getString("data"));

        switch (packet.type) {
            case Packet.JOIN_REQUEST:
                handleJoin(packet);
                break;
            case Packet.CONFIRMED_DATA_UP:
            case Packet.UNCONFIRMED_DATA_UP:
                handleMessage(packet);
                break;
            case Packet.RELAY_UNCONFIRMED_DATA_UP:
                activity.info("Relayed message, skip packet");
                break;
            default:
                activity.warning("Message type not recognized");
        }
    }


    private void handleJoin(Packet packet) {

    }


    /**
     * Handles upstream messages
     * @param packet
     */

    private void handleMessage(Packet packet) {
        long timestamp = message.getLong("tmst");
        Frame fm = new Frame(packet);
        Mote mote = motes.get(fm.getDevAddress());

        if (mote == null) {
            activity.warning(fm.getDevAddress() + ": Mote not found");
            return;
        }

        // Authentication => check mic
        if (!packet.checkIntegrity(mote,fm.counter)) {
            activity.warning(fm.getDevAddress() + ": MIC not valid");
            return;
        }

        //activity.info("Frame: " +  new String(Hex.encode(packet.getBytes())));

        // Forward message to Application Server
        AppServer appServer = appServers.get(mote.getAppEUI());

        if (appServer == null) {
            activity.warning("App server NOT found");
        } else {
            String appserverMessage = buildAppserverMessage(gateway,message,packet.type,fm);
            try(Socket toAS = new Socket(appServer.address, appServer.port)) {
                PrintWriter out = new PrintWriter(new OutputStreamWriter(toAS.getOutputStream(), StandardCharsets.US_ASCII));
                out.println(appserverMessage);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        mote.updateStatistics(fm.counter); // Update mote statistics
        activity.info(mote.printStatistics());


        /*** HANDLE MAC COMMANDS ***/
        /*if (fm.options.length > 0) {
            // There is one mac command in clear
            byte[] ans = handleMacCommand(fm.options);
            if (ans != null) {
                mote.commands.add(ans);
            }
        } else if (fm.port == 0) {
            // there is
        }
        /*** END HANDLE MAC COMMANDS ***/



        /*** SEND DOWNSTREAM MESSAGE ***/
        // Wait message to send
        String answer;
        try {
            answer = mote.messages.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }

        if (answer == null) {
            activity.info("Timeout, no message in queue to send to " + mote.getDevAddress());
            return;
        }

        Packet ansPacket = buildDownstreamMessage(answer, mote, (packet.type == Packet.CONFIRMED_DATA_UP));

        Channel channel = new Channel(
                message.getDouble("freq"),
                0,
                27,
                message.getString("modu"),
                message.getString("datr"),
                message.getString("codr"),
                true
        );


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
                    ansPacket.getBytes()
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
                    ansPacket.getBytes()
            );
        }

        //activity.info(response.payload);

        try {
            socket.send(response.getPacket(gatewayAddr));
            activity.info("Sent message to mote: " + mote.getDevAddress());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    /**
     * Build the downstream message
     * @param line
     * @param mote
     * @param sendAck
     * @return
     */

    private Packet buildDownstreamMessage(String line, Mote mote, boolean sendAck) {
        JSONObject msg = new JSONObject(line).getJSONObject("app");
        JSONObject userdata = msg.getJSONObject("userdata");
        byte[] payload = Base64.getDecoder().decode(userdata.getString("payload").getBytes());
        byte ack = (sendAck)? Frame.ACK : 0;

        short frameCounterDown = (short) (msg.getInt("seqno") & 0xFFFF);

        //activity.info("Payload downstream: " + new String(Hex.encode(payload)));

        Packet packet = new Packet(
                Packet.UNCONFIRMED_DATA_DOWN, // Non c'è nel protocollo di semtech
                new Frame(
                        mote.devAddress,
                        ack,
                        frameCounterDown,
                        null,
                        userdata.getInt("port"),
                        payload,
                        Frame.DOWNSTREAM
                ),
                mote
        );
        mote.setFrameCounterDown(frameCounterDown);
        //activity.info("Downstream: " + new String(Hex.encode(packet.getBytes())));
        return packet;
    }


    /**
     * Create json message for the app server
     * @param gateway
     * @param rxpk
     * @param type
     * @param fm
     * @return
     */

    private String buildAppserverMessage(String gateway, JSONObject rxpk, int type, Frame fm) {
        JSONObject message = new JSONObject();

        switch (type) {
            case Packet.JOIN_REQUEST:
                activity.warning("JOIN REQUEST not implemented yet");
                break;

            case Packet.CONFIRMED_DATA_UP:
            case Packet.UNCONFIRMED_DATA_UP: {
                JSONObject userdata = new JSONObject();
                userdata.put("seqno",fm.counter);
                userdata.put("port",fm.port);
                userdata.put("payload", Base64.getEncoder().encodeToString(fm.payload));

                JSONObject motetx = new JSONObject();
                motetx.put("freq",rxpk.getInt("freq"));
                motetx.put("modu",rxpk.getString("modu"));
                motetx.put("codr",rxpk.getString("codr"));
                motetx.put("adr", fm.getADR());

                userdata.put("motetx",motetx);

                JSONObject gwrx = new JSONObject();
                gwrx.put("eui",gateway);
                gwrx.put("time", rxpk.getString("time"));
                gwrx.put("timefromgateway",true);
                gwrx.put("chan",rxpk.getInt("chan"));
                gwrx.put("rfch",rxpk.getInt("rfch"));
                gwrx.put("rssi",rxpk.getInt("rssi"));
                gwrx.put("lsnr",rxpk.getInt("lsnr"));

                JSONArray gwrxArray = new JSONArray();
                gwrxArray.put(gwrx);

                JSONObject app = new JSONObject();
                app.put("moteeui",motes.get(fm.getDevAddress()).getDevEUI().toLowerCase());
                app.put("dir","up");
                app.put("userdata",userdata);
                app.put("gwrx",gwrxArray);

                message.put("app",app);

                break;
            }
            default:
                activity.warning("Unknown message type");
        }

        String msg = message.toString();

        activity.info(msg);
        return msg;
    }


    private byte[] handleMacCommand(byte[] command) {
        switch (command[0] & 0xFF) {
            case MacCommand.RelaySetupReq:
                break;
            default:
        }

        return null;
    }
}



