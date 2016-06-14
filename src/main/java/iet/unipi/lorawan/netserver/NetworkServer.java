package iet.unipi.lorawan.netserver;

import iet.unipi.lorawan.Constants;
import iet.unipi.lorawan.Mote;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class NetworkServer {

    private static final int MAX_THREADS = 50;
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);


    // Hashmap
    private final Map<String,Mote> motes;
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
        this.motes = loadMotesFromFile(Constants.MOTES_CONF);
        this.gateways = new ConcurrentHashMap<>();
        this.appServers = new ConcurrentHashMap<>();



        // faccio partire il receiver
        Thread receiver = new Thread(new NetworkServerReceiver(
                Constants.GATEWAYS_LISTENING_PORT,
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
     * @return Hashmap with key == deviece addess and value == Mote
     */

    private Map<String,Mote> loadMotesFromFile(String motesConf) {
        String file = "{}";
        Map<String, Mote> map = new ConcurrentHashMap<>();

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

            // Creo un istanza di Mote e la aggiungo alla lista di motes
            Mote newMote;

            if (mote.getString("join").equals("OTA")) {
                String appKey = mote.getString("appkey");
                newMote = new Mote(
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
                newMote = new Mote(
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
     * Start method
     * @param args
     */

    public static void main(String[] args) {
        NetworkServer networkServer = new NetworkServer();
    }
}
