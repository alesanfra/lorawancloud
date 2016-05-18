package iet.unipi.lorawan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class NetworkServer implements Runnable {

    public static final String MOTES_CONF = "conf/motes.json";

    private static final int MAX_THREADS = 10;
    private static final int APPSERVER_LISTENING_PORT = 55667;
    private static final int GATEWAYS__LISTENING_PORT = 1700;
    private static final int EUI_LENGTH = 16;

    //private final ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);


    // Sockets
    private final ServerSocket listener;

    // Hashmap
    private final Map<String,LoraMote> motes;
    private final Map<String,InetSocketAddress> gateways;
    private final Map<String,Socket> appServers;


    /**
     * Default Constructor:
     *      - allocate all the hasmaps
     *      - load from file the mote list
     *      - start receiver
     */

    public NetworkServer() {

        // Caricare motes da file
        this.motes = loadMotesFromFile(MOTES_CONF);
        this.gateways = new ConcurrentHashMap<>();
        this.appServers = new ConcurrentHashMap<>();

        // Creare socket listener
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(APPSERVER_LISTENING_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            listener = serverSocket;
        }

        // faccio partire il receiver
        Thread receiver = new Thread(new NetworkServerReceiver(
                GATEWAYS__LISTENING_PORT,
                motes,
                gateways,
                appServers
        ));

        receiver.start();

        //executor.execute(receiver);
    }


    /**
     * Load all mote parameters from a json file
     * @param motesConf Path of the configuration file
     * @return Hashmap with key == deviece addess and value == LoraMote
     */

    private Map<String,LoraMote> loadMotesFromFile(String motesConf) {
        String file = "{}";
        Map<String, LoraMote> map = new ConcurrentHashMap<>();

        // Read json from file
        try {
            Path path = Paths.get(motesConf);
            file = new String(Files.readAllBytes(path), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            e.printStackTrace();
        }

        JSONArray motes = new JSONObject(file).getJSONArray("motes");

        // Scorro tutti i motes
        for (int i=0; i<motes.length(); i++) {
            JSONObject mote = motes.getJSONObject(i);

            String appEUI = mote.getString("appeui");
            String devEUI = mote.getString("deveui");
            String devAddr = mote.getString("devaddr");

            // Creo un istanza di LoraMote e la aggiungo alla lista di motes
            LoraMote newMote;

            if (mote.getString("join").equals("OTA")) {
                String appKey = mote.getString("appkey");
                newMote = new LoraMote(
                        devEUI,
                        appEUI,
                        devAddr,
                        appKey,
                        "",
                        ""
                );
            } else {
                String netSessKey = mote.getString("netsesskey");
                String appSessKey = mote.getString("appsesskey");
                newMote = new LoraMote(
                        devEUI,
                        appEUI,
                        devAddr,
                        "",
                        netSessKey,
                        appSessKey
                );
            }

            // Aggiungo il nuovo mote alla lista
            map.put(devAddr,newMote);
        }

        return map;
    }


    /**
     *
     */

    @Override
    public void run() {
        while (true) {
            try {
                Socket appServer = listener.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(appServer.getInputStream(), StandardCharsets.US_ASCII));
                String line = in.readLine();

                // Leggo a quale applicazione vuole registrarsi
                JSONObject message = new JSONObject(line);
                String appEUI = message.getString("appeui");

                if (appEUI.length() != EUI_LENGTH) {
                    break;
                }

                Socket app = appServers.get(appEUI);

                if (app != null && !app.isClosed()) {
                    // L'applicazione era gia registrata e ancora attiva
                    appServer.close();
                    break;
                }

                // Salvo il socket - TODO: controllare che non ci sia già e gestire
                appServers.put(appEUI,appServer);

                // Faccio partire il tread Receiver
                Thread sender = new Thread(new NetworkServerSender(
                        motes,
                        gateways,
                        appServer
                ));

                // E' inutile executor perché nel caso d'uso std un thread parte e non si ferma più
                // executor.execute(sender);

                // Così sono sicuro che parte
                sender.start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Start method
     * @param args
     */

    public static void main(String[] args) {
        NetworkServer networkServer = new NetworkServer();
        networkServer.run();
    }
}
