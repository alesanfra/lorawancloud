package iet.unipi.lorawan;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Created by alessio on 17/05/16.
 */
public class TestMultiThread implements Runnable {

    private static int portNumber = 6789;

    public static void main(String[] args) {
        Server server = new Server();
        server.start();


        Thread receiver = new Thread(new TestMultiThread());
        receiver.start();

        try{
            BufferedReader br =
                    new BufferedReader(new InputStreamReader(System.in));

            String input;

            while((input=br.readLine())!=null){
                System.out.println(input);
            }

        }catch(IOException io){
            io.printStackTrace();
        }

    }

    @Override
    public void run() {

        Logger logger = Logger.getGlobal();

        try (
                Socket socket = new Socket("localhost",portNumber);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        ) {
            while (true) {

                logger.info(in.readLine());
                //System.out.println(in.readLine());
            }

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    static class Server extends Thread {

        @Override
        public void run() {

            try (
                    ServerSocket serverSocket = new ServerSocket(portNumber);
                    Socket clientSocket = serverSocket.accept();
                    OutputStreamWriter out = new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.US_ASCII);
            ) {
                while (true) {


                    out.write("Hello world!");

                    out.flush();

                    sleep(5000);
                }


            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}



