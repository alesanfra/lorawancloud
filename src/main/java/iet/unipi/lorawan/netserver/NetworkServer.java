package iet.unipi.lorawan.netserver;

import iet.unipi.lorawan.Constants;
import iet.unipi.lorawan.Mote;
import iet.unipi.lorawan.MoteCollection;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class NetworkServer {

    /**
     * Load all mote parameters from a json file
     * @param motesConf Path of the configuration file
     * @return Hashmap with key == deviece addess and value == Mote
     */

    private static MoteCollection loadMotesFromFile(String motesConf) {
        String file = "{}";
        MoteCollection map = new MoteCollection();

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

            String appEUI = mote.getString("appeui").toLowerCase();
            String devEUI = mote.getString("deveui").toLowerCase();
            String devAddr = mote.getString("devaddr").toLowerCase();

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
     * Start method:
     *      - allocate all the hasmaps
     *      - load from file the mote list
     *      - start receiver
     *      - start listener
     * @param args
     */

    public static void main(String[] args) {
        MoteCollection motes = loadMotesFromFile(Constants.MOTES_CONF);
        Map<String,AppServer> appServers = new ConcurrentHashMap<>();

        try {
            Thread listener = new Thread(new NetworkServerListener(
                    Constants.NETSERVER_LISTENING_PORT,
                    motes,
                    appServers
            ));

            Thread receiver = new Thread(new NetworkServerReceiver(
                    Constants.GATEWAYS_LISTENING_PORT,
                    motes,
                    appServers
            ));

            listener.start();
            receiver.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
