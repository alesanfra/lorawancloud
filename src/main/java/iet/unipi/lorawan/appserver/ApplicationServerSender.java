package iet.unipi.lorawan.appserver;


import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class ApplicationServerSender implements Runnable {

    private final Application application;
    private final OutputStreamWriter netServer;

    public ApplicationServerSender(Application application) {
        this.application = application;

        OutputStream outputStream = null;
        try {
            outputStream = application.socket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            netServer = new OutputStreamWriter(outputStream, StandardCharsets.US_ASCII);
        }
    }

    @Override
    public void run() {

        while (true) {
            try {
                DownstreamMessage message = application.messages.take();
                netServer.write(message.toJSONString());
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
