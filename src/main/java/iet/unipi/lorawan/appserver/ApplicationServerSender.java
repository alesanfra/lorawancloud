package iet.unipi.lorawan.appserver;


import iet.unipi.lorawan.Constants;
import iet.unipi.lorawan.SimpleDateFormatter;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.*;

public class ApplicationServerSender implements Runnable {
    private static final String FILE_HEADER = "data/AS_";

    private final Logger activity;
    private final Application application;
    private final PrintWriter socket;

    static {
        // Change ConsoleHandler behavior
        for (Handler handler: Logger.getLogger("").getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setFormatter(new SimpleDateFormatter());
            }
        }
    }

    public ApplicationServerSender(Application application) throws IOException {
        this.application = application;
        Socket socket = new Socket(Constants.NETSERVER_ADDRESS, Constants.NETSERVER_LISTENING_PORT);
        this.socket = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));

        String appEui = new String(Hex.encode(application.eui));

        // Init logger
        this.activity = Logger.getLogger("Application Server: " + appEui);
        activity.setLevel(Level.INFO);

        try {
            FileHandler activityFile = new FileHandler(FILE_HEADER + appEui + ".txt", true);
            activityFile.setFormatter(new SimpleDateFormatter());
            activity.addHandler(activityFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        // Send app eui to netserver
        String appeui = new String(Hex.encode(application.eui));
        activity.info("Start AppServer Sender: " + application.name + ", eui: " + appeui);
        JSONObject appServer = new JSONObject();
        appServer.put("appeui",appeui);
        appServer.put("addr",application.address);
        appServer.put("port",application.port);
        JSONObject hello = new JSONObject();
        hello.put("appserver",appServer);

        socket.println(hello.toString());
        socket.flush();

        try {
            while (true) {
                DownstreamMessage message = application.messages.take();
                socket.println(message.toJSONString());
                activity.info("Message sent: " + message.toJSONString());
            }
        } catch (InterruptedException e) {
            e.printStackTrace();

        }
    }
}
