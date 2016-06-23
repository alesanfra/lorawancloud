package iet.unipi.lorawan.appserver;

import iet.unipi.lorawan.Mote;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;


public class ApplicationServerReceiver implements Runnable {

/*
    private final OutputStreamWriter toNS;
    private final InputStreamReader fromNS;
    private final OutputStreamWriter toCS;
*/

    private final Application application;


    public ApplicationServerReceiver(Application application) {
        this.application = application;
/*

        //this.sockNS = sockNS;
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

        //this.sockCS = sockCS;
        outputStream = null;
        try {
            outputStream = this.sockCS.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            toCS = new OutputStreamWriter(outputStream, StandardCharsets.US_ASCII);
        }*/

    }

    @Override
    public void run() {


    }
}
