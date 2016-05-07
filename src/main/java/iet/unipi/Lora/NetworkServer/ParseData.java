package iet.unipi.Lora.NetworkServer;

/**
 * Created by alessio on 06/05/16.
 */
public class ParseData implements Runnable {

    @Override
    public void run() {

    }

    public static void main(String[] args) {
        ParseData parseData = new ParseData();
        Thread t1 = new Thread(parseData);
        t1.start();
    }
}
