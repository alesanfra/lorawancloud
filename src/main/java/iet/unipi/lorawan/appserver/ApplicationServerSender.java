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
    private final Application application;
    private final PrintWriter socket;

    public ApplicationServerSender(Application application) throws IOException {
        this.application = application;
        Socket socket = new Socket(Constants.NETSERVER_ADDRESS, Constants.NETSERVER_LISTENING_PORT);
        this.socket = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
    }

    @Override
    public void run() {
        // Send app eui to netserver
        application.log.info("Start AppServer Sender: " + application.name + ", eui: " + application.eui);
        JSONObject appServer = new JSONObject();
        appServer.put("appeui",application.eui);
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
                socket.flush();
                //application.log.info("Message sent: " + message.toJSONString());
            }
        } catch (InterruptedException e) {
            e.printStackTrace();

        }
    }
}
