package iet.unipi.lorawan.appserver;


import iet.unipi.lorawan.Constants;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApplicationServerListener implements Runnable {
    private final Application application;
    private final ServerSocket listener;
    private final ExecutorService executor = Executors.newFixedThreadPool(Constants.MAX_HANDLERS);

    public ApplicationServerListener(Application application) throws IOException {
        this.application = application;
        listener = new ServerSocket(application.port);




    }

    @Override
    public void run() {
        while (true) {
            try {
                // Accept new Application Server
                Socket socket = listener.accept();
                executor.execute(new ApplicationServerHandler(application,socket));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
