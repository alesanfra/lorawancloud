package iet.unipi.lorawan;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Created by alessio on 15/04/16.
 */
public class UDPServer implements Runnable {

    private static final int UDP_PORT = 7700;
    private static final int BUFFER_LEN = 2048;

    @Override
    public void run() {
        try {
            DatagramSocket sock = new DatagramSocket(UDP_PORT);
            System.out.println("Listening to: " + sock.getLocalAddress().getHostAddress() + " : " + sock.getLocalPort());

            while (true) {
                DatagramPacket packet = new DatagramPacket(new byte[BUFFER_LEN], BUFFER_LEN);
                sock.receive(packet);
                System.out.println("\nMessage received from: " + packet.getAddress().getHostAddress());
                System.out.println("Payload: " + (new String(packet.getData())).trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        UDPServer server = new UDPServer();
        server.run();
    }
}
