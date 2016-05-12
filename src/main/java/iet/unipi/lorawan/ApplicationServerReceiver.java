package iet.unipi.lorawan;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Created by alessio on 11/05/16.
 */

public class ApplicationServerReceiver implements Runnable {

    // Socket
    private final Socket sockNS;
    private final Socket sockCS;

    private final OutputStreamWriter toNS;
    private final InputStreamReader fromNS;
    private final OutputStreamWriter toCS;


    private final String eui;
    private final Map<String,LoraMote> motes;


    public ApplicationServerReceiver(Socket sockNS, Socket sockCS, String eui, Map<String,LoraMote> motes) {
        this.motes = motes;
        this.eui = eui;

        this.sockNS = sockNS;
        OutputStream outputStream = null;
        try {
            outputStream = this.sockNS.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            toNS = new OutputStreamWriter(outputStream, StandardCharsets.US_ASCII);
        }

        InputStream inputStream = null;
        try {
            inputStream = this.sockNS.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            fromNS = new InputStreamReader(inputStream);
        }

        this.sockCS = sockCS;
        outputStream = null;
        try {
            outputStream = this.sockCS.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            toCS = new OutputStreamWriter(outputStream, StandardCharsets.US_ASCII);
        }

    }

    @Override
    public void run() {


    }
}
