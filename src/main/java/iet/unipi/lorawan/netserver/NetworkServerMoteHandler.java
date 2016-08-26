package iet.unipi.lorawan.netserver;

import iet.unipi.lorawan.*;
import iet.unipi.lorawan.messages.Frame;
import iet.unipi.lorawan.messages.GatewayMessage;
import iet.unipi.lorawan.messages.MacCommand;
import iet.unipi.lorawan.messages.Packet;
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
    private static final int TIMEOUT = 2000; // RX_DELAY2

    // Logger
    private static final Logger activity = Logger.getLogger("Network Server Mote Handler: activity");
    private static final String ACTIVITY_FILE = "data/NS_downstream_forwarder_activity.txt";

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
    private final Map<String, Socket> appServers;



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
            Map<String,Socket> appServers
    ) {
        this.message = message;
        this.gateway = gateway;
        this.gatewayAddr = gatewayAddr;
        this.motes = motes;
        this.appServers = appServers;
    }


    @Override
    public void run() {

        /*** PARSE MESSAGE ***/

        if (message.getInt("stat") != 1) {
            activity.warning("CRC not valid, skip packet");
            return;
        }

        long timestamp = message.getLong("tmst");

        Packet mm = new Packet(message.getString("data"));
        Frame fm = new Frame(mm);
        Mote mote = motes.get(fm.getDevAddress());

        // Authentication => check mic
        if (!mm.checkIntegrity(mote)) {
            activity.warning(fm.getDevAddress() + ": MIC NOT VALID");
        }

        // Forward message to Application Server
        Socket toAS = appServers.get(mote.getAppEUI());

        if (toAS == null) {
            activity.warning("App server NOT found");
        } else {
            String appserverMessage = buildAppserverMessage(gateway,message,mm.type,fm);

            try(OutputStreamWriter out = new OutputStreamWriter(toAS.getOutputStream(), StandardCharsets.US_ASCII)) {
                out.write(appserverMessage);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        mote.updateStatistics(fm.counter); // Update mote statistics
        activity.info(mote.printStatistics());
        /*** END PARSE MESSAGE ***/


        /*** HANDLE MAC COMMANDS ***/
        if (fm.options.length > 0) {
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

        // UDP socket to gateway
        DatagramSocket socket;
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            return;
        }

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

        Packet packet = buildDownstreamMessage(answer, mote, (mm.type == Packet.CONFIRMED_DATA_UP));

        // Create sender task
        Channel channel = new Channel(
                message.getDouble("freq"),
                0,
                27,
                message.getString("modu"),
                message.getString("datr"),
                message.getString("codr"),
                false
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
                    packet.getBytes()
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
                    packet.getBytes()
            );
        }

        try {
            socket.send(response.getPacket(gatewayAddr));
        } catch (IOException e) {
            e.printStackTrace();
        }


        /*** END SEND DOWNSTREAM MESSAGE ***/
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

        Packet packet = new Packet(
                Packet.UNCONFIRMED_DATA_DOWN, // Non c'è nel protocollo di semtech
                new Frame(
                        mote.devAddress,
                        ack,
                        (short) msg.get("seqno"),
                        null,
                        userdata.getInt("port"),
                        payload,
                        Frame.DOWNSTREAM
                ),
                mote
        );
        mote.incrementFrameCounterDown(); // TODO: controllare che così funzioni
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
                activity.warning("DATA UP not implemented yet");
                JSONObject userdata = new JSONObject();
                userdata.put("seqno",fm.counter);
                userdata.put("port",fm.port);
                userdata.put("payload",fm.payload);

                JSONObject motetx = new JSONObject();
                motetx.put("freq",rxpk.getInt("freq"));
                motetx.put("modu",rxpk.getString("modu"));
                motetx.put("codr",rxpk.getString("codr"));
                motetx.put("adr", fm.getAdr());

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

        return message.toString();
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



